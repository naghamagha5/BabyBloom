package com.babybloom.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.babybloom.di.SessionManager
import com.babybloom.presentation.screens.AddChildScreen
import com.babybloom.presentation.screens.LandingScreen
import com.babybloom.presentation.screens.LoginScreen
import com.babybloom.presentation.screens.RegisterScreen

object Routes {
    const val LANDING   = "landing"
    const val LOGIN     = "login"
    const val REGISTER  = "register"
    const val ADD_CHILD = "add_child"   // ← NEW: only reached from Register
    const val HOME      = "home"
}

@Composable
fun BabyBloomNavGraph(
    sessionManager: SessionManager,
    navController : NavHostController = rememberNavController()
) {
    val isLoggedIn     by sessionManager.isLoggedIn.collectAsStateWithLifecycle(initialValue = false)
    val hasSeenLanding by sessionManager.hasSeenLanding.collectAsStateWithLifecycle(initialValue = false)

    // ── Already have an active session → go straight to Home ──────────────
    // This handles app restarts where the user was previously logged in
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            navController.navigate(Routes.HOME) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    // ── Already saw Landing → skip it, go to Login ─────────────────────────
    LaunchedEffect(hasSeenLanding) {
        if (hasSeenLanding) {
            navController.navigate(Routes.LOGIN) {
                popUpTo(Routes.LANDING) { inclusive = true }
            }
        }
    }

    NavHost(
        navController    = navController,
        startDestination = Routes.LANDING
    ) {

        // ── LANDING — shown only once ever ─────────────────────────────────
        composable(Routes.LANDING) {
            LandingScreen(
                sessionManager = sessionManager,
                onStartClick   = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.LANDING) { inclusive = true }
                    }
                }
            )
        }

        // ── LOGIN — reached from Landing or after logout ───────────────────
        composable(Routes.LOGIN) {
            LoginScreen(
                onNavigateToHome = {
                    // Login goes straight to Home — no AddChild needed
                    navController.navigate(Routes.HOME) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToRegister = {
                    navController.navigate(Routes.REGISTER)
                }
            )
        }

        // ── REGISTER — reached from Login ──────────────────────────────────
        composable(Routes.REGISTER) {
            RegisterScreen(
                onCreateAccount = {
                    // After register → AddChild (mandatory first-time setup)
                    // NOT Home — user must add a child before using the app
                    navController.navigate(Routes.ADD_CHILD) {
                        // Remove Register from back stack so back button
                        // does NOT take user back to register form
                        popUpTo(Routes.REGISTER) { inclusive = true }
                    }
                },
                onLoginClick = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.REGISTER) { inclusive = true }
                    }
                }
            )
        }

        // ── ADD CHILD — reached only from Register ─────────────────────────
        composable(Routes.ADD_CHILD) {
            AddChildScreen(
                // ViewModel reads userId from SessionManager internally —
                // no userId param needed here
                onSaveChild = {
                    // Child saved → now go to Home
                    // Clear entire back stack so back button does NOT
                    // take user back to AddChild or Register
                    navController.navigate(Routes.HOME) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // ── HOME ───────────────────────────────────────────────────────────
        composable(Routes.HOME) {
            // TODO: replace with your real HomeScreen()
            Text("Home — coming soon")
        }
    }
}