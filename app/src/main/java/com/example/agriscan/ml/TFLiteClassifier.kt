package com.example.agriscan.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
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
import kotlin.math.exp
import kotlin.math.ln

data class Prediction(val label: String, val prob: Float)

/** Rich result with uncertainty metrics */
data class Inference(
    val topK: List<Prediction>,
    /** Entropy in nats (0..ln(K)). Lower is better (peaked). */
    val entropy: Float,
    /** Confidence quality in 0..1, computed as 1 - normalizedEntropy. Higher is better. */
    val quality: Float
)

class TFLiteClassifier private constructor(
    private val interpreter: Interpreter,
    private val labels: List<String>,
    private val inputSize: Int = 224,
    private val numChannels: Int = 3,
    /** Temperature for probability calibration (T=1 means no change). */
    private val temperature: Float = 1.0f
) {

    companion object {
        // Update to your new asset names. If you ever need backwards-compat, add try/fallbacks.
        private const val MODEL_PATH  = "new_model_float32.tflite"
        private const val LABELS_PATH = "new_labels_trained_order.txt"
        private const val CALIB_PATH  = "new_calibration.json"
        private const val NUM_THREADS = 4

        @Volatile
        private var instance: TFLiteClassifier? = null

        fun getInstance(context: Context): TFLiteClassifier {
            return instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }
        }

        /** Optional: call when leaving Scan screen to free native resources. */
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
                setUseXNNPACK(true) // good for float32 models
            }
            val interpreter = Interpreter(model, options)
            val labels = loadLabels(ctx, LABELS_PATH)
            val temp = loadTemperature(ctx, CALIB_PATH) // defaults to 1.0 if not found/parsable
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
                    return seq.map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .toList()
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
                1.0f // safe default if file missing or invalid
            }
        }
    }

    fun close() {
        try { interpreter.close() } catch (_: Throwable) {}
    }

    /**
     * Predict on a Bitmap.
     * Pipeline: RGB → resize 192×192 → float32 [0,1] → NHWC → logits → (logits/T) → softmax
     * Signature unchanged; now returns **calibrated** probabilities if a temperature is present.
     */
    @Synchronized
    fun predict(bitmap: Bitmap, topK: Int = 3): List<Prediction> {
        val inputBuffer = bitmapToNHWCFloatBuffer(bitmap, inputSize, inputSize)

        // ⚠️ Size output from the model, not from labels, to avoid mismatch crashes.
        val outShape = interpreter.getOutputTensor(0).shape() // e.g. [1, 15] or [15]
        val numClasses = if (outShape.isNotEmpty()) outShape.last() else labels.size.coerceAtLeast(1)

        val output = Array(1) { FloatArray(numClasses) }
        interpreter.run(inputBuffer, output)

        // Calibrated softmax: divide logits by T before exponentiating
        val probs = softmaxCalibrated(output[0], temperature)

        return probs
            .mapIndexed { idx, p ->
                val name = if (idx in labels.indices) labels[idx] else "class_$idx"
                Prediction(name, p)
            }
            .sortedByDescending { it.prob }
            .take(topK)
    }

    /** Decode a URI efficiently (downsample) then predict. */
    fun predictFromUri(context: Context, uri: Uri, topK: Int = 3): List<Prediction> {
        val bmp = decodeScaled(context, uri, maxDim = 1024)
            ?: error("Could not decode image: $uri")
        return predict(bmp, topK)
    }

    // ---------------------
    //  New: Uncertainty API
    // ---------------------

    /** Predict + return entropy (in nats) and quality (1 - normalized entropy). */
    @Synchronized
    fun predictWithUncertainty(bitmap: Bitmap, topK: Int = 3): Inference {
        val inputBuffer = bitmapToNHWCFloatBuffer(bitmap, inputSize, inputSize)

        val outShape = interpreter.getOutputTensor(0).shape()
        val numClasses = if (outShape.isNotEmpty()) outShape.last() else labels.size.coerceAtLeast(1)

        val output = Array(1) { FloatArray(numClasses) }
        interpreter.run(inputBuffer, output)

        val probs = softmaxCalibrated(output[0], temperature)

        val preds = probs
            .mapIndexed { idx, p ->
                val name = if (idx in labels.indices) labels[idx] else "class_$idx"
                Prediction(name, p)
            }
            .sortedByDescending { it.prob }
            .take(topK)

        val h = entropy(probs)
        val q = 1f - normalizedEntropy(probs)

        return Inference(preds, h, q)
    }

    /** Convenience for URI input. */
    fun predictFromUriWithUncertainty(context: Context, uri: Uri, topK: Int = 3): Inference {
        val bmp = decodeScaled(context, uri, maxDim = 1024)
            ?: error("Could not decode image: $uri")
        return predictWithUncertainty(bmp, topK)
    }

    // --- Helpers ---

    private fun bitmapToNHWCFloatBuffer(src: Bitmap, dstW: Int, dstH: Int): ByteBuffer {
        val resized = if (src.width != dstW || src.height != dstH) {
            Bitmap.createScaledBitmap(src, dstW, dstH, true)
        } else src

        val buffer = ByteBuffer.allocateDirect(4 * dstW * dstH * numChannels).apply {
            order(ByteOrder.nativeOrder())
        }

        // RGB in [0,1], NHWC order
        for (y in 0 until dstH) {
            for (x in 0 until dstW) {
                val c = resized.getPixel(x, y)
                buffer.putFloat(Color.red(c) / 255f)
                buffer.putFloat(Color.green(c) / 255f)
                buffer.putFloat(Color.blue(c) / 255f)
            }
        }
        buffer.rewind()

        if (resized !== src) resized.recycle()
        return buffer
    }

    /** Calibrated softmax: applies logits/T then standard softmax (numerically stable). */
    private fun softmaxCalibrated(logits: FloatArray, temperature: Float): FloatArray {
        val T = if (temperature > 0f) temperature else 1f
        // find max(logits/T) for numerical stability
        var maxVal = Float.NEGATIVE_INFINITY
        for (v in logits) {
            val z = v / T
            if (z > maxVal) maxVal = z
        }

        var sum = 0.0
        val exps = DoubleArray(logits.size)
        for (i in logits.indices) {
            val z = logits[i] / T - maxVal
            val e = exp(z.toDouble())
            exps[i] = e
            sum += e
        }

        val probs = FloatArray(logits.size)
        if (sum == 0.0) {
            // extremely defensive: fall back to uniform
            val u = 1.0f / logits.size
            for (i in logits.indices) probs[i] = u
            return probs
        }
        for (i in logits.indices) {
            probs[i] = (exps[i] / sum).toFloat()
        }
        return probs
    }

    /** Entropy in nats. */
    private fun entropy(probs: FloatArray): Float {
        var h = 0.0
        for (p in probs) if (p > 0f) h -= (p.toDouble() * ln(p.toDouble()))
        return h.toFloat()
    }

    /** Normalized entropy in 0..1 (divides by ln(K)). */
    private fun normalizedEntropy(probs: FloatArray): Float {
        val h = entropy(probs)
        val k = probs.size.toDouble()
        val hMax = ln(k)
        return if (hMax > 0.0) (h / hMax).toFloat() else 0f
    }

    /** Efficiently decode large images with downsampling. */
    private fun decodeScaled(context: Context, uri: Uri, maxDim: Int): Bitmap? {
        val resolver = context.contentResolver

        // 1) Bounds pass
        val optsBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, optsBounds) }

        val (w, h) = optsBounds.outWidth to optsBounds.outHeight
        if (w <= 0 || h <= 0) {
            return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }

        // 2) Compute inSampleSize (power-of-two downscale to <= maxDim)
        var sample = 1
        var tw = w
        var th = h
        while (tw > maxDim || th > maxDim) {
            sample *= 2
            tw /= 2
            th /= 2
        }

        // 3) Decode with sampling
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }
}
