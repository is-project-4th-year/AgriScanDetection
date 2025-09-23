package com.example.agriscan.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

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

@Dao
interface CaptureDao {

    @Query("SELECT * FROM captures ORDER BY createdAt DESC")
    fun observeCaptures(): Flow<List<CaptureEntity>>

    @Query("SELECT * FROM captures WHERE fieldId = :fieldId ORDER BY createdAt DESC")
    fun observeByField(fieldId: Long): Flow<List<CaptureEntity>>

    @Insert
    suspend fun insert(capture: CaptureEntity): Long

    @Query("UPDATE captures SET fieldId = :fieldId WHERE id IN (:ids)")
    suspend fun assignToField(ids: List<Long>, fieldId: Long?)

    @Query("DELETE FROM captures WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM captures WHERE id IN (:ids)")
    suspend fun deleteMany(ids: List<Long>)
}
