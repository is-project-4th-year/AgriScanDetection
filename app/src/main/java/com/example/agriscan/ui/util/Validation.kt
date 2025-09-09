package com.example.agriscan.ui.util

import android.util.Patterns

object Validators {
    fun isValidEmail(email: String): Boolean =
        Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()

    fun isStrongPassword(pw: String): Boolean {
        if (pw.length < 8) return false
        val hasUpper = pw.any { it.isUpperCase() }
        val hasLower = pw.any { it.isLowerCase() }
        val hasDigit = pw.any { it.isDigit() }
        val symbols = "!@#\$%^&*()_+[]{}|;:'\",.<>?/\\`~-="
        val hasSymbol = pw.any { it in symbols }
        return hasUpper && hasLower && hasDigit && hasSymbol
    }
}
