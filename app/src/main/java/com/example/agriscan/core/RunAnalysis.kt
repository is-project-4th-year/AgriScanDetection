package com.example.agriscan.core

import android.content.Context
import android.net.Uri
import com.example.agriscan.ml.Prediction
import com.example.agriscan.ml.TFLiteClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs TFLite inference off the UI thread and returns either predictions or an error.
 */
fun runAnalysis(
    context: Context,
    uri: Uri,
    onResult: (List<Prediction>?, String?) -> Unit
) {
    CoroutineScope(Dispatchers.Default).launch {
        try {
            val clf = TFLiteClassifier.getInstance(context)
            val preds = clf.predictFromUri(context, uri, topK = 3)
            withContext(Dispatchers.Main) { onResult(preds, null) }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onResult(null, e.message ?: "Failed to analyze image.") }
        }
    }
}
