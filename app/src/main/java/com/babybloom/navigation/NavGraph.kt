package com.babybloom.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.babybloom.di.SessionManager
import com.babybloom.presentation.screens.AddChildScreen
import com.babybloom.presentation.screens.ChangePasswordScreen
import com.babybloom.presentation.screens.ChildProfileScreen
import com.babybloom.presentation.screens.LandingScreen
import com.babybloom.presentation.screens.LoginScreen
import com.babybloom.presentation.screens.ParentShell
import com.babybloom.presentation.screens.RegisterScreen
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

object Routes {
    const val LANDING         = "landing"
    const val LOGIN           = "login"
    const val REGISTER        = "register"
    const val CHANGE_PASSWORD = "change_password"
    const val ADD_CHILD       = "add_child"
    const val HOME            = "home"
    const val CHILD_PROFILE = "child_profile"

}

@Composable
fun BabyBloomNavGraph(
    sessionManager: SessionManager,
    navController : NavHostController = rememberNavController()
) {
    var isReady    by remember { mutableStateOf(false) }
    var startRoute by remember { mutableStateOf(Routes.LANDING) }

    LaunchedEffect(Unit) {
        val (hasSeenLanding, isLoggedIn) = sessionManager.hasSeenLanding
            .combine(sessionManager.isLoggedIn) { seen, loggedIn -> seen to loggedIn }
            .first()

        startRoute = when {
            isLoggedIn     -> Routes.HOME
            hasSeenLanding -> Routes.LOGIN
            else           -> Routes.LANDING
        }
        isReady = true
    }

    if (!isReady) return   // pink windowBackground shows while DataStore loads

    NavHost(
        navController    = navController,
        startDestination = startRoute
    ) {

        // ── LANDING ────────────────────────────────────────────────────────
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
                    navController.navigate(Routes.CHANGE_PASSWORD)
                }
            )
        }

        // ── REGISTER ───────────────────────────────────────────────────────
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

        // ── CHANGE PASSWORD ────────────────────────────────────────────────
        composable(Routes.CHANGE_PASSWORD) {
            ChangePasswordScreen(
                onSaveClick = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.CHANGE_PASSWORD) { inclusive = true }
                    }
                },
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }

        // ── ADD CHILD ──────────────────────────────────────────────────────
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
        composable(Routes.HOME) { backStackEntry ->
            val startTab by backStackEntry.savedStateHandle
                .getStateFlow("startTab", 0)
                .collectAsState()

            ParentShell(
                startTab = startTab,
                onNavigateToLogin = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToChangePwd = {
                    navController.navigate(Routes.CHANGE_PASSWORD)
                },
                onNavigateToAddChild = {
                    navController.navigate(Routes.ADD_CHILD)
                },
                onNavigateToChildProfile = { childId ->
                    navController.navigate("${Routes.CHILD_PROFILE}/$childId")
                }
            )
        }

        composable(
            route = "${Routes.CHILD_PROFILE}/{childId}",
            arguments = listOf(navArgument("childId") { type = NavType.LongType })
        ) {
            ChildProfileScreen(
                onNavigateToHome = {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("startTab", 1)
                    navController.popBackStack()
                }
            )
        }
    }
}