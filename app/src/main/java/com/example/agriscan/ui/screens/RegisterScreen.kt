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
import com.example.agriscan.ui.util.Validators

@Composable
fun RegisterScreen(vm: AuthViewModel, onNavigate: (String) -> Unit, onBack: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var emailErr by remember { mutableStateOf<String?>(null) }
    var pwErr by remember { mutableStateOf<String?>(null) }
    val state by vm.state.collectAsState()

    LaunchedEffect(state) { if (state is AuthState.Registered) onNavigate(AuthScreens.VerifyEmail.route) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Create Account", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = email, onValueChange = { email = it; emailErr = null }, label = { Text("Email") }, isError = emailErr != null)
            if (emailErr != null) Text(emailErr!!, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = password, onValueChange = { password = it; pwErr = null }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), isError = pwErr != null)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = confirm, onValueChange = { confirm = it }, label = { Text("Confirm Password") }, visualTransformation = PasswordVisualTransformation())
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val e = email.trim()
                    when {
                        !Validators.isValidEmail(e) -> emailErr = "Enter a valid email"
                        !Validators.isStrongPassword(password) -> pwErr = "Use 8+ chars with upper, lower, digit & symbol"
                        password != confirm -> pwErr = "Passwords do not match"
                        else -> vm.register(e, password)
                    }
                },
                enabled = state !is AuthState.Loading
            ) { Text("Register") }
            TextButton(onClick = onBack) { Text("Back") }
            if (state is AuthState.Error) {
                Spacer(Modifier.height(8.dp))
                Text((state as AuthState.Error).message, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
