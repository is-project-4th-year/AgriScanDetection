package com.example.agriscan.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fields")
data class FieldEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "captures")
data class CaptureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val createdAt: Long = System.currentTimeMillis()
)
