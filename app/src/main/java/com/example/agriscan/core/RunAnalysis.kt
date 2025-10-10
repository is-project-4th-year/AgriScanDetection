//package com.example.agriscan.core
//
//import android.content.Context
//import android.net.Uri
//import com.example.agriscan.ml.Prediction
//import com.example.agriscan.ml.TFLiteClassifier
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.withContext
//
///**
// * Runs TFLite inference off the UI thread and returns either predictions or an error.
// */
//fun runAnalysis(
//    context: Context,
//    uri: Uri,
//    onResult: (List<Prediction>?, String?) -> Unit
//) {
//    CoroutineScope(Dispatchers.Default).launch {
//        try {
//            val clf = TFLiteClassifier.getInstance(context)
//            val preds = clf.predictFromUri(context, uri, topK = 3)
//            withContext(Dispatchers.Main) { onResult(preds, null) }
//        } catch (e: Exception) {
//            withContext(Dispatchers.Main) { onResult(null, e.message ?: "Failed to analyze image.") }
//        }
//    }
//}

package com.example.agriscan.core

import android.content.Context
import android.net.Uri
import com.example.agriscan.ml.Inference
import com.example.agriscan.ml.TFLiteClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs TFLite inference off the UI thread and returns either an Inference (topK + uncertainty) or an error.
 */
fun runAnalysis(
    context: Context,
    uri: Uri,
    onResult: (Inference?, String?) -> Unit
) {
    CoroutineScope(Dispatchers.Default).launch {
        try {
            val clf = TFLiteClassifier.getInstance(context)

            // Calibrated predictions + uncertainty (entropy + quality)
            val res = clf.predictFromUriWithUncertainty(context, uri, topK = 3)
            // res.topK    -> list of Prediction(label, prob)
            // res.quality -> 0..1 (higher means more confident / lower entropy)
            // res.entropy -> in nats

            withContext(Dispatchers.Main) { onResult(res, null) }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onResult(null, e.message ?: "Failed to analyze image.") }
        }
    }
}
