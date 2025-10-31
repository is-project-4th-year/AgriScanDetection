package com.example.agriscan.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.tensorflow.lite.Interpreter
import org.json.JSONObject
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.ln

data class Prediction(val label: String, val prob: Float)

data class Inference(
    val topK: List<Prediction>,
    /** Entropy in nats (0..ln(K)). Lower is better. */
    val entropy: Float,
    /** Quality in 0..1 = 1 - normalizedEntropy. Higher is better. */
    val quality: Float
)

class TFLiteClassifier private constructor(
    private val interpreter: Interpreter,
    private val labels: List<String>,
    private val inputSize: Int = 224,
    private val numChannels: Int = 3,
    /** Temperature for probability calibration (T=1 = no change). */
    private val temperature: Float = 1.0f
) {

    companion object {
        private const val MODEL_PATH  = "model_fp32.tflite"  // or "model_int8.tflite"
        private const val LABELS_PATH = "labels.txt"
        private const val CALIB_PATH  = "new_calibration.json"
        private const val NUM_THREADS = 4

        @Volatile private var instance: TFLiteClassifier? = null

        fun getInstance(context: Context): TFLiteClassifier {
            return instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }
        }

        fun shutdown() {
            synchronized(this) {
                try { instance?.close() } catch (_: Throwable) {}
                instance = null
            }
        }

        private fun build(ctx: Context): TFLiteClassifier {
            val model = loadModelFile(ctx, MODEL_PATH)
            val options = Interpreter.Options().apply {
                setNumThreads(NUM_THREADS)
                setUseXNNPACK(true)
            }
            val interpreter = Interpreter(model, options)
            val labels = loadLabels(ctx, LABELS_PATH)
            val temp = loadTemperature(ctx, CALIB_PATH)

            val outShape = interpreter.getOutputTensor(0).shape()
            val outK = if (outShape.isNotEmpty()) outShape.last() else labels.size
            require(outK == labels.size) {
                "Model output classes ($outK) != labels size (${labels.size}). Ensure labels.txt matches training."
            }
            return TFLiteClassifier(interpreter, labels, temperature = temp)
        }

        private fun loadModelFile(context: Context, assetName: String): MappedByteBuffer {
            val afd = context.assets.openFd(assetName)
            FileInputStream(afd.fileDescriptor).channel.use { channel ->
                return channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    afd.startOffset,
                    afd.length
                )
            }
        }

        private fun loadLabels(context: Context, assetName: String): List<String> {
            context.assets.open(assetName).use { input ->
                BufferedReader(InputStreamReader(input)).useLines { seq ->
                    return seq.map { it.trim() }.filter { it.isNotEmpty() }.toList()
                }
            }
        }

        private fun loadTemperature(context: Context, assetName: String): Float {
            return try {
                context.assets.open(assetName).bufferedReader().use { br ->
                    val txt = br.readText()
                    val json = JSONObject(txt)
                    val t = json.optDouble("temperature", 1.0)
                    if (t.isNaN() || t <= 0.0) 1.0f else t.toFloat()
                }
            } catch (_: Throwable) {
                1.0f
            }
        }
    }

    fun close() {
        try { interpreter.close() } catch (_: Throwable) {}
    }

    /** Predict on a Bitmap. Returns topK with calibrated probabilities if temperature != 1. */
    @Synchronized
    fun predict(bitmap: Bitmap, topK: Int = 3): List<Prediction> {
        val inputBuffer = bitmapToNHWCFloatBuffer(bitmap, inputSize, inputSize)
        val outShape = interpreter.getOutputTensor(0).shape()
        val numClasses = if (outShape.isNotEmpty()) outShape.last() else labels.size.coerceAtLeast(1)
        val output = Array(1) { FloatArray(numClasses) }
        interpreter.run(inputBuffer, output)
        val probsRaw = output[0]
        val probs = if (temperature != 1f) temperatureScaleProbs(probsRaw, temperature) else probsRaw

        return probs.mapIndexed { idx, p ->
            Prediction(if (idx in labels.indices) labels[idx] else "class_$idx", p)
        }.sortedByDescending { it.prob }.take(topK)
    }

    /** Predict + uncertainty metrics (entropy/quality). */
    @Synchronized
    fun predictWithUncertainty(bitmap: Bitmap, topK: Int = 3): Inference {
        val inputBuffer = bitmapToNHWCFloatBuffer(bitmap, inputSize, inputSize)
        val outShape = interpreter.getOutputTensor(0).shape()
        val numClasses = if (outShape.isNotEmpty()) outShape.last() else labels.size.coerceAtLeast(1)
        val output = Array(1) { FloatArray(numClasses) }
        interpreter.run(inputBuffer, output)
        val probsRaw = output[0]
        val probs = if (temperature != 1f) temperatureScaleProbs(probsRaw, temperature) else probsRaw

        val preds = probs.mapIndexed { idx, p ->
            Prediction(if (idx in labels.indices) labels[idx] else "class_$idx", p)
        }.sortedByDescending { it.prob }.take(topK)

        val h = entropy(probs)
        val q = 1f - normalizedEntropy(probs)
        return Inference(preds, h, q)
    }

    fun predictFromUri(context: Context, uri: Uri, topK: Int = 3): List<Prediction> {
        val bmp = decodeScaled(context, uri, maxDim = 1024) ?: error("Could not decode image: $uri")
        return predict(bmp, topK)
    }

    fun predictFromUriWithUncertainty(context: Context, uri: Uri, topK: Int = 3): Inference {
        val bmp = decodeScaled(context, uri, maxDim = 1024) ?: error("Could not decode image: $uri")
        return predictWithUncertainty(bmp, topK)
    }

    // -------- Helpers --------

    private fun centerCropSquare(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val size = minOf(w, h)
        val x = (w - size) / 2
        val y = (h - size) / 2
        return Bitmap.createBitmap(src, x, y, size, size)
    }

    /** Feed **0..255 floats**; Normalization layer in graph handles scaling. */
    private fun bitmapToNHWCFloatBuffer(src: Bitmap, dstW: Int, dstH: Int): ByteBuffer {
        val cropped = centerCropSquare(src)
        val resized = if (cropped.width != dstW || cropped.height != dstH) {
            Bitmap.createScaledBitmap(cropped, dstW, dstH, true)
        } else cropped

        val buffer = ByteBuffer.allocateDirect(4 * dstW * dstH * numChannels).order(ByteOrder.nativeOrder())
        val ints = IntArray(dstW * dstH)
        resized.getPixels(ints, 0, dstW, 0, 0, dstW, dstH)
        var i = 0
        while (i < ints.size) {
            val c = ints[i]
            buffer.putFloat(((c shr 16) and 0xFF).toFloat()) // R
            buffer.putFloat(((c shr 8) and 0xFF).toFloat())  // G
            buffer.putFloat((c and 0xFF).toFloat())          // B
            i++
        }
        buffer.rewind()

        if (resized !== src && resized !== cropped) resized.recycle()
        if (cropped !== src) cropped.recycle()
        return buffer
    }

    private fun temperatureScaleProbs(probsIn: FloatArray, temperature: Float): FloatArray {
        val T = if (temperature > 0f) temperature else 1f
        if (T == 1f) return probsIn.copyOf()

        var sum = 0.0
        val powered = DoubleArray(probsIn.size)
        for (i in probsIn.indices) {
            val v = probsIn[i].coerceAtLeast(0f).toDouble()
            val p = Math.pow(v, 1.0 / T)
            powered[i] = p
            sum += p
        }
        val out = FloatArray(probsIn.size)
        if (sum == 0.0) {
            val u = 1f / probsIn.size
            for (i in out.indices) out[i] = u
        } else {
            for (i in out.indices) out[i] = (powered[i] / sum).toFloat()
        }
        return out
    }

    private fun entropy(probs: FloatArray): Float {
        var h = 0.0
        for (p in probs) if (p > 0f) h -= (p.toDouble() * ln(p.toDouble()))
        return h.toFloat()
    }

    private fun normalizedEntropy(probs: FloatArray): Float {
        val h = entropy(probs)
        val k = probs.size.toDouble()
        val hMax = ln(k)
        return if (hMax > 0.0) (h / hMax).toFloat() else 0f
    }

    private fun decodeScaled(context: Context, uri: Uri, maxDim: Int): Bitmap? {
        val resolver = context.contentResolver
        val optsBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, optsBounds) }
        val w = optsBounds.outWidth
        val h = optsBounds.outHeight
        if (w <= 0 || h <= 0) {
            return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }
        var sample = 1
        var tw = w
        var th = h
        while (tw > maxDim || th > maxDim) {
            sample *= 2
            tw /= 2
            th /= 2
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }
}
