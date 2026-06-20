package com.babybloom.presentation.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babybloom.R
import com.babybloom.data.local.dao.ChildDao
import com.babybloom.data.local.entity.ChildEntity
import com.babybloom.di.SessionManager
import com.babybloom.domain.model.AppNotification
import com.babybloom.domain.model.ChildStatus
import com.babybloom.domain.notifications.ParentNotificationHandler
import com.babybloom.domain.repository.AppNotificationRepository
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
import kotlin.math.max

data class HomeNotificationUi(
    val id: Long,
    val childId: Long?,
    val title: String,
    val message: String,
    val time: String,
    val isRead: Boolean,
    val destinationTab: Int
)

data class ParentHomeUiState(
    val isLoading: Boolean = false,
    val parentName: String = "",
    val greeting: String = "",
    val totalChildren: Int = 0,
    val activeChildrenToday: Int = 0,
    val childrenNeedingSupport: Int = 0,
    val weeklyAchievementsCount: Int = 0,
    val totalPointsThisWeek: Int = 0,
    val weeklyProgressPercentage: Double = 0.0,
    val aiInsightMessage: String = "",
    val hasUnreadNotifications: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ParentHomeViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val childDao: ChildDao,
    private val notificationRepository: AppNotificationRepository,
    private val notificationHandler: ParentNotificationHandler,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ParentHomeUiState(isLoading = true))
    val uiState: StateFlow<ParentHomeUiState> = _uiState.asStateFlow()

    private val childrenFlow = sessionManager.userId
        .flatMapLatest { uid ->
            if (uid == -1L) flowOf(emptyList())
            else childDao.getChildrenByUser(uid)
        }

    private val notificationFlow = sessionManager.userId
        .flatMapLatest { uid ->
            if (uid == -1L) flowOf(emptyList())
            else notificationRepository.observeByUser(uid)
        }

    val notifications: StateFlow<List<HomeNotificationUi>> = notificationFlow
        .map { list -> list.map(::toNotificationUi) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val hasUnreadNotifications: StateFlow<Boolean> = notificationFlow
        .map { list -> list.any { !it.isRead } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val children: StateFlow<List<ChildEntity>> = childrenFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedChild = MutableStateFlow<ChildEntity?>(null)
    val selectedChild: StateFlow<ChildEntity?> = _selectedChild.asStateFlow()

    fun selectChild(child: ChildEntity) {
        _selectedChild.value = child
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch {
            val userId = sessionManager.userId.first()
            if (userId != -1L) {
                notificationRepository.markAllAsReadForUser(userId)
            }
        }
    }

    fun markNotificationRead(id: Long) {
        viewModelScope.launch {
            notificationRepository.markAsRead(id)
        }
    }

    init {
        loadHomeScreenData()
    }

    fun loadHomeScreenData() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)

                val name = sessionManager.userName.first().ifBlank { "" }
                val greeting = getGreeting()
                val children = childrenFlow.first()

                children.forEach { child ->
                    notificationHandler.evaluateAssessmentMissing(child.id)
                    notificationHandler.evaluateSessionInactivity(child.id)
                }

                val notifications = notificationFlow.first()

                val total = children.size
                val active = children.count { it.status == ChildStatus.ACTIVE.name }
                val needsSupport = children.count { it.status == ChildStatus.NEEDS_SUPPORT.name }

                _uiState.value = ParentHomeUiState(
                    isLoading = false,
                    parentName = name,
                    greeting = greeting,
                    totalChildren = total,
                    activeChildrenToday = active,
                    childrenNeedingSupport = needsSupport,
                    weeklyAchievementsCount = 0,
                    totalPointsThisWeek = 0,
                    weeklyProgressPercentage = 0.0,
                    aiInsightMessage = generateAIInsight(active),
                    hasUnreadNotifications = notifications.any { !it.isRead }
                )
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = context.getString(R.string.error_generic_unexpected)
                )
            }
        }
    }

    fun retryLoadData() = loadHomeScreenData()

    private fun getGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour < 12 -> context.getString(R.string.greeting_good_morning)
            hour < 17 -> context.getString(R.string.greeting_good_afternoon)
            else -> context.getString(R.string.greeting_good_evening)
        }
    }

    private fun generateAIInsight(activeToday: Int): String =
        if (activeToday > 0) context.getString(R.string.parent_home_featured_desc)
        else context.getString(R.string.ai_empty_subtitle)

    private fun toNotificationUi(notification: AppNotification): HomeNotificationUi =
        HomeNotificationUi(
            id = notification.id,
            childId = notification.childId,
            title = notification.title,
            message = notification.message,
            time = formatRelativeTime(notification.createdAt),
            isRead = notification.isRead,
            destinationTab = notification.destinationTab
        )

    private fun formatRelativeTime(createdAt: Long): String {
        val diffMillis = max(0L, System.currentTimeMillis() - createdAt)
        val minutes = diffMillis / (60L * 1000L)
        val hours = diffMillis / (60L * 60L * 1000L)
        val days = diffMillis / (24L * 60L * 60L * 1000L)

        return when {
            minutes < 60L -> context.getString(
                R.string.label_notification_minutes_ago,
                max(1L, minutes).toInt()
            )
            hours == 1L -> context.getString(R.string.label_notification_hour_ago)
            hours < 24L -> context.getString(R.string.label_notification_hours_ago, hours.toInt())
            days == 1L -> context.getString(R.string.label_notification_day_ago)
            else -> context.getString(R.string.label_notification_days_ago, days.toInt())
        }
    }
}
