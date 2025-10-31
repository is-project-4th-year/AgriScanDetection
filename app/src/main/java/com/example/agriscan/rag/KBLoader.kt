package com.example.agriscan.rag

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

data class KBEntry(
    val id: String,
    val title: String,
    val clazz: String,
    val text: String
)

class KBLoader(context: Context, assetName: String = "kb.jsonl") {

    val entries: List<KBEntry> = loadFromAssets(context, assetName)

    // Fast lookups
    private val idIndex: Map<String, KBEntry> = entries.associateBy { it.id }
    private val classIndex: Map<String, List<KBEntry>> = entries.groupBy { it.clazz }

    /** Top-k docs for a predicted class */
    fun forClass(clazz: String, k: Int = 3): List<KBEntry> =
        (classIndex[clazz] ?: emptyList()).take(k)

    /** Title by document id (or null if not found) */
    fun titleOf(id: String): String? = idIndex[id]?.title

    private fun loadFromAssets(ctx: Context, asset: String): List<KBEntry> {
        val list = mutableListOf<KBEntry>()
        ctx.assets.open(asset).use { input ->
            BufferedReader(InputStreamReader(input)).useLines { lines ->
                lines.forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) return@forEach
                    val json = JSONObject(trimmed)
                    list += KBEntry(
                        id    = json.getString("id"),
                        title = json.getString("title"),
                        clazz = json.getString("class"),
                        text  = json.getString("text")
                    )
                }
            }
        }
        return list
    }
}
