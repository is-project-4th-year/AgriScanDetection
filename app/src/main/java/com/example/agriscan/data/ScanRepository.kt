package com.example.agriscan.data

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

class ScanRepository(
    private val storage: FirebaseStorage,
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    /**
     * Uploads the photo to Storage and writes a Firestore doc:
     * users/{uid}/scans/{id} { url, path, ts }
     * @return the created scan document ID.
     */
    suspend fun uploadScan(uri: Uri): Result<String> = runCatching {
        val uid = auth.currentUser?.uid ?: error("Not signed in")
        val path = "scans/$uid/${System.currentTimeMillis()}.jpg"
        val ref = storage.reference.child(path)

        ref.putFile(uri).await()
        val url = ref.downloadUrl.await().toString()

        val doc = db.collection("users").document(uid)
            .collection("scans").document()

        val id = doc.id
        doc.set(
            mapOf(
                "id" to id,
                "url" to url,
                "path" to path,
                "ts" to FieldValue.serverTimestamp()
            )
        ).await()
        id
    }
}
