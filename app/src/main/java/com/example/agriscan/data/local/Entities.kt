package com.example.agriscan.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "fields")
data class FieldEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/** Each captured photo, with prediction metadata for RAG. */
@Entity(
    tableName = "captures",
    indices = [Index("fieldId")]
)
data class CaptureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val uri: String,
    val fieldId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),

    // Prediction metadata (filled after TFLite inference)
    val predictedClass: String? = null,  // e.g., "Tomato_Late_blight"
    val top1Prob: Float? = null,         // 0..1
    val modelVersion: String? = null     // e.g., "mobilenetv2-v1"
)

/** Offline RAG advice session tied to a capture. */
@Entity(
    tableName = "advice_sessions",
    foreignKeys = [
        ForeignKey(
            entity = CaptureEntity::class,
            parentColumns = ["id"],
            childColumns = ["captureId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("captureId"), Index("createdAt")]
)
data class AdviceSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val captureId: Long,                 // FK -> captures.id
    val query: String,                   // farmer question
    val predictedClass: String,          // snapshot of class used
    val topDocIdsCsv: String,            // "Tomato_Late_blight#core"
    val answerText: String,              // final generated text
    val createdAt: Long = System.currentTimeMillis()
)
