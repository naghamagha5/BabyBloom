package com.babybloom.presentation.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PaintingStyle.Companion.Stroke
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.babybloom.R
import com.babybloom.domain.model.Child
import com.babybloom.presentation.viewmodels.ChildProfileViewModel
import com.babybloom.ui.theme.*

// ─── Tab enum ────────────────────────────────────────────────────────────────
enum class ChildProfileTab { ANALYTICS, AI_INSIGHTS, SETTINGS }

// ─── Screen ──────────────────────────────────────────────────────────────────

@Composable
fun ChildProfileScreen(
    onNavigateToHome: () -> Unit = {},
    viewModel: ChildProfileViewModel = hiltViewModel()

) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf(ChildProfileTab.ANALYTICS) }

    LaunchedEffect(uiState.navigateToHome) {
        if (uiState.navigateToHome) {
            viewModel.onNavigationHandled()
            onNavigateToHome()
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
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
                    child           = uiState.child,
                    sessionCount    = uiState.sessionCount,
                    progressPercent = uiState.progressPercent,
                    onBackClick     = onNavigateToHome
                )
                when (selectedTab) {
                    ChildProfileTab.ANALYTICS -> ChildAnalyticsTab(
                        childProfile     = uiState.childProfile,
                        recentActivities = uiState.recentActivities,
                        weeklyChartData  = uiState.weeklyChartData
                    )
                    ChildProfileTab.AI_INSIGHTS -> ChildAiInsightsTab(
                        parsedInsight = uiState.parsedInsight,
                        isLoading     = uiState.isLoadingInsight,
                        onRefresh     = viewModel::onRefreshInsight
                    )
                    ChildProfileTab.SETTINGS    -> ChildSettingsTab(
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
    child: Child?,
    sessionCount: Int,
    progressPercent: Int,
    onBackClick: () -> Unit
) {
    val buttonGradient = Brush.horizontalGradient(
        colors = listOf(GradientPurpleMedium, GradientPurpleDark)
    )

    val colorAvatarBorder = Brush.linearGradient(
        colors = listOf(Gradient3, Gradient2, Gradient1)
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
                val context = LocalContext.current
                Column(
                    modifier            = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

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
                                    .offset(x = (4).dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
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
                            Box(
                                modifier         = Modifier
                                    .size(82.dp)
                                    .background(White, CircleShape)
                                    .clip(CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (child?.avatar.isNullOrBlank()) {
                                    Icon(
                                        painter            = painterResource(R.drawable.ic_child_avatar_default),
                                        contentDescription = null,
                                        modifier           = Modifier.size(54.dp),
                                        tint               = NavyDark.copy(alpha = 0.5f)
                                    )
                                } else {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(
                                                android.net.Uri.parse(
                                                    "android.resource://${context.packageName}/drawable/${child!!.avatar}"
                                                )
                                            )
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = child.name,
                                        contentScale       = ContentScale.Crop,
                                        modifier           = Modifier
                                            .size(82.dp)
                                            .clip(CircleShape),
                                        error       = painterResource(R.drawable.ic_child_avatar_default),
                                        placeholder = painterResource(R.drawable.ic_child_avatar_default)
                                    )
                                }
                            }
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
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        StatCard(
                            label    = stringResource(R.string.stat_status_label),
                            value    = if (child?.isActive != false)
                                stringResource(R.string.stat_status_active)
                            else
                                stringResource(R.string.stat_status_inactive),
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
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
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
    selectedTab: ChildProfileTab,
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
                    // Mirrors the bar-chart icon for RTL direction
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