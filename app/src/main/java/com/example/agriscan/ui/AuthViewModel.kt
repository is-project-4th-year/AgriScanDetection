package com.example.agriscan.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.agriscan.data.AuthRepository
import com.example.agriscan.data.SignInResult
import com.google.firebase.auth.MultiFactorResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class AuthState {
    data object Idle : AuthState()
    data object Loading : AuthState()
    data class Error(val message: String) : AuthState()
    data class Info(val message: String) : AuthState()
    data object Registered : AuthState()
    data object SignedIn : AuthState()
    data class RequiresMfa(val resolver: MultiFactorResolver) : AuthState()
}

sealed class SessionState {
    data object Unknown : SessionState()
    data object SignedOut : SessionState()
    data object SignedInUnverified : SessionState()
    data object SignedInVerified : SessionState()
}

class AuthViewModel(private val repo: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow<AuthState>(AuthState.Idle)
    val state: StateFlow<AuthState> = _state

    private val _session = MutableStateFlow<SessionState>(SessionState.Unknown)
    val session: StateFlow<SessionState> = _session

    fun checkSession() {
        val u = repo.currentUser()
        _session.value = when {
            u == null -> SessionState.SignedOut
            u.isEmailVerified -> SessionState.SignedInVerified
            else -> SessionState.SignedInUnverified
        }
    }

    fun register(email: String, password: String) {
        // Prevent “register again” while signed in
        repo.currentUser()?.let { u ->
            _state.value = AuthState.Error("Already signed in as ${u.email ?: "current user"}. Sign out first.")
            return
        }
        _state.value = AuthState.Loading
        viewModelScope.launch {
            val res = repo.registerEmail(email, password)
            _state.value = res.fold(
                onSuccess = { AuthState.Registered },
                onFailure = { AuthState.Error(it.message ?: "Registration failed") }
            )
        }
    }

    fun signIn(email: String, password: String) {
        _state.value = AuthState.Loading
        viewModelScope.launch {
            when (val res = repo.signInEmail(email, password)) {
                is SignInResult.Success -> {
                    _state.value = AuthState.SignedIn
                    checkSession()
                }
                is SignInResult.RequiresSecondFactor -> _state.value = AuthState.RequiresMfa(res.resolver)
                is SignInResult.Error -> _state.value = AuthState.Error(res.message)
            }
        }
    }

    fun sendPasswordReset(email: String) {
        _state.value = AuthState.Loading
        viewModelScope.launch {
            val res = repo.sendPasswordReset(email)
            _state.value = res.fold(
                onSuccess = { AuthState.Info("Password reset email sent") },
                onFailure = { AuthState.Error(it.message ?: "Could not send reset email") }
            )
        }
    }

    fun refreshEmailVerified(onVerified: () -> Unit) {
        viewModelScope.launch {
            val ok = repo.refreshEmailVerification()
            if (ok) {
                checkSession()
                onVerified()
            }
        }
    }

    fun signOut() {
        repo.signOut()
        _session.value = SessionState.SignedOut
        _state.value = AuthState.Idle
    }
}
