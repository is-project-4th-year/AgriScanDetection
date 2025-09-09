package com.example.agriscan.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthMultiFactorException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneMultiFactorGenerator
import kotlinx.coroutines.tasks.await

sealed interface SignInResult {
    data object Success : SignInResult
    data class RequiresSecondFactor(val resolver: com.google.firebase.auth.MultiFactorResolver) : SignInResult
    data class Error(val message: String) : SignInResult
}

private fun normalize(email: String) = email.trim().lowercase()

class AuthRepository(private val auth: FirebaseAuth) {

    suspend fun registerEmail(email: String, password: String): Result<Unit> = runCatching {
        val e = normalize(email)
        auth.createUserWithEmailAndPassword(e, password).await()
        auth.currentUser?.sendEmailVerification()?.await()
    }

    suspend fun signInEmail(email: String, password: String): SignInResult = try {
        val e = normalize(email)
        auth.signInWithEmailAndPassword(e, password).await()
        SignInResult.Success
    } catch (e: FirebaseAuthMultiFactorException) {
        SignInResult.RequiresSecondFactor(e.resolver)
    } catch (e: Exception) {
        SignInResult.Error(e.message ?: "Unknown error")
    }

    suspend fun refreshEmailVerification(): Boolean {
        val user = auth.currentUser ?: return false
        user.reload().await()
        return user.isEmailVerified
    }

    suspend fun enrollPhoneSecondFactor(credential: PhoneAuthCredential): Result<Unit> = runCatching {
        val user = auth.currentUser ?: error("No signed-in user")
        val assertion = PhoneMultiFactorGenerator.getAssertion(credential)
        user.multiFactor.enroll(assertion, "primary").await()
    }

    suspend fun resolveMfaSignIn(
        resolver: com.google.firebase.auth.MultiFactorResolver,
        credential: PhoneAuthCredential
    ): Result<Unit> = runCatching {
        val assertion = PhoneMultiFactorGenerator.getAssertion(credential)
        resolver.resolveSignIn(assertion).await()
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        auth.sendPasswordResetEmail(normalize(email)).await()
    }

    fun signOut() = auth.signOut()
    fun currentUser() = auth.currentUser
}
