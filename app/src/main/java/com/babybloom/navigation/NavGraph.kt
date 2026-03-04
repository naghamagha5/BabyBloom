package com.babybloom.navigation

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
import com.babybloom.presentation.screens.ParentView

object Routes {
    const val LANDING   = "landing"
    const val LOGIN     = "login"
    const val REGISTER  = "register"
    const val ADD_CHILD = "add_child"   // only reached from Register
    const val HOME      = "home"
    const val PARNET    = "PARNETVIEW"
}

@Composable
fun BabyBloomNavGraph(
    sessionManager: SessionManager,
    navController : NavHostController = rememberNavController()
) {
    val isLoggedIn     by sessionManager.isLoggedIn.collectAsStateWithLifecycle(initialValue = false)
    val hasSeenLanding by sessionManager.hasSeenLanding.collectAsStateWithLifecycle(initialValue = false)

    // ── Already have an active session → go straight to Home ──────────────
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

        composable(Routes.REGISTER) {
            RegisterScreen(
                onCreateAccount = {
                    navController.navigate(Routes.ADD_CHILD) {
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

        composable(Routes.ADD_CHILD) {
            AddChildScreen(
                onSaveChild = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.HOME) {
            // reserved for future use
        }

        composable(Routes.PARNET) {
            ParentView()
        }
    }
}