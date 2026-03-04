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
import com.babybloom.presentation.screens.LoginScreen
import com.babybloom.presentation.screens.RegisterScreen
import com.babybloom.presentation.screens.ParentView

object Routes {
    const val LOGIN    = "login"
    const val REGISTER = "register"
    const val HOME     = "home"
    const val PARNET   = "PARNETVIEW"
}

@Composable
fun BabyBloomNavGraph(
    sessionManager: SessionManager,
    navController: NavHostController = rememberNavController()
) {
    val isLoggedIn by sessionManager.isLoggedIn.collectAsStateWithLifecycle(initialValue = false)

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            navController.navigate(Routes.PARNET) {
                popUpTo(Routes.LOGIN) { inclusive = true }
            }
        }
    }

    NavHost(
        navController    = navController,
        startDestination = Routes.LOGIN
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onNavigateToHome = {
                    navController.navigate(Routes.PARNET) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
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
                    navController.navigate(Routes.PARNET) {
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

        composable(Routes.HOME) {
            // reserved for future use
        }

        composable(Routes.PARNET) {
            ParentView()  // ← no callbacks for now
        }
    }
}