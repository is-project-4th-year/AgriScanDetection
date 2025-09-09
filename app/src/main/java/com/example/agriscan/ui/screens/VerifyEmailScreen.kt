package com.example.agriscan.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.agriscan.ui.AuthViewModel
import com.example.agriscan.ui.defaultAuthVM

@Composable
fun VerifyEmailScreen(onContinue: () -> Unit, vm: AuthViewModel? = null) {
    val viewModel = vm ?: defaultAuthVM()
    var checking by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("We sent a verification email. Please verify, then tap Continue.")
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                checking = true
                viewModel.refreshEmailVerified {
                    checking = false
                    onContinue()
                }
            }) { Text(if (checking) "Checking..." else "Continue") }
        }
    }
}
