package com.example.agriscan.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.agriscan.ui.AuthViewModel

@Composable
fun HomeScreen(
    vm: AuthViewModel,
    onSignedOut: () -> Unit,
    onOpenScanner: () -> Unit = {}
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Welcome to AgriScan 🌿", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(20.dp))
            Button(onClick = onOpenScanner) { Text("Open Scanner") }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { vm.signOut(); onSignedOut() }) { Text("Sign out") }
        }
    }
}
