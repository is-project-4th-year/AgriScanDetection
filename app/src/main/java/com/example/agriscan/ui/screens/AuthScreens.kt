package com.example.agriscan.ui.screens

sealed class AuthScreens(val route: String) {
    data object Login : AuthScreens("login")
    data object Register : AuthScreens("register")
    data object VerifyEmail : AuthScreens("verify_email")
    data object LinkPhone : AuthScreens("link_phone")
}