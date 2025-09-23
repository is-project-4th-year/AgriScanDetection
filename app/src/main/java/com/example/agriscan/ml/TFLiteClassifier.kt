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
        private const val MODEL_PATH = "model_float32.tflite"
        private const val LABELS_PATH = "labels.txt"
        private const val NUM_THREADS = 4

        /** Single, lazily-initialized instance */
        @Volatile
        private var instance: TFLiteClassifier? = null

        fun getInstance(context: Context): TFLiteClassifier {
            return instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }
        }

        /** Close the interpreter and clear the singleton (e.g., on app going to background). */
        fun shutdown() {
            synchronized(this) {
                try {
                    instance?.close()
                } catch (_: Throwable) {
                } finally {
                    instance = null
                }
            }
        }

        private fun build(ctx: Context): TFLiteClassifier {
            val model = loadModelFile(ctx, MODEL_PATH)
            val options = Interpreter.Options().apply {
                setNumThreads(NUM_THREADS)
                // Optional: enable XNNPACK for float models (usually on by default)
                setUseXNNPACK(true)
                // Optional (only if you’ve added GPU delegate dep):
                // addDelegate(org.tensorflow.lite.gpu.GpuDelegate())
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

    /** Close TFLite interpreter resources */
    fun close() {
        try {
            interpreter.close()
        } catch (_: Throwable) {
        }
    }

    /**
     * Run prediction on a Bitmap.
     * RGB → resize 192×192 → float32 [0,1] → NHWC → logits → softmax
     * Synchronized to avoid concurrent access to the same Interpreter.
     */
    @Synchronized
    fun predict(bitmap: Bitmap, topK: Int = 3): List<Prediction> {
        val inputBuffer = bitmapToNHWCFloatBuffer(bitmap, inputSize, inputSize)
        val numClasses = labels.size.coerceAtLeast(1)
        val output = Array(1) { FloatArray(numClasses) }

        interpreter.run(inputBuffer, output)
        val probs = softmax(output[0])

        return probs
            .mapIndexed { idx, p ->
                val name = if (idx < labels.size) labels[idx] else "class_$idx"
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
            // Fallback single-pass decode
            return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }

        // 2) Compute inSampleSize
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
