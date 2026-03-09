package com.babybloom.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.babybloom.di.SessionManager
import com.babybloom.presentation.screens.AddChildScreen
import com.babybloom.presentation.screens.ChangePasswordScreen
import com.babybloom.presentation.screens.LandingScreen
import com.babybloom.presentation.screens.LoginScreen
import com.babybloom.presentation.screens.MyChildrenContent
import com.babybloom.presentation.screens.ParentHomeScreen
import com.babybloom.presentation.screens.ParentView
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
    const val PARENT_VIEW     = "parent_view"
    const val MY_CHILDREN     = "my_children"
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
        composable(Routes.HOME) {
            ParentHomeScreen(
                onNavigate = { route ->
                    when (route) {
                        "children" -> navController.navigate(Routes.MY_CHILDREN)
                        "settings" -> navController.navigate(Routes.PARENT_VIEW)
                        "add_child" -> navController.navigate(Routes.ADD_CHILD)
                        else -> {}
                    }
                }
            )
        }

        // ── PARENT VIEW ────────────────────────────────────────────────────
        composable(Routes.PARENT_VIEW) {
            ParentView(
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
                }
            )
        }

        // ── MY CHILDREN ────────────────────────────────────────────────────
        composable(Routes.MY_CHILDREN) {
            MyChildrenContent(
                onAddChildClick = {
                    navController.navigate(Routes.ADD_CHILD)
                }
            )
        }
    }
}