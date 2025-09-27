package com.example.agriscan.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import org.tensorflow.lite.Interpreter
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.exp

data class Prediction(val label: String, val prob: Float)

class TFLiteClassifier private constructor(
    private val interpreter: Interpreter,
    private val labels: List<String>,
    private val inputSize: Int = 192,
    private val numChannels: Int = 3
) {

    companion object {
        private const val MODEL_PATH = "model_float32.tflite" // your float32 model
        private const val LABELS_PATH = "labels.txt"
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
            return TFLiteClassifier(interpreter, labels)
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
    }

    fun close() {
        try { interpreter.close() } catch (_: Throwable) {}
    }

    /**
     * Predict on a Bitmap.
     * Pipeline: RGB → resize 192×192 → float32 [0,1] → NHWC → logits → softmax
     */
    @Synchronized
    fun predict(bitmap: Bitmap, topK: Int = 3): List<Prediction> {
        val inputBuffer = bitmapToNHWCFloatBuffer(bitmap, inputSize, inputSize)

        // ⚠️ Size output from the model, not from labels, to avoid mismatch crashes.
        val outShape = interpreter.getOutputTensor(0).shape() // e.g. [1, 15] or [15]
        val numClasses = if (outShape.isNotEmpty()) outShape.last() else labels.size.coerceAtLeast(1)

        val output = Array(1) { FloatArray(numClasses) }
        interpreter.run(inputBuffer, output)

        val probs = softmax(output[0])

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

    private fun softmax(logits: FloatArray): FloatArray {
        var max = Float.NEGATIVE_INFINITY
        for (v in logits) if (v > max) max = v
        var sum = 0.0
        val exps = DoubleArray(logits.size)
        for (i in logits.indices) {
            val e = exp((logits[i] - max).toDouble())
            exps[i] = e
            sum += e
        }
        val probs = FloatArray(logits.size)
        for (i in logits.indices) {
            probs[i] = (exps[i] / sum).toFloat()
        }
        return probs
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
