package com.babybloom.presentation.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.babybloom.R
import com.babybloom.presentation.viewmodels.ParentHomeViewModel
import com.babybloom.ui.theme.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import com.babybloom.presentation.screens.MyChildrenContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue


// ─────────────────────────────────────────────────────────────────────────────
// SHELL — owns the bottom nav and all tabs
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ParentShell(
    onNavigateToLogin     : () -> Unit = {},
    onNavigateToChangePwd : () -> Unit = {},
    onNavigateToAddChild  : () -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(0) }  // ← lands on Home

    Box(modifier = Modifier.fillMaxSize()) {
        when (selectedTab) {
            0 -> ParentHomeScreen(
                onNavigate = { route ->
                    when (route) {
                        "children"  -> selectedTab = 1
                        "settings"  -> selectedTab = 2
                        "add_child" -> onNavigateToAddChild()
                    }
                }
            )
            1 -> MyChildrenContent(onAddChildClick = onNavigateToAddChild)
            2 -> ParentSettingsContent(
                onNavigateToLogin     = onNavigateToLogin,
                onNavigateToChangePwd = onNavigateToChangePwd,
                onNavigateToAddChild  = onNavigateToAddChild
            )
        }

        ParentBottomNav(
            selectedTab   = selectedTab,
            onTabSelected = { selectedTab = it },
            modifier      = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .zIndex(2f)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// BOTTOM NAVIGATION
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ParentBottomNav(
    selectedTab   : Int,
    onTabSelected : (Int) -> Unit,
    modifier      : Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(100.dp)
            .shadow(
                elevation    = 12.dp,
                shape        = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                ambientColor = ProgressPurple.copy(alpha = 0.06f)
            )
            .background(
                color = Color.White,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            )
    ) {
        Row(
            modifier              = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            ParentNavTab(iconRes = R.drawable.ic_home,     label = stringResource(R.string.nav_home),     selected = selectedTab == 0, onClick = { onTabSelected(0) })
            ParentNavTab(iconRes = R.drawable.ic_children, label = stringResource(R.string.nav_children), selected = selectedTab == 1, onClick = { onTabSelected(1) })
            ParentNavTab(iconRes = R.drawable.ic_profile,  label = stringResource(R.string.nav_settings), selected = selectedTab == 2, onClick = { onTabSelected(2) })
        }
    }
}

@Composable
private fun ParentNavTab(
    iconRes  : Int,
    label    : String,
    selected : Boolean,
    onClick  : () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null
            ) { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .then(
                    if (selected) Modifier.background(ProgressPurple.copy(alpha = 0.15f))
                    else Modifier
                )
                .padding(horizontal = 16.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter            = painterResource(id = iconRes),
                contentDescription = label,
                tint               = if (selected) ProgressPurple else TextSecondary,
                modifier           = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text       = label,
            fontSize   = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color      = if (selected) ProgressPurple else TextSecondary
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
// MAIN SCREEN
// ════════════════════════════════════════════════════════════════════════════
@Composable
fun ParentHomeScreen(
    viewModel: ParentHomeViewModel = viewModel(),
    onNavigate: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ScreenBackgroundLight)
        ) {
            // ════════════════════════════════════════════════════════════════════
            // FIXED HEADER
            // ════════════════════════════════════════════════════════════════════
            ParentHomeHeader()

            // ════════════════════════════════════════════════════════════════════
            // SCROLLABLE BODY — With visible scrollbar
            // ════════════════════════════════════════════════════════════════════
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp)
                        .padding(top = 16.dp, bottom = 16.dp)
                ) {
                    // ════════════════════════════════════════════════════════════
                    // SECTION 1: OVERVIEW & ACHIEVEMENTS
                    // ════════════════════════════════════════════════════════════
                    Section1Container(uiState = uiState)

                    Spacer(modifier = Modifier.height(24.dp))

                    // ════════════════════════════════════════════════════════════
                    // SECTION 2: AI INSIGHTS & QUICK ACTIONS
                    // ════════════════════════════════════════════════════════════
                    Section2Container(uiState = uiState, onNavigate = onNavigate)

                    Spacer(modifier = Modifier.height(24.dp))
                }

                // ════════════════════════════════════════════════════════════════
                // SCROLLBAR INDICATOR — Right side
                // ════════════════════════════════════════════════════════════════
                VerticalScrollbar(
                    scrollState = scrollState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(vertical = 8.dp, horizontal = 3.dp)
                )
            }

            // ════════════════════════════════════════════════════════════════════
            // FIXED BOTTOM NAVIGATION BAR
            // ════════════════════════════════════════════════════════════════════

        }
    }
}

