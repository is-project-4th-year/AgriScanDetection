package com.example.agriscan.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [FieldEntity::class, CaptureEntity::class, AdviceSessionEntity::class],
    version = 5,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fieldDao(): FieldDao
    abstract fun captureDao(): CaptureDao
    abstract fun adviceDao(): AdviceDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        // --- Helpers for robust migrations ---
        private fun columnExists(db: SupportSQLiteDatabase, table: String, column: String): Boolean {
            db.query("PRAGMA table_info($table)").use { c ->
                val nameIdx = c.getColumnIndex("name")
                while (c.moveToNext()) {
                    if (nameIdx >= 0 && c.getString(nameIdx) == column) return true
                }
            }
            return false
        }
        private fun ensureCapturePredictionColumns(db: SupportSQLiteDatabase) {
            if (!columnExists(db, "captures", "predictedClass")) {
                db.execSQL("ALTER TABLE captures ADD COLUMN predictedClass TEXT")
            }
            if (!columnExists(db, "captures", "top1Prob")) {
                db.execSQL("ALTER TABLE captures ADD COLUMN top1Prob REAL")
            }
            if (!columnExists(db, "captures", "modelVersion")) {
                db.execSQL("ALTER TABLE captures ADD COLUMN modelVersion TEXT")
            }
        }
        private fun ensureAdviceSessions(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS advice_sessions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    captureId INTEGER NOT NULL,
                    query TEXT NOT NULL,
                    predictedClass TEXT NOT NULL,
                    topDocIdsCsv TEXT NOT NULL,
                    answerText TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    FOREIGN KEY(captureId) REFERENCES captures(id) ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_advice_sessions_captureId ON advice_sessions(captureId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_advice_sessions_createdAt ON advice_sessions(createdAt)")
        }

        // --- Original migrations you already had ---
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureCapturePredictionColumns(db)
            }
        }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureAdviceSessions(db)
            }
        }
        // Keep this (harmless), but we’ll also add robust 3→5 / 4→5 below.
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // no-op previously; leave as-is
            }
        }

        // --- Robust rescue migrations ---
        // If a device is at v3 (or arrives at v3 via earlier paths) but *still* lacks the 3 columns,
        // this will add them and assert advice_sessions.
        private val MIGRATION_3_5 = object : Migration(3, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureCapturePredictionColumns(db)
                ensureAdviceSessions(db)
            }
        }
        // If a device is already at v4 but columns are missing (due to earlier fallback builds),
        // this will add them and assert advice_sessions.
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureCapturePredictionColumns(db)
                ensureAdviceSessions(db)
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "agriscan.db"   // If stubborn restore persists, temporarily change to "agriscan_v5.db"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_3_5,
                        MIGRATION_4_5
                    )
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
