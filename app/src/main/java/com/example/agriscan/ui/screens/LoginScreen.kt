package com.example.agriscan.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.agriscan.ui.AuthState
import com.example.agriscan.ui.AuthViewModel

@Composable
fun LoginScreen(vm: AuthViewModel, onNavigate: (String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val state by vm.state.collectAsState()

    LaunchedEffect(state) {
        when (state) {
            AuthState.SignedIn -> onNavigate("home")
            is AuthState.RequiresMfa -> onNavigate("mfa")
            else -> Unit
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("AgriScan Login", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") })
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text("Password") }, visualTransformation = PasswordVisualTransformation()
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = { vm.signIn(email, password) }, enabled = state !is AuthState.Loading) {
                Text("Sign In")
            }
            TextButton(onClick = { onNavigate(AuthScreens.Register.route) }) { Text("Create Account") }
            TextButton(onClick = { onNavigate("forgot") }) { Text("Forgot password?") }
            if (state is AuthState.Error) {
                Spacer(Modifier.height(8.dp))
                Text((state as AuthState.Error).message, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
