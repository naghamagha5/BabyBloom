package com.babybloom.navigation

import android.net.Uri

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
import com.babybloom.presentation.screens.ActivityShellScreen
import com.babybloom.presentation.screens.AddChildScreen
import com.babybloom.presentation.screens.AssessmentScreen
import com.babybloom.presentation.screens.ChangePasswordScreen
import com.babybloom.presentation.screens.ChildProfileScreen
import com.babybloom.presentation.screens.ChildProfileTab
import com.babybloom.presentation.screens.LandingScreen
import com.babybloom.presentation.screens.LoginScreen
import com.babybloom.presentation.screens.ParentShell
import com.babybloom.presentation.screens.RegisterScreen
import com.babybloom.presentation.screens.WelcomeLearningScreen
import com.babybloom.util.SessionQueueCodec
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

object Routes {
    const val LANDING            = "landing"
    const val LOGIN              = "login"
    const val REGISTER           = "register"
    const val CHANGE_PASSWORD    = "change_password"
    const val ADD_CHILD          = "add_child"
    const val HOME               = "home"
    const val CHILD_PROFILE      = "child_profile"
    const val WELCOME_LEARNING   = "welcome_learning"   // ← new
    const val ASSESSMENT         = "assessment"
    const val ACTIVITY_SHELL     = "activity_shell"
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

    if (!isReady) return

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
                onBackClick = { navController.popBackStack() }
            )
        }

        // ── ADD CHILD ──────────────────────────────────────────────────────
        composable(Routes.ADD_CHILD) {
            AddChildScreen(
                onSaveChild = { childId ->
                    navController.navigate("${Routes.CHILD_PROFILE}/$childId") {
                        popUpTo(Routes.ADD_CHILD) { inclusive = true }
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

        // ── CHILD PROFILE ──────────────────────────────────────────────────
        composable(
            route     = "${Routes.CHILD_PROFILE}/{childId}",
            arguments = listOf(navArgument("childId") { type = NavType.LongType })
        ) { backStackEntry ->
            val startTab by backStackEntry.savedStateHandle
                .getStateFlow("startTab", 0)
                .collectAsState()

            ChildProfileScreen(
                initialTab = when (startTab) {
                    0    -> ChildProfileTab.ANALYTICS
                    1    -> ChildProfileTab.AI_INSIGHTS
                    2    -> ChildProfileTab.SETTINGS
                    else -> ChildProfileTab.ANALYTICS
                },
                onNavigateToHome = {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("startTab", 1)
                    navController.popBackStack()
                },
                onNavigateToWelcomeLearning = { childId ->
                    navController.navigate("${Routes.WELCOME_LEARNING}/$childId")
                },
                onNavigateToAssessment = { childId ->
                    navController.navigate("${Routes.ASSESSMENT}/$childId")
                }
            )
        }

        composable(
            route = "${Routes.ASSESSMENT}/{childId}",
            arguments = listOf(navArgument("childId") { type = NavType.LongType })
        ) { backStackEntry ->
            val childId = backStackEntry.arguments?.getLong("childId") ?: 0L

            AssessmentScreen(
                childId = childId,
                onAssessmentComplete = {
                    navController.navigate("${Routes.CHILD_PROFILE}/$childId") {
                        popUpTo("${Routes.ASSESSMENT}/$childId") { inclusive = true }
                    }
                },
                onExitAssessment = {
                    navController.popBackStack(
                        route = "${Routes.CHILD_PROFILE}/$childId",
                        inclusive = false
                    )
                }
            )
        }

        composable(
            route = "${Routes.WELCOME_LEARNING}/{childId}",
            arguments = listOf(navArgument("childId") { type = NavType.LongType })
        ) { backStackEntry ->
            val childId = backStackEntry.arguments?.getLong("childId") ?: 0L
            WelcomeLearningScreen(
                onNavigateToActivity = { activityId, sessionId, cId, contentId, encodedQueue, stepIndex ->
                    navController.navigate(
                        buildString {
                            append("${Routes.ACTIVITY_SHELL}/$activityId/$sessionId/$cId")
                            append("?contentId=${Uri.encode(contentId.orEmpty())}")
                            append("&queue=${Uri.encode(encodedQueue)}")
                            append("&stepIndex=$stepIndex")
                        }
                    )
                }
            )
        }

        // ── ACTIVITY SHELL ─────────────────────────────────────────────────────
        composable(
            route = "${Routes.ACTIVITY_SHELL}/{activityId}/{sessionId}/{childId}?contentId={contentId}&queue={queue}&stepIndex={stepIndex}",
            arguments = listOf(
                navArgument("activityId") { type = NavType.StringType },
                navArgument("sessionId")  { type = NavType.LongType },
                navArgument("childId")    { type = NavType.LongType },
                navArgument("contentId")  { type = NavType.StringType; defaultValue = "" },
                navArgument("queue")      { type = NavType.StringType; defaultValue = "" },
                navArgument("stepIndex")  { type = NavType.IntType; defaultValue = 0 }
            )
        ) { backStackEntry ->
            val activityId = backStackEntry.arguments?.getString("activityId") ?: ""
            val sessionId  = backStackEntry.arguments?.getLong("sessionId")    ?: 0L
            val childId    = backStackEntry.arguments?.getLong("childId")      ?: 0L
            val contentId  = backStackEntry.arguments?.getString("contentId").orEmpty().ifBlank { null }
            val queueArg   = backStackEntry.arguments?.getString("queue")
            val stepIndex  = backStackEntry.arguments?.getInt("stepIndex") ?: 0
            val queue      = SessionQueueCodec.decode(queueArg)

            ActivityShellScreen(
                activityId         = activityId,
                sessionId          = sessionId,
                childId            = childId,
                contentId          = contentId,
                queue              = queue,
                stepIndex          = stepIndex,
                onActivityComplete = { _, _, effectiveSessionId, decision ->
                    when (decision) {
                        is com.babybloom.domain.model.SessionDecision.Repeat -> {
                            navController.navigate(
                                buildString {
                                    append("${Routes.ACTIVITY_SHELL}/${decision.activityId}/$effectiveSessionId/$childId")
                                    append("?contentId=${Uri.encode(decision.contentId.orEmpty())}")
                                    append("&queue=${Uri.encode(queueArg.orEmpty())}")
                                    append("&stepIndex=$stepIndex")
                                }
                            ) {
                                popUpTo(backStackEntry.destination.id) { inclusive = true }
                            }
                        }
                        is com.babybloom.domain.model.SessionDecision.Next -> {
                            navController.navigate(
                                buildString {
                                    append("${Routes.ACTIVITY_SHELL}/${decision.activityId}/$effectiveSessionId/$childId")
                                    append("?contentId=${Uri.encode(decision.contentId.orEmpty())}")
                                    append("&queue=${Uri.encode(queueArg.orEmpty())}")
                                    append("&stepIndex=${stepIndex + 1}")
                                }
                            ) {
                                popUpTo(backStackEntry.destination.id) { inclusive = true }
                            }
                        }
                        else -> {
                            navController.popBackStack(
                                route     = "${Routes.CHILD_PROFILE}/$childId",
                                inclusive = false
                            )
                            navController.currentBackStackEntry
                                ?.savedStateHandle
                                ?.set("startTab", 0)
                        }
                    }
                },
                onExit = {
                    navController.popBackStack(
                        route     = "${Routes.CHILD_PROFILE}/$childId",
                        inclusive = false
                    )
                    navController.currentBackStackEntry
                        ?.savedStateHandle
                        ?.set("startTab", 0)
                }
            )
        }
    }
}
