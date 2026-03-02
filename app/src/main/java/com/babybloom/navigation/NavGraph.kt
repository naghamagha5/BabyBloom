package com.babybloom.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.babybloom.di.SessionManager
import com.babybloom.presentation.screens.LoginScreen
import com.babybloom.presentation.viewmodels.LoginViewModel

// ─── Route constants (no hardcoded strings anywhere else) ─────────────────────
object Routes {
    const val LOGIN    = "login"
    const val REGISTER = "register"
    const val HOME     = "home"
}

@Composable
fun BabyBloomNavGraph(
    sessionManager: SessionManager,
    navController: NavHostController = rememberNavController()
) {
    // Check if user is already logged in — auto-route to Home
    val isLoggedIn by sessionManager.isLoggedIn.collectAsStateWithLifecycle(initialValue = false)

    val startDestination = if (isLoggedIn) Routes.HOME else Routes.LOGIN

    NavHost(
        navController    = navController,
        startDestination = startDestination
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onNavigateToHome = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToRegister = {
                    navController.navigate(Routes.REGISTER)
                }
            )
        }

        composable(Routes.REGISTER) {
            // Your RegisterScreen goes here
        }

//        composable(Routes.HOME) {
//            HomeScreen() // your existing HomeScreen
//        }
    }
}