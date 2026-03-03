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
import com.babybloom.presentation.screens.LandingScreen
import com.babybloom.presentation.screens.LoginScreen
import com.babybloom.presentation.screens.RegisterScreen

object Routes {
    const val LANDING  = "landing"   // ← NEW
    const val LOGIN    = "login"
    const val REGISTER = "register"
    const val HOME     = "home"
}

@Composable
fun BabyBloomNavGraph(
    sessionManager: SessionManager,
    navController: NavHostController = rememberNavController()
) {
    // ── Observe both flags from DataStore ──────────────────────────────────
    val isLoggedIn     by sessionManager.isLoggedIn.collectAsStateWithLifecycle(initialValue = false)
    val hasSeenLanding by sessionManager.hasSeenLanding.collectAsStateWithLifecycle(initialValue = false)

    // ── If already logged in from a previous session → go straight to Home ─
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            navController.navigate(Routes.HOME) {
                popUpTo(0) { inclusive = true }   // clear entire back stack
            }
        }
    }

    // ── If landing was already seen → skip it, go to Login ────────────────
    // This only fires on first composition, not on every recomposition
    LaunchedEffect(hasSeenLanding) {
        if (hasSeenLanding) {
            navController.navigate(Routes.LOGIN) {
                popUpTo(Routes.LANDING) { inclusive = true }
            }
        }
    }

    // ── startDestination is LANDING — NavGraph decides where to redirect ───
    NavHost(
        navController    = navController,
        startDestination = Routes.LANDING
    ) {

        // ── LANDING — shown only once on new device ────────────────────────
        composable(Routes.LANDING) {
            LandingScreen(
                sessionManager = sessionManager,
                onStartClick = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.LANDING) { inclusive = true }
                    }
                }
            )
        }

        // ── LOGIN ──────────────────────────────────────────────────────────
        composable(Routes.LOGIN) {
            LoginScreen(
                onNavigateToHome = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToRegister = {
                    navController.navigate(Routes.REGISTER)
                }
            )
        }

        // ── REGISTER ───────────────────────────────────────────────────────
        composable(Routes.REGISTER) {
            RegisterScreen(
                onCreateAccount = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onLoginClick = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.REGISTER) { inclusive = true }
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