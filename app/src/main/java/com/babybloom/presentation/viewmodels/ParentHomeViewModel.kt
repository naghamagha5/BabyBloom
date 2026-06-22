package com.babybloom.presentation.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babybloom.R
import com.babybloom.data.local.dao.ChildDao
import com.babybloom.data.local.dao.ChildProfileSnapshotDao
import com.babybloom.data.local.entity.ChildEntity
import com.babybloom.di.SessionManager
import com.babybloom.domain.model.AppNotification
import com.babybloom.domain.model.ChildStatus
import com.babybloom.domain.model.ParsedInsight
import com.babybloom.domain.notifications.ParentNotificationHandler
import com.babybloom.domain.repository.AiInsightRepository
import com.babybloom.domain.repository.AppNotificationRepository
import com.babybloom.domain.repository.ChildProfileRepository
import com.babybloom.domain.repository.SessionRepository
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

data class SelectedChildInsightUiState(
    val briefMessage: String = "",
    val hasInsight: Boolean = false
)

@HiltViewModel
class ParentHomeViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val childDao: ChildDao,
    private val childProfileRepository: ChildProfileRepository,
    private val childProfileSnapshotDao: ChildProfileSnapshotDao,
    private val aiInsightRepository: AiInsightRepository,
    private val sessionRepository: SessionRepository,
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

    val selectedChildInsightUiState: StateFlow<SelectedChildInsightUiState?> = selectedChild
        .flatMapLatest { child ->
            if (child == null) {
                flowOf(null)
            } else {
                aiInsightRepository.getLatestInsight(child.id).map { insight ->
                    if (insight == null) {
                        SelectedChildInsightUiState(hasInsight = false)
                    } else {
                        SelectedChildInsightUiState(
                            briefMessage = buildBriefInsight(insight.insightText),
                            hasInsight = true
                        )
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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
                val weeklyStats = buildWeeklyStats(children)

                _uiState.value = ParentHomeUiState(
                    isLoading = false,
                    parentName = name,
                    greeting = greeting,
                    totalChildren = total,
                    activeChildrenToday = active,
                    childrenNeedingSupport = needsSupport,
                    weeklyAchievementsCount = weeklyStats.achievementsCount,
                    totalPointsThisWeek = weeklyStats.averageSessionMinutes,
                    weeklyProgressPercentage = weeklyStats.averageProgressPercent,
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
        if (activeToday > 0) context.getString(R.string.parent_home_ai_overview_message)
        else context.getString(R.string.parent_home_ai_select_prompt)

    private suspend fun buildWeeklyStats(children: List<ChildEntity>): WeeklyStats {
        if (children.isEmpty()) return WeeklyStats()

        val weekStartMillis = startOfCurrentWeekMillis()

        val completedSessionsThisWeek = children
            .flatMap { child -> sessionRepository.getAllSessions(child.id) }
            .filter { session ->
                val endTime = session.endTime ?: return@filter false
                endTime >= weekStartMillis
            }

        val averageProgressPercent = children
            .mapNotNull { child ->
                childProfileRepository.getByChildId(child.id)?.overallProgressPercent?.toDouble()
            }
            .takeIf { it.isNotEmpty() }
            ?.average()
            ?: 0.0

        val achievementsCount = children.sumOf { child ->
            countWeeklyLevelUps(
                childId = child.id,
                weekStartMillis = weekStartMillis
            )
        }

        val averageSessionMinutes = completedSessionsThisWeek
            .mapNotNull { session ->
                session.endTime?.let { endTime ->
                    ((endTime - session.startTime).coerceAtLeast(0L) / 60_000L).toInt()
                }
            }
            .takeIf { it.isNotEmpty() }
            ?.average()
            ?.toInt()
            ?: 0

        return WeeklyStats(
            averageProgressPercent = averageProgressPercent,
            achievementsCount = achievementsCount,
            averageSessionMinutes = averageSessionMinutes
        )
    }

    private suspend fun countWeeklyLevelUps(
        childId: Long,
        weekStartMillis: Long
    ): Int {
        val snapshots = childProfileSnapshotDao.getForChild(childId)
        if (snapshots.size < 2) return 0

        return snapshots
            .zipWithNext()
            .filter { (_, current) -> current.capturedAt >= weekStartMillis }
            .sumOf { (previous, current) ->
                (current.languageLevel - previous.languageLevel).coerceAtLeast(0) +
                    (current.numeracyLevel - previous.numeracyLevel).coerceAtLeast(0) +
                    (current.motorLevel - previous.motorLevel).coerceAtLeast(0)
            }
    }

    private fun startOfCurrentWeekMillis(): Long {
        val calendar = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.SATURDAY
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    private fun buildBriefInsight(insightText: String): String {
        val parsed = ParsedInsight.from(insightText)
        val parts = buildList {
            parsed.guidanceIntro.takeIf { it.isNotBlank() }?.let { add(it.trim()) }
            parsed.development.takeIf { it.isNotBlank() }?.let { add(it.trim()) }
            parsed.strengths.takeIf { it.isNotBlank() }?.let { add(it.trim()) }
            parsed.tip1Body.takeIf { it.isNotBlank() }?.let { add(it.trim()) }
            parsed.tip2Body.takeIf { it.isNotBlank() }?.let { add(it.trim()) }
            parsed.learningStyle.takeIf { it.isNotBlank() }?.let { add(it.trim()) }
        }

        if (parts.isEmpty()) return context.getString(R.string.parent_home_ai_summary_fallback)

        val summary = parts
            .take(3)
            .joinToString(separator = "\n\n") { part ->
                part.replace(Regex("\\s+"), " ").trim()
            }

        return if (summary.length <= 320) {
            summary
        } else {
            summary.take(317).trimEnd() + "..."
        }
    }

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

private data class WeeklyStats(
    val averageProgressPercent: Double = 0.0,
    val achievementsCount: Int = 0,
    val averageSessionMinutes: Int = 0
)
