package com.example.agriscan.ui.screens

import android.app.Activity
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.agriscan.ui.defaultRepo
import com.google.firebase.FirebaseException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

private const val TAG = "LinkPhone"

@Composable
fun LinkPhoneScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current as Activity
    val scope = rememberCoroutineScope()
    val repo = remember { defaultRepo() }

    // Don't enroll again if already has a phone factor
    val alreadyEnrolled =
        Firebase.auth.currentUser?.multiFactor?.enrolledFactors?.isNotEmpty() == true
    if (alreadyEnrolled) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("You already have 2FA enrolled for this account.")
                Spacer(Modifier.height(12.dp))
                Button(onClick = onDone) { Text("Continue") }
            }
        }
        return
    }

    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var enrolling by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    fun enroll(cred: PhoneAuthCredential) {
        enrolling = true
        scope.launch {
            val res = repo.enrollPhoneSecondFactor(cred)
            enrolling = false
            if (res.isSuccess) onDone() else {
                errorText = res.exceptionOrNull()?.localizedMessage ?: "Failed to enroll 2FA"
                Log.e(TAG, "Enroll failed", res.exceptionOrNull())
            }
        }
    }

    val callbacks = remember {
        object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                Log.d(TAG, "onVerificationCompleted (instant/auto)")
                enroll(credential)
            }
            override fun onVerificationFailed(e: FirebaseException) {
                sending = false
                errorText = e.localizedMessage ?: "Verification failed"
                Log.e(TAG, "onVerificationFailed", e)
            }
            override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                verificationId = id
                sending = false
                Log.d(TAG, "onCodeSent: $id")
            }
        }
    }

    fun sendCode() {
        val e164 = phone.trim().replace(" ", "")
        if (!e164.startsWith("+") || e164.length < 10) {
            errorText = "Enter phone as +2547XXXXXXXX"
            return
        }
        errorText = null
        sending = true
        val options = PhoneAuthOptions.newBuilder(Firebase.auth)
            .setPhoneNumber(e164)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(ctx)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Secure your account with 2FA (SMS)", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone e.g. +2547xxxxxxx") })
            Spacer(Modifier.height(8.dp))
            Button(onClick = { sendCode() }, enabled = !sending) {
                Text(if (sending) "Sending..." else "Send Code")
            }
            Spacer(Modifier.height(12.dp))

            if (verificationId != null) {
                OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Enter OTP") })
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        verificationId?.let { id ->
                            val cred = PhoneAuthProvider.getCredential(id, code.trim())
                            enroll(cred)
                        }
                    },
                    enabled = !enrolling && code.length >= 6
                ) { Text(if (enrolling) "Enrolling..." else "Verify & Enroll") }
            }

            errorText?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
