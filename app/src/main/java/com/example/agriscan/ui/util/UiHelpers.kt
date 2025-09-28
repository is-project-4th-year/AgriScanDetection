package com.example.agriscan.ui.util

enum class ConfidenceBand { HIGH, MEDIUM, LOW }

fun classifyConfidence(quality: Float, top1Prob: Float): ConfidenceBand = when {
    quality >= 0.85f && top1Prob >= 0.60f -> ConfidenceBand.HIGH
    quality >= 0.60f && top1Prob >= 0.35f -> ConfidenceBand.MEDIUM
    else -> ConfidenceBand.LOW
}

fun prettyLabel(raw: String): String =
    raw.replace("__", " – ")
        .replace('_', ' ')
        .trim()
        .split(' ')
        .joinToString(" ") { it.replaceFirstChar { c -> c.titlecase() } }
