package com.example.agriscan.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "fields")
data class FieldEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    // keep NOT NULL per your current schema; default to "" to avoid crashes
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * We add fieldId so captures can be assigned to a field.
 * FK uses SET_NULL so deleting a field won’t delete images.
 */
@Entity(
    tableName = "captures",
    foreignKeys = [
        ForeignKey(
            entity = FieldEntity::class,
            parentColumns = ["id"],
            childColumns = ["fieldId"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.NO_ACTION
        )
    ],
    indices = [Index("fieldId")]
)
data class CaptureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val uri: String,
    val fieldId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
