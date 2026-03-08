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
import com.babybloom.presentation.screens.ChangePasswordScreen
import com.babybloom.presentation.screens.LandingScreen
import com.babybloom.presentation.screens.LoginScreen
import com.babybloom.presentation.screens.RegisterScreen
import com.babybloom.presentation.screens.ParentHomeScreen
object Routes {
    const val LANDING         = "landing"
    const val LOGIN           = "login"
    const val REGISTER        = "register"
    const val CHANGE_PASSWORD = "change_password"   // ← NEW
    const val ADD_CHILD       = "add_child"
    const val HOME            = "home"
}

@Composable
fun BabyBloomNavGraph(
    sessionManager: SessionManager,
    navController : NavHostController = rememberNavController()
) {
    val isLoggedIn     by sessionManager.isLoggedIn.collectAsStateWithLifecycle(initialValue = false)
    val hasSeenLanding by sessionManager.hasSeenLanding.collectAsStateWithLifecycle(initialValue = false)

    // Already have an active session → go straight to Home
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            navController.navigate(Routes.HOME) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    // Already saw Landing → skip it, go to Login
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
                },
                onNavigateToChangePassword = {
                    // Push ChangePassword on top of Login
                    // Back button on that screen will pop back here
                    navController.navigate(Routes.CHANGE_PASSWORD)
                }
            )
        }

        // ── REGISTER ───────────────────────────────────────────────────────
        composable(Routes.REGISTER) {
            RegisterScreen(
                onCreateAccount = {
                    // After register → AddChild (mandatory first-time setup)
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

        // ── CHANGE PASSWORD — reached from Login forgot password link ───────
        composable(Routes.CHANGE_PASSWORD) {
            ChangePasswordScreen(
                onSaveClick = {
                    // Password changed successfully → back to Login
                    // popUpTo removes ChangePassword from back stack
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.CHANGE_PASSWORD) { inclusive = true }
                    }
                },
                onBackClick = {
                    // Back button → simply pop ChangePassword off the stack
                    // Returns to Login which is underneath
                    navController.popBackStack()
                }
            )
        }

        // ── ADD CHILD — reached only from Register ─────────────────────────
        composable(Routes.ADD_CHILD) {
            AddChildScreen(
                onSaveChild = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // ── HOME ───────────────────────────────────────────────────────────
        composable(Routes.HOME) {
            ParentHomeScreen(
                onNavigate = { route ->
                    when (route) {
                        "children" -> {
                            // TODO: Navigate to Children screen when created
                            // navController.navigate(Routes.CHILDREN)
                        }
                        "settings" -> {
                            // TODO: Navigate to Settings screen when created
                            // navController.navigate(Routes.SETTINGS)
                        }
                        "add_child" -> {
                            navController.navigate(Routes.ADD_CHILD)
                        }
                        else -> {}
                    }
                }
            )
        }
    }
}