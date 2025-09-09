package com.example.agriscan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.agriscan.ui.AuthState
import com.example.agriscan.ui.defaultAuthVM
import com.example.agriscan.ui.screens.*
import com.example.agriscan.ui.screens.home.HomeShell
import com.example.agriscan.ui.theme.AgriScanTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AgriScanTheme(dynamicColor = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val nav = rememberNavController()
                    val vm = defaultAuthVM()
                    val state by vm.state.collectAsState()

                    NavHost(navController = nav, startDestination = "splash") {

                        composable("splash") {
                            SplashScreen(vm = vm, onRoute = { route ->
                                nav.navigate(route) { popUpTo("splash") { inclusive = true } }
                            })
                        }

                        composable(AuthScreens.Login.route) {
                            LaunchedEffect(state) {
                                if (state is AuthState.RequiresMfa) nav.navigate("mfa")
                            }
                            LoginScreen(vm = vm, onNavigate = { nav.navigate(it) })
                        }

                        composable(AuthScreens.Register.route) {
                            RegisterScreen(
                                vm = vm,
                                onNavigate = { nav.navigate(it) },
                                onBack = { nav.popBackStack() }
                            )
                        }

                        composable(AuthScreens.VerifyEmail.route) {
                            VerifyEmailScreen(
                                vm = vm,
                                onContinue = { nav.navigate(AuthScreens.LinkPhone.route) }
                            )
                        }

                        composable(AuthScreens.LinkPhone.route) {
                            LinkPhoneScreen(onDone = {
                                nav.navigate("home") { popUpTo(0) }
                            })
                        }

                        composable("mfa") {
                            val resolver = (state as? AuthState.RequiresMfa)?.resolver
                            if (resolver != null) {
                                ResolveMfaScreen(resolver = resolver) {
                                    nav.navigate("home") { popUpTo(0) }
                                }
                            } else {
                                LaunchedEffect(Unit) {
                                    nav.navigate(AuthScreens.Login.route) { popUpTo(0) }
                                }
                            }
                        }

                        composable("forgot") {
                            ForgotPasswordScreen(vm = vm, onBack = { nav.popBackStack() })
                        }

                        // Home uses the bottom-nav shell
                        composable("home") {
                            HomeShell(
                                vm = vm,
                                onSignedOut = { nav.navigate("splash") { popUpTo(0) } }
                            )
                        }
                    }
                }
            }
        }
    }
}
