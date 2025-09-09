package com.example.agriscan.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.agriscan.ui.AuthState
import com.example.agriscan.ui.AuthViewModel
import com.example.agriscan.ui.util.Validators
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ForgotPasswordScreen(
    vm: AuthViewModel,
    onBack: () -> Unit
) {
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var email by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }

    // When reset succeeds, show a snackbar then navigate back to Login
    LaunchedEffect(state) {
        val s = state
        if (s is AuthState.Info) {
            snackbar.showSnackbar(s.message)
            delay(1500)
            onBack()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbar) }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Reset your password", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; emailError = null },
                    label = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    isError = emailError != null
                )
                if (emailError != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(emailError!!, color = MaterialTheme.colorScheme.error)
                }

                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    val e = email.trim()
                    if (!Validators.isValidEmail(e)) {
                        emailError = "Enter a valid email"
                    } else {
                        vm.sendPasswordReset(e)
                    }
                }) { Text("Send reset email") }

                TextButton(onClick = onBack) { Text("Back") }

                when (val s = state) {
                    is AuthState.Error -> {
                        Spacer(Modifier.height(8.dp))
                        Text(s.message, color = MaterialTheme.colorScheme.error)
                    }
                    else -> Unit
                }
            }
        }
    }
}
