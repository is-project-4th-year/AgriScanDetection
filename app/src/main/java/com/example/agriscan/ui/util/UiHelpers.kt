package com.example.agriscan.ui.util

enum class ConfidenceBand { HIGH, MEDIUM, LOW }

/**
 * Maps (quality, top1Prob) into a coarse confidence band that you can use to
 * color / message the result in the UI. Tweak thresholds as you collect feedback.
 */
fun classifyConfidence(quality: Float, top1Prob: Float): ConfidenceBand = when {
    quality >= 0.85f && top1Prob >= 0.60f -> ConfidenceBand.HIGH
    quality >= 0.60f && top1Prob >= 0.35f -> ConfidenceBand.MEDIUM
    else -> ConfidenceBand.LOW
}

/**
 * Turns a model label like "tomato_tomato_yellowleaf_curl_virus" into
 * "Tomato – Tomato Yellowleaf Curl Virus".
 */
fun prettyLabel(raw: String): String =
    raw.replace("__", " – ")
        .replace('_', ' ')
        .trim()
        .split(' ')
        .joinToString(" ") { it.replaceFirstChar { c -> c.titlecase() } }

/** Simple percent formatter for UI. */
fun formatPct(p: Float): String = String.format("%.1f%%", (p * 100f))
