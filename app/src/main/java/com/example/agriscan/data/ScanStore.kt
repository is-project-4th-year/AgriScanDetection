package com.example.agriscan.data

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class ScanEntry(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val timestamp: Long = System.currentTimeMillis(),
    val result: String? = null,           // placeholder for analysis result
)

object ScanStore {
    private val _items = MutableStateFlow<List<ScanEntry>>(emptyList())
    val items = _items.asStateFlow()

    fun add(entry: ScanEntry) {
        _items.value = listOf(entry) + _items.value  // newest first
    }

    fun updateResult(id: String, result: String) {
        _items.value = _items.value.map { if (it.id == id) it.copy(result = result) else it }
    }

    fun get(id: String): ScanEntry? = _items.value.firstOrNull { it.id == id }
    fun clear() { _items.value = emptyList() }
}
