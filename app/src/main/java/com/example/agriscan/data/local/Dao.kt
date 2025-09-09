package com.example.agriscan.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FieldDao {
    @Query("SELECT * FROM fields ORDER BY createdAt DESC")
    fun observeFields(): Flow<List<FieldEntity>>

    @Insert
    suspend fun insert(field: FieldEntity): Long

    @Update
    suspend fun update(field: FieldEntity)

    @Query("DELETE FROM fields WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface CaptureDao {
    @Query("SELECT * FROM captures ORDER BY createdAt DESC")
    fun observeCaptures(): Flow<List<CaptureEntity>>

    @Insert
    suspend fun insert(capture: CaptureEntity): Long

    @Query("DELETE FROM captures WHERE uri = :uri")
    suspend fun deleteByUri(uri: String)

    @Query("SELECT COUNT(*) FROM captures")
    fun observeCount(): Flow<Long>
}