// ════════════════════════════════════════════════════════════════════
// HEADER SECTION
// ════════════════════════════════════════════════════════════════════
@Composable
fun ParentHomeHeader(notificationCount: Int = 0) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ScreenBackgroundLight)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Greeting + Name
        Column {
            Text(
                text = stringResource(R.string.parent_home_greeting),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = HeaderGreetingColor
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.parent_home_title),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Cursive,
                color = HeaderTitleColor
            )
        }

        // Notification with badge
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(NavyDark),
            contentAlignment = Alignment.Center
        ) {
            BadgedBox(
                badge = {
                    if (notificationCount > 0) {
                        Badge {
                            Text(
                                text = notificationCount.toString(),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Notifications",
                    tint = White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// SECTION 1: OVERVIEW CARDS & ACHIEVEMENTS STATS
// ════════════════════════════════════════════════════════════════════════════
@Composable
fun Section1Container(uiState: com.babybloom.presentation.viewmodels.ParentHomeUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(
                topStart = 24.dp,
                topEnd = 24.dp,
                bottomStart = 24.dp,
                bottomEnd = 24.dp
            ))
            .background(Color(0xFFEAEFFF))
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.parent_home_cards_title),
            style = TextStyle(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = HeaderTitleColor
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OverviewCard(
                title = stringResource(R.string.parent_home_card_1_title),
                subtitle = stringResource(R.string.parent_home_card_1_subtitle),
                backgroundColor = ParentStatsBlueBG,
                iconColor = ParentCardBlue,
                count = uiState.totalChildren,
                iconRes = R.drawable.ic_nav_children,
                modifier = Modifier.weight(1f)
            )

            OverviewCard(
                title = stringResource(R.string.parent_home_card_2_title),
                subtitle = stringResource(R.string.parent_home_card_2_subtitle),
                backgroundColor = ParentStatsOrangeBG,
                iconColor = ParentCardOrange,
                count = uiState.activeChildrenToday,
                iconRes = R.drawable.ic_active_chiled,
                modifier = Modifier.weight(1f)
            )

            OverviewCard(
                title = stringResource(R.string.parent_home_card_3_title),
                subtitle = stringResource(R.string.parent_home_card_3_subtitle),
                backgroundColor = ParentStatsPurpleBG,
                iconColor = ParentCardPurple,
                count = uiState.childrenNeedingSupport,
                iconRes = R.drawable.outline_exclamation_24,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.parent_home_stats_title),
            style = TextStyle(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = HeaderTitleColor
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                number = stringResource(R.string.parent_home_stat_1_number),
                label = stringResource(R.string.parent_home_stat_1_label),
                backgroundColor = ParentStatsBlueBG,
                accentColor = ParentCardBlue,
                iconRes = R.drawable.ic_done_task,
                modifier = Modifier.weight(1f)
            )

            StatCard(
                number = stringResource(R.string.parent_home_stat_2_number),
                label = stringResource(R.string.parent_home_stat_2_label),
                backgroundColor = ParentStatsOrangeBG,
                accentColor = ParentCardOrange,
                iconRes = R.drawable.outline_timer_24,
                modifier = Modifier.weight(1f)
            )

            StatCard(
                number = stringResource(R.string.parent_home_stat_3_number),
                label = stringResource(R.string.parent_home_stat_3_label),
                backgroundColor = ParentStatsPurpleBG,
                accentColor = ParentCardPurple,
                iconRes = R.drawable.baseline_auto_graph_24,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// OVERVIEW CARD
// ════════════════════════════════════════════════════════════════════════════
@Composable
fun OverviewCard(
    title: String,
    subtitle: String,
    backgroundColor: Color,
    iconColor: Color,
    count: Int,
    iconRes: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconColor.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = title,
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(iconColor)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = count.toString(),
            style = TextStyle(
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = iconColor
            )
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = title,
            style = TextStyle(
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = HeaderGreetingColor
            ),
            maxLines = 2,
            textAlign = TextAlign.Center
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
// STAT CARD
// ════════════════════════════════════════════════════════════════════════════
@Composable
fun StatCard(
    number: String,
    label: String,
    backgroundColor: Color,
    accentColor: Color,
    iconRes: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(accentColor.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = label,
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(accentColor)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = number,
            style = TextStyle(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = label,
            style = TextStyle(
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                color = HeaderGreetingColor
            ),
            maxLines = 2,
            textAlign = TextAlign.Center
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
// SECTION 2: AI INSIGHTS & QUICK ACTIONS
// ════════════════════════════════════════════════════════════════════════════
@Composable
fun Section2Container(
    uiState: com.babybloom.presentation.viewmodels.ParentHomeUiState,
    onNavigate: (String) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(
                topStart = 24.dp,
                topEnd = 24.dp,
                bottomStart = 24.dp,
                bottomEnd = 24.dp
            ))
            .background(Color(0xFFEAEFFF))
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            AIInsightsDarkBg,
                            AIInsightsDarkBg.copy(alpha = 0.8f)
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.parent_home_featured_title),
                        style = TextStyle(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = White
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(AIInsightsIconBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "★",
                            style = TextStyle(
                                fontSize = 25.sp,
                                color = White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = uiState.aiInsightMessage,
                    style = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = White.copy(alpha = 0.9f)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.parent_home_featured_more),
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AIInsightsIconBg
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.parent_home_sections_title),
            style = TextStyle(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = HeaderTitleColor
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        )

        QuickActionItem(
            title = stringResource(R.string.parent_home_section_1_title),
            description = stringResource(R.string.parent_home_section_1_desc),
            backgroundColor = QuickActionIconPurple,
            iconRes = R.drawable.ic_search,
            onNavigate = { onNavigate("children") }
        )

        Spacer(modifier = Modifier.height(12.dp))

        QuickActionItem(
            title = stringResource(R.string.parent_home_section_2_title),
            description = stringResource(R.string.parent_home_section_2_desc),
            backgroundColor = QuickActionIconBlue,
            iconRes = R.drawable.ic_add,
            onNavigate = { onNavigate("add_child") }
        )

        Spacer(modifier = Modifier.height(12.dp))

        QuickActionItem(
            title = stringResource(R.string.parent_home_section_3_title),
            description = stringResource(R.string.parent_home_section_3_desc),
            backgroundColor = QuickActionIconOrange,
            iconRes = R.drawable.ic_support,
            onNavigate = { onNavigate("settings") }
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
// QUICK ACTION ITEM
// ════════════════════════════════════════════════════════════════════════════
@Composable
fun QuickActionItem(
    title: String,
    description: String,
    backgroundColor: Color,
    iconRes: Int,
    modifier: Modifier = Modifier,
    onNavigate: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(White)
            .padding(12.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(backgroundColor),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = title,
                modifier = Modifier.size(24.dp),
                colorFilter = ColorFilter.tint(White)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = title,
                style = TextStyle(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HeaderTitleColor
                )
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = description,
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = HeaderGreetingColor
                )
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Image(
            painter = painterResource(id = R.drawable.baseline_chevron_left_24),
            contentDescription = "Navigate",
            modifier = Modifier.size(24.dp),
            colorFilter = ColorFilter.tint(HeaderGreetingColor)
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
// CUSTOM SCROLL INDICATOR
// ════════════════════════════════════════════════════════════════════════════
@Composable
private fun VerticalScrollbar(
    scrollState: androidx.compose.foundation.ScrollState,
    modifier: Modifier = Modifier
) {
    if (scrollState.maxValue > 0) {
        val scrollProgress = scrollState.value.toFloat() / scrollState.maxValue.toFloat()

        Box(
            modifier = modifier
                .width(4.dp)
                .background(
                    color = BorderGray,
                    shape = RoundedCornerShape(2.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight(0.3f)
                    .offset(y = (scrollProgress * 450).dp)
                    .background(
                        color = NavyDark.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}