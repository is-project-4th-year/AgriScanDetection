package com.example.agriscan.data

import com.example.agriscan.data.local.CaptureDao
import com.example.agriscan.data.local.CaptureEntity
import kotlinx.coroutines.flow.Flow

class LibraryRepository(private val dao: CaptureDao) {

    fun observeAll(): Flow<List<CaptureEntity>> = dao.observeCaptures()

    fun observeByField(fieldId: Long): Flow<List<CaptureEntity>> = dao.observeByField(fieldId)

    suspend fun addCapture(uriString: String, fieldId: Long? = null): Long =
        dao.insert(CaptureEntity(uri = uriString, fieldId = fieldId))

    suspend fun assignToField(captureIds: List<Long>, fieldId: Long?) =
        dao.assignToField(captureIds, fieldId)

    suspend fun remove(captureId: Long) = dao.delete(captureId)

    suspend fun removeMany(captureIds: List<Long>) = dao.deleteMany(captureIds)
}
