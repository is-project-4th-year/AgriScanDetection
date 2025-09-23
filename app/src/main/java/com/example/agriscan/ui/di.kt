package com.example.agriscan.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.room.Room
import com.example.agriscan.data.AuthRepository
import com.example.agriscan.data.FieldRepository
import com.example.agriscan.data.LibraryRepository
import com.example.agriscan.data.local.AgriDb
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

// --- Auth ---
fun defaultRepo() = AuthRepository(Firebase.auth)

// --- Single Room DB instance remembered in Compose ---
@Composable
fun defaultDb(): AgriDb {
    val appCtx = LocalContext.current.applicationContext
    return remember {
        Room.databaseBuilder(appCtx, AgriDb::class.java, "agriscan.db")
            // Dev-only safety: if schema changes, wipe & recreate instead of crashing.
            // You chose Option A, so we do NOT add explicit migrations here.
            .fallbackToDestructiveMigration()
            .build()
    }
}

// --- Repositories backed by Room ---
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
