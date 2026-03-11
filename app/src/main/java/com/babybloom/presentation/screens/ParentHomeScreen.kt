package com.babybloom.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babybloom.R
import com.babybloom.presentation.viewmodels.AppNotification
import com.babybloom.presentation.viewmodels.ParentHomeUiState
import com.babybloom.presentation.viewmodels.ParentHomeViewModel
import com.babybloom.ui.theme.*

// ─────────────────────────────────────────────────────────────────────────────
// SHELL
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ParentShell(
    startTab                 : Int = 0,
    onNavigateToLogin        : () -> Unit = {},
    onNavigateToChangePwd    : () -> Unit = {},
    onNavigateToAddChild     : () -> Unit = {},
    onNavigateToChildProfile : (Long) -> Unit = {}
) {
    var selectedTab by rememberSaveable { mutableStateOf(startTab) }

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
            1 -> MyChildrenContent(
                onAddChildClick = onNavigateToAddChild,
                onChildClick    = onNavigateToChildProfile
            )
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
// BOTTOM NAV
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
            .shadow(elevation = 12.dp, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), ambientColor = ProgressPurple.copy(alpha = 0.06f))
            .background(color = Color.White, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
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
private fun ParentNavTab(iconRes: Int, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .then(if (selected) Modifier.background(ProgressPurple.copy(alpha = 0.15f)) else Modifier)
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

// ─────────────────────────────────────────────────────────────────────────────
// MAIN SCREEN
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ParentHomeScreen(
    viewModel  : ParentHomeViewModel = hiltViewModel(),
    onNavigate : (String) -> Unit = {}
) {
    val uiState       by viewModel.uiState.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val hasUnread     by viewModel.hasUnreadNotifications.collectAsStateWithLifecycle()

    val configuration = LocalConfiguration.current
    val screenWidth   = configuration.screenWidthDp.dp
    val screenHeight  = configuration.screenHeightDp.dp

    var showNotifications by remember { mutableStateOf(false) }

    // Outer Box so the notification panel can overlay the screen content
    Box(modifier = Modifier.fillMaxSize()) {

        Scaffold(
            contentWindowInsets = WindowInsets(0),
            containerColor      = BackgroundLight
        ) { paddingValues ->

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .background(BackgroundLight)
                ) {
                    ParentHeader(
                        greeting            = uiState.greeting,
                        userName            = uiState.parentName,
                        hasNotifications    = hasUnread,
                        onNotificationClick = { showNotifications = true },
                        screenWidth         = screenWidth,
                        screenHeight        = screenHeight
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                            .padding(top = 16.dp, bottom = 16.dp)
                    ) {
                        Section1Container(uiState = uiState, viewModel = viewModel)
                        Spacer(modifier = Modifier.height(24.dp))
                        Section2Container(uiState = uiState, onNavigate = onNavigate)
                        Spacer(modifier = Modifier.height(100.dp))
                    }
                }
            }
        }

        // ── Dim backdrop — tapping it dismisses the panel ─────────────────
        AnimatedVisibility(
            visible = showNotifications,
            enter   = fadeIn(),
            exit    = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication        = null
                    ) { showNotifications = false }
                    .zIndex(3f)
            )
        }

        // ── Notification panel — slides up from just below the header ─────
        AnimatedVisibility(
            visible = showNotifications,
            enter   = slideInVertically { -it } + fadeIn(),
            exit    = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 120.dp)           // clears the header
                .padding(horizontal = 12.dp)
                .zIndex(4f)
        ) {
            NotificationsPanel(
                notifications = notifications,
                onMarkAllRead = {
                    viewModel.markAllNotificationsRead()
                    showNotifications = false
                },
                onDismiss = { showNotifications = false }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// NOTIFICATIONS PANEL  — compact dropdown card, NOT full screen
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun NotificationsPanel(
    notifications : List<AppNotification>,
    onMarkAllRead : () -> Unit,
    onDismiss     : () -> Unit
) {
    val unreadCount = notifications.count { !it.isRead }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 16.dp, shape = RoundedCornerShape(20.dp), ambientColor = ShadowColor.copy(alpha = 0.12f))
                .clip(RoundedCornerShape(20.dp))
                .background(BackgroundLight)
        ) {
            // ── Panel header ──────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(GradientPurpleDark, GradientPurpleMedium)
                        ),
                        shape = RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 1.dp)
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        // X — close
                        Box(
                            modifier = Modifier
                                .size(35.dp)
                                .background(DarkNavy.copy(alpha = 0.20f), CircleShape)
                                .clickable { onDismiss() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter            = painterResource(id = R.drawable.ic_exit),
                                contentDescription = stringResource(R.string.cd_close_notifications),
                                tint               = DarkNavy,
                                modifier           = Modifier.size(20.dp)
                            )
                        }
                        // Title + unread badge
                        Column(
                            modifier            = Modifier.weight(1f).padding(top = 10.dp),
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(-5.dp)
                        ) {
                            Text(
                                text      = stringResource(R.string.label_notifications_screen_title),
                                fontSize  = 25.sp,
                                fontWeight = FontWeight.Bold,
                                color     = DarkNavy,
                                textAlign = TextAlign.End   // ← add this
                            )
                            if (unreadCount > 0) {
                                Text(
                                    text     = if (unreadCount == 2)
                                        stringResource(R.string.label_notifications_new_count_two)
                                    else
                                        stringResource(R.string.label_notifications_new_count, unreadCount),
                                    fontSize = 16.sp,
                                    color    = DarkNavy.copy(alpha = 0.75f),
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        // Bell icon
                        Box(
                            modifier = Modifier
                                .size(35.dp)
                                .background(DarkNavy.copy(alpha = 0.20f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter            = painterResource(id = R.drawable.ic_bell),
                                contentDescription = null,
                                tint               = DarkNavy,
                                modifier           = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // ── Notification cards — fixed height so panel stays compact ──
            LazyColumn(
                modifier            = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding      = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
            ) {
                items(notifications, key = { it.id }) { notif ->
                    NotifCard(notification = notif)
                }
            }

            // ── Mark all read ─────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp, top = 4.dp)
                    .clip(RoundedCornerShape(50.dp))
                    .background(ProgressPurple)
                    .clickable { onMarkAllRead() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text       = stringResource(R.string.btn_mark_all_read),
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color      = White
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// NOTIFICATION CARD  (compact, no change to logic)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun NotifCard(notification: AppNotification) {
    val (borderColor, dotColor) = when (notification.id % 3) {
        1    -> NotifAccentGreen  to NotifUnreadDotGreen
        2    -> NotifAccentPurple to NotifUnreadDotPink
        else -> NotifAccentOrange to NotifUnreadDotOrange
    }
    val showAccent = !notification.isRead

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 2.dp, shape = RoundedCornerShape(12.dp), ambientColor = ShadowColor.copy(alpha = 0.05f))
            .clip(RoundedCornerShape(12.dp))
            .background(NotifCardBg,shape = RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp))
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)
            ) {
                // Title + unread dot
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text       = notification.title,
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color      = NavyDark,
                        textAlign  = TextAlign.End,
                        modifier   = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = if (showAccent) dotColor else Color.Transparent,
                                shape = CircleShape
                            )
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                // Message
                Text(
                    text      = notification.message,
                    fontSize  = 11.sp,
                    color     = TextSecondary,
                    textAlign = TextAlign.End,
                    modifier  = Modifier.fillMaxWidth(),
                    maxLines  = 2
                )
                Spacer(modifier = Modifier.height(6.dp))
                // Timestamp
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(text = notification.time, fontSize = 10.sp, color = NotifTimestampColor)
                    Spacer(modifier = Modifier.width(3.dp))
                    Icon(
                        painter            = painterResource(id = R.drawable.ic_clock),
                        contentDescription = null,
                        tint               = NotifTimestampColor,
                        modifier           = Modifier.size(11.dp)
                    )
                }
            }
            // Colored left border strip
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(
                        color = if (showAccent) borderColor else Color.Transparent,
                        shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
                    )
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HEADER  (your exact original — zero changes)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ParentHeader(
    greeting            : String,
    userName            : String,
    hasNotifications    : Boolean,
    onNotificationClick : () -> Unit,
    modifier            : Modifier = Modifier,
    screenWidth         : Dp = 0.dp,
    screenHeight        : Dp = 0.dp
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(130.dp)
                .shadow(elevation = 8.dp, shape = RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp))
                .background(color = BackgroundLight, shape = RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp))
        ) {
            Image(
                painter            = painterResource(id = R.drawable.ic_leaf_corner_tl),
                contentDescription = null,
                contentScale       = ContentScale.Fit,
                modifier           = Modifier
                    .size(screenWidth * 0.22f)
                    .align(Alignment.TopEnd)
                    .offset(x = (20).dp, y = 42.dp)
                    .rotate(-10f)
                    .graphicsLayer { scaleX = -2.0f; scaleY = 2.0f }
            )

            Box(
                modifier = Modifier
                    .padding(start = 22.dp, top = 30.dp)
                    .size(42.dp)
                    .align(Alignment.TopStart)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(color = DarkNavy.copy(alpha = 0.15f), shape = CircleShape)
                        .clickable { onNotificationClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter            = painterResource(id = R.drawable.ic_bell),
                        contentDescription = stringResource(R.string.label_notifications),
                        tint               = NavyDark,
                        modifier           = Modifier.size(24.dp)
                    )
                }
                if (hasNotifications) {
                    Box(
                        modifier = Modifier
                            .size(13.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = 2.dp, y = (-2).dp)
                            .background(color = DotNotification, shape = CircleShape)
                            .border(width = 1.5.dp, color = White, shape = CircleShape)
                    )
                }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(start = 72.dp, end = 80.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(text = greeting,  fontSize = 28.sp, fontWeight = FontWeight.Normal, color = TextPrimary,  textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                Text(text = userName,  fontSize = 30.sp, fontWeight = FontWeight.Bold,   color = NavyDark,     textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION 1  (your exact original — zero changes)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun Section1Container(
    uiState   : ParentHomeUiState,
    viewModel : ParentHomeViewModel
) {

    val children       by viewModel.children.collectAsStateWithLifecycle()
    val selectedChild  by viewModel.selectedChild.collectAsStateWithLifecycle()
    var dropdownOpen   by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(SectionCardBackground)
            .padding(16.dp)
    ) {
        Text(text = stringResource(R.string.parent_home_cards_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = HeaderTitleColor, modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp))

        Row(modifier = Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OverviewCard(title = stringResource(R.string.parent_home_card_1_title), subtitle = stringResource(R.string.parent_home_card_1_subtitle), backgroundColor = Card1, count = uiState.totalChildren,          iconRes = R.drawable.ic_total_children, modifier = Modifier.weight(1f))
            OverviewCard(title = stringResource(R.string.parent_home_card_2_title), subtitle = stringResource(R.string.parent_home_card_2_subtitle), backgroundColor = Card2, count = uiState.activeChildrenToday,    iconRes = R.drawable.ic_active,         modifier = Modifier.weight(1f))
            OverviewCard(title = stringResource(R.string.parent_home_card_3_title), subtitle = stringResource(R.string.parent_home_card_3_subtitle), backgroundColor = Card3, count = uiState.childrenNeedingSupport, iconRes = R.drawable.ic_care,           modifier = Modifier.weight(1f))
        }

        Text(text = stringResource(R.string.parent_home_stats_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = HeaderTitleColor, modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp))

        Row(modifier = Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(number = stringResource(R.string.parent_home_stat_1_number), label = stringResource(R.string.parent_home_stat_1_label), backgroundColor = Card2, accentColor = ParentCardBlue,   iconRes = R.drawable.ic_progress,     modifier = Modifier.weight(1f))
            StatCard(number = stringResource(R.string.parent_home_stat_2_number), label = stringResource(R.string.parent_home_stat_2_label), backgroundColor = Card3, accentColor = ParentCardOrange, iconRes = R.drawable.ic_achievements, modifier = Modifier.weight(1f))
            StatCard(number = stringResource(R.string.parent_home_stat_3_number), label = stringResource(R.string.parent_home_stat_3_label), backgroundColor = Card1, accentColor = ParentCardPurple, iconRes = R.drawable.ic_timer,        modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(8.dp))

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(30.dp))
                    .background(Color.White)
                    .padding(15.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {

                    // ── Row: brain icon + title + dropdown circle ─────────────
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.Top
                    ) {
                        // Brain icon
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkPurple),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter            = painterResource(id = R.drawable.ic_brain_ai),
                                contentDescription = null,
                                modifier           = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Title
                        Text(
                            text       = stringResource(R.string.parent_home_featured_title),
                            fontSize   = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color      = NavyDark,
                            textAlign  = TextAlign.Start,
                            modifier   = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Dropdown circle — tapping opens child picker
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .offset(x = 50.dp, y = -50.dp)
                                .background(color = DarkPurple, shape = CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication        = null
                                ) { dropdownOpen = !dropdownOpen },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter            = painterResource(id = R.drawable.ic_dropdown),
                                contentDescription = null,
                                tint               = White,
                                modifier           = Modifier
                                    .size(22.dp)
                                    .offset(x = (-10).dp, y = (20).dp)
                                    .rotate(if (dropdownOpen) 180f else 0f)
                            )
                        }
                    }

                    // ── AI insight message ────────────────────────────────────
                    Text(
                        text       = if (selectedChild != null)
                            stringResource(R.string.parent_home_ai_insight_for_child, selectedChild!!.name)
                        else
                            uiState.aiInsightMessage,
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color      = NavyDark.copy(alpha = 0.9f),
                        modifier   = Modifier
                            .fillMaxWidth()
                            .offset(y = (-30).dp)
                    )

                    // ── Child dropdown list ───────────────────────────────────
                    AnimatedVisibility(visible = dropdownOpen) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset(y = (-24).dp)
                        ) {
                            // "Select child" hint row
                            Text(
                                text      = stringResource(R.string.parent_home_ai_select_child),
                                fontSize  = 12.sp,
                                color     = TextSecondary,
                                modifier  = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp),
                                textAlign = TextAlign.Start
                            )
                            children.forEach { child ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (selectedChild?.id == child.id)
                                                ProgressPurple.copy(alpha = 0.12f)
                                            else
                                                Color.Transparent
                                        )
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication        = null
                                        ) {
                                            viewModel.selectChild(child)
                                            dropdownOpen = false
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment     = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Text(
                                        text      = child.name,
                                        fontSize  = 14.sp,
                                        fontWeight = if (selectedChild?.id == child.id)
                                            FontWeight.Bold
                                        else
                                            FontWeight.Normal,
                                        color     = if (selectedChild?.id == child.id)
                                            DarkPurple
                                        else
                                            NavyDark
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    // Small colored dot per child
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(
                                                color = if (selectedChild?.id == child.id)
                                                    DarkPurple
                                                else
                                                    TextSecondary.copy(alpha = 0.4f),
                                                shape = CircleShape
                                            )
                                    )
                                }
                                Divider(color = BorderGray.copy(alpha = 0.4f), thickness = 0.5.dp)
                            }
                        }
                    }

                    // ── "View more" link ──────────────────────────────────────
                    Text(
                        text       = stringResource(R.string.parent_home_featured_more),
                        fontSize   = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = Purple,
                        modifier   = Modifier
                            .fillMaxWidth()
                            .offset(y = (-24).dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// OVERVIEW CARD  (your exact original)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun OverviewCard(title: String, subtitle: String, backgroundColor: Color, count: Int, iconRes: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier.clip(RoundedCornerShape(16.dp)).background(backgroundColor).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(White.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
            Image(painter = painterResource(id = iconRes), contentDescription = title, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = count.toString(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = NavyDark)
        Spacer(modifier = Modifier.height(5.dp))
        Text(text = title, fontSize = 11.sp, color = TextSecondary, maxLines = 2, textAlign = TextAlign.Center)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// STAT CARD  (your exact original)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun StatCard(number: String, label: String, backgroundColor: Color, accentColor: Color, iconRes: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier.clip(RoundedCornerShape(16.dp)).background(backgroundColor).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(White.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
            Image(painter = painterResource(id = iconRes), contentDescription = label, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = number.toString(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = NavyDark)
        Spacer(modifier = Modifier.height(5.dp))
        Text(text = label, fontSize = 11.sp, color = TextSecondary, maxLines = 2, textAlign = TextAlign.Center)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION 2  (your exact original)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun Section2Container(uiState: ParentHomeUiState, onNavigate: (String) -> Unit = {}) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(SectionCardBackground).padding(16.dp)) {
        Text(text = stringResource(R.string.parent_home_sections_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = HeaderTitleColor, modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp))
        QuickActionItem(title = stringResource(R.string.parent_home_section_1_title), description = stringResource(R.string.parent_home_section_1_desc), backgroundColor = Card2, iconRes = R.drawable.ic_search,  onNavigate = { onNavigate("children")  })
        Spacer(modifier = Modifier.height(12.dp))
        QuickActionItem(title = stringResource(R.string.parent_home_section_2_title), description = stringResource(R.string.parent_home_section_2_desc), backgroundColor = Card3,   iconRes = R.drawable.ic_add,     onNavigate = { onNavigate("add_child") })
        Spacer(modifier = Modifier.height(12.dp))
        QuickActionItem(title = stringResource(R.string.parent_home_section_3_title), description = stringResource(R.string.parent_home_section_3_desc), backgroundColor = Card1, iconRes = R.drawable.ic_support, onNavigate = { onNavigate("settings")  })
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// QUICK ACTION ITEM  (your exact original)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun QuickActionItem(title: String, description: String, backgroundColor: Color, iconRes: Int, modifier: Modifier = Modifier, onNavigate: () -> Unit = {}) {
    Row(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(White).clickable { onNavigate() }.padding(12.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(backgroundColor), contentAlignment = Alignment.Center) {
            Image(painter = painterResource(id = iconRes), contentDescription = title, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
            Text(text = title,       fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = HeaderTitleColor)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = description, fontSize = 12.sp, color = HeaderGreetingColor)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Image(painter = painterResource(id = R.drawable.baseline_chevron_left_24), contentDescription = null, modifier = Modifier.size(24.dp), colorFilter = ColorFilter.tint(HeaderGreetingColor))
    }
}