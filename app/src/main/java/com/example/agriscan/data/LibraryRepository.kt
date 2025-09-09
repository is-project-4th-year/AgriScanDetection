package com.example.agriscan.data

import android.net.Uri
import com.example.agriscan.data.local.CaptureDao
import com.example.agriscan.data.local.CaptureEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LibraryRepository(private val dao: CaptureDao) {

    /** Library items as URIs (newest first). */
    val captures: Flow<List<Uri>> =
        dao.observeCaptures().map { list ->
            list.mapNotNull { runCatching { Uri.parse(it.uri) }.getOrNull() }
        }

    suspend fun addCapture(uri: Uri) {
        dao.insert(CaptureEntity(uri = uri.toString()))
    }

    suspend fun removeCapture(uri: Uri) {
        dao.deleteByUri(uri.toString())
    }
}
