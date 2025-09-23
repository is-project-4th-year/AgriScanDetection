package com.example.agriscan.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "fields")
data class FieldEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "captures",
    indices = [Index("fieldId")]
)
data class CaptureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val uri: String,
    // keep nullable so “Remove from field” works
    val fieldId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
