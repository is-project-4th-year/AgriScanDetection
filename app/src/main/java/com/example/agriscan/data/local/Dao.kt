package com.example.agriscan.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// -------------------- Fields --------------------

@Dao
interface FieldDao {
    @Query("SELECT * FROM fields ORDER BY createdAt DESC")
    fun observeFields(): Flow<List<FieldEntity>>

    @Query("SELECT * FROM fields WHERE id = :id")
    fun observeField(id: Long): Flow<FieldEntity?>

    @Query("SELECT * FROM fields WHERE id = :id")
    suspend fun find(id: Long): FieldEntity?

    @Insert
    suspend fun insert(field: FieldEntity): Long

    @Update
    suspend fun update(field: FieldEntity)

    @Query("DELETE FROM fields WHERE id = :id")
    suspend fun delete(id: Long)
}

// -------------------- Captures (photos) --------------------

@Dao
interface CaptureDao {
    @Query("SELECT * FROM captures ORDER BY createdAt DESC")
    fun observeCaptures(): Flow<List<CaptureEntity>>

    @Query("SELECT * FROM captures WHERE fieldId = :fieldId ORDER BY createdAt DESC")
    fun observeByField(fieldId: Long): Flow<List<CaptureEntity>>

    @Query("SELECT * FROM captures WHERE id = :id")
    fun observeCapture(id: Long): Flow<CaptureEntity?>

    @Query("SELECT * FROM captures WHERE id = :id")
    suspend fun find(id: Long): CaptureEntity?

    // Helper to resolve a row by its URI (used after inserting through repository)
    @Query("SELECT * FROM captures WHERE uri = :uri LIMIT 1")
    suspend fun findByUri(uri: String): CaptureEntity?

    @Insert
    suspend fun insert(capture: CaptureEntity): Long

    @Update
    suspend fun update(capture: CaptureEntity)

    @Query("UPDATE captures SET fieldId = :fieldId WHERE id IN (:ids)")
    suspend fun assignToField(ids: List<Long>, fieldId: Long?)

    @Query("DELETE FROM captures WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM captures WHERE id IN (:ids)")
    suspend fun deleteMany(ids: List<Long>)

    // Set prediction after TFLite inference
    @Query("""
        UPDATE captures
        SET predictedClass = :predictedClass,
            top1Prob       = :top1Prob,
            modelVersion   = :modelVersion
        WHERE id = :id
    """)
    suspend fun setPrediction(
        id: Long,
        predictedClass: String?,
        top1Prob: Float?,
        modelVersion: String?
    )
}

// -------------------- Advice sessions (RAG history) --------------------

@Dao
interface AdviceDao {
    @Insert
    suspend fun insert(a: AdviceSessionEntity): Long

    @Query("SELECT * FROM advice_sessions WHERE captureId = :captureId ORDER BY createdAt DESC")
    fun observeForCapture(captureId: Long): Flow<List<AdviceSessionEntity>>

    @Query("SELECT * FROM advice_sessions WHERE captureId = :captureId ORDER BY createdAt DESC LIMIT 1")
    suspend fun findLastForCapture(captureId: Long): AdviceSessionEntity?

    @Query("DELETE FROM advice_sessions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM advice_sessions WHERE captureId = :captureId")
    suspend fun deleteForCapture(captureId: Long)
}
