package com.babybloom.presentation.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.babybloom.R
import com.babybloom.domain.model.Child
import com.babybloom.domain.model.ChildStatus
import com.babybloom.presentation.viewmodels.ChildProfileViewModel
import com.babybloom.ui.theme.*

// ─── Tab enum ────────────────────────────────────────────────────────────────
enum class ChildProfileTab { ANALYTICS, AI_INSIGHTS, SETTINGS }

private fun resolveAvatarResId(avatar: String): Int = when (avatar) {
    "avatars/girl_1.webp", "avatar_girl_1" -> R.drawable.avatar_girl_1
    "avatars/girl_2.webp", "avatar_girl_2" -> R.drawable.avatar_girl_2
    "avatars/girl_3.webp", "avatar_girl_3" -> R.drawable.avatar_girl_3
    "avatars/girl_4.webp", "avatar_girl_4" -> R.drawable.avatar_girl_4
    "avatars/girl_5.webp", "avatar_girl_5" -> R.drawable.avatar_girl_5
    "avatars/girl_6.webp", "avatar_girl_6" -> R.drawable.avatar_girl_6
    "avatars/boy_1.webp",  "avatar_boy_1"  -> R.drawable.avatar_boy_1
    "avatars/boy_2.webp",  "avatar_boy_2"  -> R.drawable.avatar_boy_2
    "avatars/boy_3.webp",  "avatar_boy_3"  -> R.drawable.avatar_boy_3
    "avatars/boy_4.webp",  "avatar_boy_4"  -> R.drawable.avatar_boy_4
    "avatars/boy_5.webp",  "avatar_boy_5"  -> R.drawable.avatar_boy_5
    "avatars/boy_6.webp",  "avatar_boy_6"  -> R.drawable.avatar_boy_6
    else                                    -> R.drawable.ic_child_avatar_default
}

// ─── Screen ──────────────────────────────────────────────────────────────────

