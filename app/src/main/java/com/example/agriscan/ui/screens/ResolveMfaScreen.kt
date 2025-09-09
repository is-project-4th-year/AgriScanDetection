package com.example.agriscan.ui.screens

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.agriscan.ui.defaultRepo
import com.google.firebase.FirebaseException
import com.google.firebase.auth.MultiFactorResolver
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.PhoneMultiFactorInfo
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@Composable
fun ResolveMfaScreen(
    resolver: MultiFactorResolver,
    onDone: () -> Unit
) {
    val ctx = LocalContext.current as Activity
    val scope = rememberCoroutineScope()
    val repo = remember { defaultRepo() }

    val hint = resolver.hints.firstOrNull() as? PhoneMultiFactorInfo
    var verificationId by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var resolving by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    // --- define the resolver helper BEFORE using it in callbacks ---
    fun resolveCred(cred: PhoneAuthCredential) {
        resolving = true
        errorText = null
        scope.launch {
            val r = repo.resolveMfaSignIn(resolver, cred)
            resolving = false
            if (r.isSuccess) onDone()
            else errorText = r.exceptionOrNull()?.localizedMessage ?: "Failed to complete sign-in"
        }
    }

    val callbacks = remember {
        object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(cred: PhoneAuthCredential) {
                // Auto-retrieval or instant
                resolveCred(cred)
            }
            override fun onVerificationFailed(e: FirebaseException) {
                sending = false
                errorText = e.localizedMessage ?: "Verification failed"
            }
            override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                verificationId = id
                sending = false
            }
        }
    }

    fun sendCode() {
        if (hint == null) { errorText = "No enrolled phone found"; return }
        sending = true
        errorText = null
        val opts = PhoneAuthOptions.newBuilder(Firebase.auth)
            .setActivity(ctx)
            .setTimeout(60L, TimeUnit.SECONDS)
            // 🔑 MFA sign-in resolution path
            .setMultiFactorSession(resolver.session)
            .setMultiFactorHint(hint)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(opts)
    }

    LaunchedEffect(Unit) { sendCode() } // auto-send on open

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Enter the code sent to ${hint?.phoneNumber ?: "your phone"}")
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("OTP") })
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    verificationId?.let {
                        val cred = PhoneAuthProvider.getCredential(it, code.trim())
                        resolveCred(cred)
                    }
                },
                enabled = !resolving && (verificationId != null) && code.length >= 6
            ) { Text(if (resolving) "Verifying..." else "Verify") }

            TextButton(onClick = { if (!sending) sendCode() }) {
                Text(if (sending) "Sending..." else "Resend code")
            }

            errorText?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
