package com.example.agriscan.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.agriscan.data.AuthRepository
import com.example.agriscan.data.FieldRepository
import com.example.agriscan.data.LibraryRepository
import com.example.agriscan.data.local.AppDatabase
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

// --- Auth ---
fun defaultRepo() = AuthRepository(Firebase.auth)

// --- Single Room DB instance remembered in Compose ---
@Composable
fun defaultDb(): AppDatabase {
    val appCtx = LocalContext.current.applicationContext
    return remember { AppDatabase.get(appCtx) }
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
