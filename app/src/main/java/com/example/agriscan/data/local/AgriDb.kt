package com.example.agriscan.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [FieldEntity::class, CaptureEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AgriDb : RoomDatabase() {
    abstract fun fieldDao(): FieldDao
    abstract fun captureDao(): CaptureDao
}
