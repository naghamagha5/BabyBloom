package com.babybloom.presentation.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babybloom.R
import com.babybloom.data.local.dao.ChildDao
import com.babybloom.data.local.entity.ChildEntity
import com.babybloom.di.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
// SHARED MODEL  — used by both ParentHomeViewModel and ParentViewModel
// ─────────────────────────────────────────────────────────────────────────────
data class AppNotification(
    val id      : Int,
    val title   : String,
    val message : String,
    val time    : String,
    val isRead  : Boolean = false
)

// ─────────────────────────────────────────────────────────────────────────────
// UI STATE
// ─────────────────────────────────────────────────────────────────────────────
data class ParentHomeUiState(
    val isLoading                : Boolean = false,
    val parentName               : String  = "",
    val greeting                 : String  = "",
    val totalChildren            : Int     = 0,
    val activeChildrenToday      : Int     = 0,
    val childrenNeedingSupport   : Int     = 0,
    val weeklyAchievementsCount  : Int     = 0,
    val totalPointsThisWeek      : Int     = 0,
    val weeklyProgressPercentage : Double  = 0.0,
    val aiInsightMessage         : String  = "",
    val hasUnreadNotifications   : Boolean = false,
    val error                    : String? = null
)

// ─────────────────────────────────────────────────────────────────────────────
// VIEW MODEL
// ─────────────────────────────────────────────────────────────────────────────
@HiltViewModel
class ParentHomeViewModel @Inject constructor(
    private val sessionManager : SessionManager,
    private val childDao       : ChildDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // ── Main UI state ─────────────────────────────────────────────────────
    private val _uiState = MutableStateFlow(ParentHomeUiState(isLoading = true))
    val uiState: StateFlow<ParentHomeUiState> = _uiState.asStateFlow()

    // ── Notifications — seeded from string resources, no hardcoded Arabic ─
    private val _notifications = MutableStateFlow(
        listOf(
            AppNotification(
                id      = 1,
                title   = context.getString(R.string.notif_title_activity_completed, "فاطمة", "الرياضيات"),
                message = context.getString(R.string.notif_body_activity_completed, "فاطمة يسري", "عدّ النجوم", 95),
                time    = context.getString(R.string.label_notification_minutes_ago, 5),
                isRead  = false
            ),
            AppNotification(
                id      = 2,
                title   = context.getString(R.string.notif_title_ai_insight),
                message = context.getString(R.string.notif_body_ai_insight),
                time    = context.getString(R.string.label_notification_hour_ago),
                isRead  = false
            ),
            AppNotification(
                id      = 3,
                title   = context.getString(R.string.notif_title_needs_support, "أنس"),
                message = context.getString(R.string.notif_body_needs_support, "أنس"),
                time    = context.getString(R.string.label_notification_hours_ago, 3),
                isRead  = true
            )
        )
    )
    val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()

    val hasUnreadNotifications: StateFlow<Boolean> = _notifications
        .map { list -> list.any { !it.isRead } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // ── Children stream from DB ───────────────────────────────────────────
    private val childrenFlow = sessionManager.userId
        .flatMapLatest { uid ->
            if (uid == -1L) flowOf(emptyList())
            else childDao.getChildrenByUser(uid)
        }

    val children: StateFlow<List<ChildEntity>> = childrenFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedChild = MutableStateFlow<ChildEntity?>(null)
    val selectedChild: StateFlow<ChildEntity?> = _selectedChild.asStateFlow()

    fun selectChild(child: ChildEntity) {
        _selectedChild.value = child
    }

    fun markAllNotificationsRead() {
        _notifications.value = _notifications.value.map { it.copy(isRead = true) }
    }

    fun markNotificationRead(id: Int) {
        _notifications.value = _notifications.value.map {
            if (it.id == id) it.copy(isRead = true) else it
        }
    }

    init { loadHomeScreenData() }

    fun loadHomeScreenData() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)

                val name     = sessionManager.userName.first().ifBlank { "" }
                val greeting = getGreeting()
                val children = childrenFlow.first()

                val total        = children.size
                val needsSupport = children.count { it.notes.isNotEmpty() }
                val active       = (total - needsSupport).coerceAtLeast(0)

                _uiState.value = ParentHomeUiState(
                    isLoading                = false,
                    parentName               = name,
                    greeting                 = greeting,
                    totalChildren            = total,
                    activeChildrenToday      = active,
                    childrenNeedingSupport   = needsSupport,
                    weeklyAchievementsCount  = 0,
                    totalPointsThisWeek      = 0,
                    weeklyProgressPercentage = 0.0,
                    aiInsightMessage         = generateAIInsight(active),
                    hasUnreadNotifications   = _notifications.value.any { !it.isRead }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error     = context.getString(R.string.error_generic_unexpected)
                )
            }
        }
    }

    fun retryLoadData() = loadHomeScreenData()

    // no-op hook — popup open/close owned by UI
    fun onNotificationClick() {}

    // ── Private helpers ───────────────────────────────────────────────────
    private fun getGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour < 12 -> context.getString(R.string.greeting_good_morning)
            hour < 17 -> context.getString(R.string.greeting_good_afternoon)
            else      -> context.getString(R.string.greeting_good_evening)
        }
    }

    private fun generateAIInsight(activeToday: Int): String =
        if (activeToday > 0) context.getString(R.string.parent_home_featured_desc)
        else                 context.getString(R.string.ai_empty_subtitle)
}
