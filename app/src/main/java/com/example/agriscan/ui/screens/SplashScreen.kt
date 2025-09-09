package com.example.agriscan.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.agriscan.ui.AuthViewModel
import com.example.agriscan.ui.SessionState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp

@Composable
fun SplashScreen(vm: AuthViewModel, onRoute: (String) -> Unit) {
    val session by vm.session.collectAsState()
    LaunchedEffect(Unit) { vm.checkSession() }
    LaunchedEffect(session) {
        when (session) {
            SessionState.SignedInVerified -> onRoute("home")
            SessionState.SignedInUnverified -> onRoute(AuthScreens.VerifyEmail.route)
            SessionState.SignedOut -> onRoute(AuthScreens.Login.route)
            else -> Unit
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("AgriScan", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text("Field insights, made simple", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(20.dp))
            CircularProgressIndicator()
        }
    }
}
