package com.example.agriscan.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.room.Room
import com.example.agriscan.data.AuthRepository
import com.example.agriscan.data.FieldRepository
import com.example.agriscan.data.LibraryRepository
import com.example.agriscan.data.local.AgriDb
import com.google.firebase.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

// --- Auth ---
fun defaultRepo() = AuthRepository(Firebase.auth)

// --- Internal: central DB builder with env-aware config ---
private fun buildDb(appCtx: Context): AgriDb {
    val builder = Room.databaseBuilder(appCtx, AgriDb::class.java, "agriscan.db")

    if (BuildConfig.DEBUG) {
        // DEV ONLY: if schema changes and you bumped the version,
        // Room will drop & recreate instead of crashing.
        builder.fallbackToDestructiveMigration()
        // Optionally, also handle downgrades in dev:
        // builder.fallbackToDestructiveMigrationOnDowngrade()
    } else {
        // RELEASE: add real migrations here as you bump versions.
        // builder.addMigrations(MIGRATION_1_2, MIGRATION_2_3, ...)
    }

    return builder.build()
}

// --- Single Room DB instance remembered in Compose ---
@Composable
fun defaultDb(): AgriDb {
    val appCtx = LocalContext.current.applicationContext
    return remember { buildDb(appCtx) }
}

// --- Repositories backed by Room ---
// NOTE: Ensure there is only ONE definition of these functions in your project.
// If you still have a previous di_local.kt with the same names, remove/rename it.
@Composable
fun defaultFieldRepo(): FieldRepository {
    val db = defaultDb()
    return remember { FieldRepository(db.fieldDao()) }
}

@Composable
fun defaultLibRepo(): LibraryRepository {
    val db = defaultDb()
    return remember { LibraryRepository(db.captureDao()) }
}

// --- ViewModel provider ---
@Composable
fun defaultAuthVM(): AuthViewModel = remember { AuthViewModel(defaultRepo()) }
