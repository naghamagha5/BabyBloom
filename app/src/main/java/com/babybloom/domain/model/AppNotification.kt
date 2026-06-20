package com.babybloom.domain.model

enum class NotificationType {
    AI_INSIGHT_READY,
    CHILD_NEEDS_SUPPORT,
    STATUS_CHANGED,
    PROGRESS_MILESTONE,
    ASSESSMENT_RESULT_AVAILABLE,
    ASSESSMENT_MISSING,
    SESSION_INACTIVITY
}

object NotificationDestinationTab {
    const val ANALYTICS = 0
    const val AI_INSIGHTS = 1
    const val SETTINGS = 2
}

data class AppNotification(
    val id: Long = 0,
    val userId: Long,
    val childId: Long? = null,
    val type: NotificationType,
    val title: String,
    val message: String,
    val createdAt: Long = System.currentTimeMillis(),
    val readAt: Long? = null,
    val eventKey: String,
    val destinationTab: Int = NotificationDestinationTab.ANALYTICS
) {
    val isRead: Boolean
        get() = readAt != null
}
