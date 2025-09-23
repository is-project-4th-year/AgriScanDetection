package com.example.agriscan.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import coil.compose.rememberAsyncImagePainter
import com.example.agriscan.data.ScanStore
import com.example.agriscan.ui.defaultLibRepo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ScanDetailScreen(entry: NavBackStackEntry) {
    val id = entry.arguments?.getString("id") ?: return
    val scan = ScanStore.get(id) ?: return
    val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

    // Persist this capture to the local Library (Room) once per screen-open
    val libRepo = defaultLibRepo()
    var saved by remember(id) { mutableStateOf(false) }
    LaunchedEffect(id) {
        if (!saved) {
            // fieldId = null because we’re just archiving the raw capture here.
            // You can assign to a Field later from Library/Details.
            libRepo.addCapture(uriString = scan.uri.toString(), fieldId = null)
            saved = true
        }
    }

    Column(Modifier.fillMaxSize()) {
        Image(
            painter = rememberAsyncImagePainter(scan.uri),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.height(16.dp))
        Column(Modifier.padding(16.dp)) {
            Text("Result", style = MaterialTheme.typography.titleMedium)
            Text(scan.result ?: "Unprocessed", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))
            Text(
                "Captured ${sdf.format(Date(scan.timestamp))}",
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