@Composable
fun ChildProfileScreen(
    onNavigateToHome          : () -> Unit = {},
    onNavigateToWelcomeLearning: (childId: Long) -> Unit = {},
    onNavigateToAssessment    : (childId: Long) -> Unit = {},
    initialTab                  : ChildProfileTab = ChildProfileTab.ANALYTICS,  // ← add this
    viewModel: ChildProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by rememberSaveable { mutableStateOf(initialTab)    }

    LaunchedEffect(uiState.navigateToHome) {
        if (uiState.navigateToHome) {
            viewModel.onNavigationHandled()
            onNavigateToHome()
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        @OptIn(ExperimentalMaterial3Api::class)
        Scaffold(
            containerColor      = BackgroundLight,
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                ChildProfileBottomNav(
                    selectedTab   = selectedTab,
                    onTabSelected = { selectedTab = it }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                ChildProfileHeader(
                    child                      = uiState.child,
                    sessionCount               = uiState.sessionCount,
                    progressPercent            = uiState.progressPercent,
                    assessmentCompleted        = uiState.childProfile?.assessmentCompleted == true,
                    onBackClick                = onNavigateToHome,
                    onStartLearningClick       = {
                        uiState.child?.id?.let { childId ->
                            if (uiState.childProfile?.assessmentCompleted == true) {
                                onNavigateToWelcomeLearning(childId)
                            } else {
                                onNavigateToAssessment(childId)
                            }
                        }
                    }
                )
                when (selectedTab) {
                    ChildProfileTab.ANALYTICS -> ChildAnalyticsTab(
                        childProfile     = uiState.childProfile,
                        recentActivities = uiState.recentActivities,
                        chartData        = uiState.chartData
                    )
                    ChildProfileTab.AI_INSIGHTS -> ChildAiInsightsTab(
                        parsedInsight = uiState.parsedInsight,
                        isLoading     = uiState.isLoadingInsight,
                        canGenerate   = uiState.canGenerateInsight,
                        generationMessage = uiState.insightGenerationMessage,
                        generationError = uiState.insightGenerationError,
                        onRefresh     = viewModel::onRefreshInsight
                    )
                    ChildProfileTab.SETTINGS -> ChildSettingsTab(
                        child                         = uiState.child,
                        currentSessionDurationMinutes = uiState.currentSessionDurationMinutes,
                        showDurationPicker            = uiState.showSessionDurationPicker,
                        showRemoveDialog              = uiState.showRemoveChildDialog,
                        onToggleSoundEffects          = viewModel::onToggleSoundEffects,
                        onToggleBackgroundMusic       = viewModel::onToggleBackgroundMusic,
                        onToggleUiTheme               = viewModel::onToggleUiTheme,
                        onSessionDurationClick        = viewModel::onShowSessionDurationPicker,
                        onDismissDurationPicker       = viewModel::onDismissSessionDurationPicker,
                        onConfirmDuration             = viewModel::onConfirmSessionDuration,
                        onRemoveChildClick            = viewModel::onShowRemoveChildDialog,
                        onDismissRemoveDialog         = viewModel::onDismissRemoveChildDialog,
                        onConfirmRemoveChild          = viewModel::onConfirmRemoveChild
                    )
                }
            }
        }
    }
}

// ─── Shared Header ────────────────────────────────────────────────────────────
@Composable
internal fun ChildProfileHeader(
    child                  : Child?,
    sessionCount           : Int,
    progressPercent        : Int,
    assessmentCompleted    : Boolean,
    onBackClick            : () -> Unit,
    onStartLearningClick   : () -> Unit
) {
    val buttonGradient = Brush.horizontalGradient(
        colors = listOf(GradientPurpleMedium, GradientPurpleDark)
    )

    val startLearningGradient = Brush.horizontalGradient(
        colors = listOf(GradientOrangeMedium, GradientOrangeDark)
    )

    val colorAvatarBorder = Brush.linearGradient(
        colors = listOf(Gradient3, Gradient2, Gradient1)
    )

    // Pulse animation for the CTA button
    val infiniteTransition = rememberInfiniteTransition(label = "cta_pulse")
    val buttonScale by infiniteTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = 1.03f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cta_scale"
    )

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(color = BackgroundLight)
        ) {

            Image(
                painter            = painterResource(R.drawable.ic_leaf_corner_tl),
                contentDescription = null,
                contentScale       = ContentScale.Fit,
                modifier           = Modifier
                    .width(120.dp)
                    .height(200.dp)
                    .align(Alignment.TopStart)
                    .offset(x = (-12).dp, y = (-15).dp)
                    .rotate(20f)
            )

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Column(
                    modifier            = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

                    // ── Top bar (title + back button) ─────────────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .background(
                                    brush = buttonGradient,
                                    shape = RoundedCornerShape(50.dp)
                                )
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                painter            = painterResource(R.drawable.ic_eye_on),
                                contentDescription = null,
                                tint               = White,
                                modifier           = Modifier.size(16.dp)
                            )
                            Text(
                                text  = stringResource(R.string.child_profile_title),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color      = White
                                )
                            )
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .size(44.dp)
                                .background(
                                    brush = buttonGradient,
                                    shape = CircleShape
                                )
                                .clickable(onClick = onBackClick),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter            = painterResource(R.drawable.ic_arrow_back),
                                contentDescription = stringResource(R.string.cd_back),
                                tint               = White,
                                modifier           = Modifier
                                    .size(22.dp)
                                    .offset(x = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // ── Avatar + name row ─────────────────────────────────────
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Box(
                            modifier         = Modifier.size(88.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawArc(
                                    brush      = colorAvatarBorder,
                                    startAngle = -140f,
                                    sweepAngle = 240f,
                                    useCenter  = false,
                                    style      = Stroke(width = 3.dp.toPx())
                                )
                            }
                            Image(
                                painter            = painterResource(
                                    resolveAvatarResId(child?.avatar.orEmpty())
                                ),
                                contentDescription = child?.name,
                                contentScale       = ContentScale.Crop,
                                modifier           = Modifier
                                    .size(82.dp)
                                    .clip(CircleShape)
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier            = Modifier.padding(start = 12.dp, end = 16.dp)
                        ) {
                            Text(
                                text      = child?.name
                                    ?: stringResource(R.string.placeholder_child_name),
                                style     = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color      = NavyDark
                                ),
                                textAlign = TextAlign.End
                            )
                            child?.age?.let { age ->
                                Text(
                                    text      = "$age ${stringResource(R.string.label_years)}",
                                    style     = MaterialTheme.typography.bodySmall.copy(
                                        color = TextSecondary
                                    ),
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // ── Stat cards ────────────────────────────────────────────
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        StatCard(
                            label    = stringResource(R.string.stat_status_label),
                            value = when (child?.status) {
                                ChildStatus.ACTIVE        -> stringResource(R.string.stat_status_active)
                                ChildStatus.CALM          -> stringResource(R.string.stat_status_calm)
                                ChildStatus.NEEDS_SUPPORT -> stringResource(R.string.stat_status_needs_support)
                                null                      -> stringResource(R.string.stat_status_calm)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            label    = stringResource(R.string.stat_progress),
                            value    = "$progressPercent%",
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            label    = stringResource(R.string.stat_sessions),
                            value    = sessionCount.toString(),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ── Start Learning CTA button ─────────────────────────────
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 32.dp) // minimized width by increasing horizontal padding
                            .fillMaxWidth()
                            .scale(buttonScale)
                            .clip(RoundedCornerShape(50.dp))
                            .background(brush = startLearningGradient)
                            .clickable(onClick = onStartLearningClick)
                            .padding(vertical = 10.dp), // minimized height by decreasing vertical padding
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Image(
                                painter            = painterResource(R.drawable.ic_rocket),
                                contentDescription = null,
                                modifier           = Modifier.size(35.dp)
                            )
                            Text(
                                text  = if (assessmentCompleted) "اِبْدَأِ الْمُغَامَرَةَ" else "اِبْدَأْ رِحْلَةَ الِاكْتِشَافِ",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color      = White,
                                    fontSize   = 20.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    label   : String,
    value   : String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier            = modifier
            .background(
                color = GradientPurpleLight,
                shape = RoundedCornerShape(14.dp)
            )
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text      = label,
            style     = MaterialTheme.typography.labelSmall.copy(
                color = NavyDark.copy(alpha = 0.60f)
            ),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text      = value,
            style     = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color      = NavyDark,
                fontSize   = 17.sp
            ),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ChildProfileBottomNav(
    selectedTab  : ChildProfileTab,
    onTabSelected: (ChildProfileTab) -> Unit
) {
    NavigationBar(
        containerColor = White,
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            selected = selectedTab == ChildProfileTab.SETTINGS,
            onClick  = { onTabSelected(ChildProfileTab.SETTINGS) },
            icon = {
                Icon(
                    painter = painterResource(
                        if (selectedTab == ChildProfileTab.SETTINGS)
                            R.drawable.ic_settings_filled
                        else
                            R.drawable.ic_settings_outline
                    ),
                    contentDescription = null
                )
            },
            label  = {
                Text(
                    text  = stringResource(R.string.tab_settings),
                    style = MaterialTheme.typography.labelSmall
                )
            },
            colors = navBarColors()
        )

        NavigationBarItem(
            selected = selectedTab == ChildProfileTab.AI_INSIGHTS,
            onClick  = { onTabSelected(ChildProfileTab.AI_INSIGHTS) },
            icon = {
                Icon(
                    painter = painterResource(
                        if (selectedTab == ChildProfileTab.AI_INSIGHTS)
                            R.drawable.ic_ai_insights_filled
                        else
                            R.drawable.ic_ai_insights_outline
                    ),
                    contentDescription = null
                )
            },
            label  = {
                Text(
                    text  = stringResource(R.string.tab_ai_insights),
                    style = MaterialTheme.typography.labelSmall
                )
            },
            colors = navBarColors()
        )

        NavigationBarItem(
            selected = selectedTab == ChildProfileTab.ANALYTICS,
            onClick  = { onTabSelected(ChildProfileTab.ANALYTICS) },
            icon = {
                Icon(
                    painter = painterResource(
                        if (selectedTab == ChildProfileTab.ANALYTICS)
                            R.drawable.ic_analytics_filled
                        else
                            R.drawable.ic_analytics_outline
                    ),
                    contentDescription = null,
                    modifier = Modifier.graphicsLayer { scaleX = -1f }
                )
            },
            label  = {
                Text(
                    text  = stringResource(R.string.tab_analytics),
                    style = MaterialTheme.typography.labelSmall
                )
            },
            colors = navBarColors()
        )
    }
}

@Composable
private fun navBarColors() = NavigationBarItemDefaults.colors(
    selectedIconColor   = ProgressPurple,
    selectedTextColor   = ProgressPurple,
    unselectedIconColor = TextSecondary,
    unselectedTextColor = TextSecondary,
    indicatorColor      = CardPurple
)
