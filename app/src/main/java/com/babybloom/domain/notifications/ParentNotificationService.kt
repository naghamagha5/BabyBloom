package com.babybloom.domain.notifications

import android.content.Context
import android.text.BidiFormatter
import com.babybloom.R
import com.babybloom.domain.insight.InsightGenerationPolicy
import com.babybloom.domain.model.AppNotification
import com.babybloom.domain.model.ChildProfile
import com.babybloom.domain.model.ChildStatus
import com.babybloom.domain.model.NotificationDestinationTab
import com.babybloom.domain.model.NotificationType
import com.babybloom.domain.repository.AiInsightRepository
import com.babybloom.domain.repository.AppNotificationRepository
import com.babybloom.domain.repository.AssessmentRepository
import com.babybloom.domain.repository.ChildRepository
import com.babybloom.domain.repository.SessionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

open class ParentNotificationService @Inject constructor(
    private val notificationRepository: AppNotificationRepository,
    private val childRepository: ChildRepository,
    private val sessionRepository: SessionRepository,
    private val aiInsightRepository: AiInsightRepository,
    private val assessmentRepository: AssessmentRepository,
    @ApplicationContext private val context: Context
) : ParentNotificationHandler {

    override suspend fun onAssessmentResultAvailable(childId: Long, assessmentId: Long) {
        val child = childRepository.getById(childId) ?: return
        val childName = formatChildName(child.name)
        notificationRepository.deleteTypeForChild(child.userId, childId, NotificationType.ASSESSMENT_MISSING)
        notificationRepository.create(
            AppNotification(
                userId = child.userId,
                childId = childId,
                type = NotificationType.ASSESSMENT_RESULT_AVAILABLE,
                title = text(R.string.notif_title_assessment_available, childName),
                message = text(R.string.notif_body_assessment_available, childName),
                eventKey = "ASSESSMENT:$childId:$assessmentId",
                destinationTab = NotificationDestinationTab.ANALYTICS
            )
        )
    }

    override suspend fun onStatusChanged(
        childId: Long,
        previousStatus: ChildStatus,
        newStatus: ChildStatus
    ) {
        if (previousStatus == newStatus) return

        val child = childRepository.getById(childId) ?: return
        val childName = formatChildName(child.name)
        val createdAt = System.currentTimeMillis()

        val notification = when (newStatus) {
            ChildStatus.NEEDS_SUPPORT -> AppNotification(
                userId = child.userId,
                childId = childId,
                type = NotificationType.CHILD_NEEDS_SUPPORT,
                title = text(R.string.notif_title_needs_support, childName),
                message = text(R.string.notif_body_needs_support, childName),
                eventKey = "STATUS:$childId:${newStatus.name}:$createdAt",
                destinationTab = NotificationDestinationTab.ANALYTICS
            )
            ChildStatus.ACTIVE -> {
                if (previousStatus != ChildStatus.NEEDS_SUPPORT) return
                AppNotification(
                    userId = child.userId,
                    childId = childId,
                    type = NotificationType.STATUS_CHANGED,
                    title = text(R.string.notif_title_status_active, childName),
                    message = text(R.string.notif_body_status_active, childName),
                    eventKey = "STATUS:$childId:${newStatus.name}:$createdAt",
                    destinationTab = NotificationDestinationTab.ANALYTICS
                )
            }
            ChildStatus.CALM -> {
                if (previousStatus != ChildStatus.NEEDS_SUPPORT) return
                AppNotification(
                    userId = child.userId,
                    childId = childId,
                    type = NotificationType.STATUS_CHANGED,
                    title = text(R.string.notif_title_status_calm, childName),
                    message = text(R.string.notif_body_status_calm, childName),
                    eventKey = "STATUS:$childId:${newStatus.name}:$createdAt",
                    destinationTab = NotificationDestinationTab.ANALYTICS
                )
            }
        }

        notificationRepository.create(notification)
    }

    override suspend fun onProgressUpdated(profile: ChildProfile) {
        val child = childRepository.getById(profile.childId) ?: return
        val childName = formatChildName(child.name)
        val milestone = progressMilestoneFor(profile.overallProgressPercent) ?: return

        notificationRepository.create(
            AppNotification(
                userId = child.userId,
                childId = child.id,
                type = NotificationType.PROGRESS_MILESTONE,
                title = text(R.string.notif_title_progress_milestone, childName),
                message = text(
                    R.string.notif_body_progress_milestone,
                    childName,
                    milestone
                ),
                eventKey = "PROGRESS:${child.id}:$milestone",
                destinationTab = NotificationDestinationTab.ANALYTICS
            )
        )
    }

    override suspend fun onInsightReadyCheck(profile: ChildProfile) {
        if (!profile.assessmentCompleted) return

        val child = childRepository.getById(profile.childId) ?: return
        val childName = formatChildName(child.name)
        if (notificationRepository.hasUnreadTypeForChild(child.userId, child.id, NotificationType.AI_INSIGHT_READY)) {
            return
        }

        val latestCompletedSession = sessionRepository
            .getRecentSessions(profile.childId, limit = 1)
            .firstOrNull { it.endTime != null }
            ?: return

        val latestInsight = aiInsightRepository.getLatestForChild(profile.childId)
        val hasNewData = latestInsight == null ||
            (latestCompletedSession.endTime ?: latestCompletedSession.startTime) > latestInsight.generatedAt

        if (!hasNewData || !InsightGenerationPolicy.canGenerate(latestInsight?.generatedAt)) {
            return
        }

        val readyToken = latestInsight?.id?.toString() ?: "INITIAL"

        notificationRepository.create(
            AppNotification(
                userId = child.userId,
                childId = child.id,
                type = NotificationType.AI_INSIGHT_READY,
                title = text(R.string.notif_title_insight_ready, childName),
                message = text(R.string.notif_body_insight_ready, childName),
                eventKey = "AI_READY:${child.id}:$readyToken",
                destinationTab = NotificationDestinationTab.AI_INSIGHTS
            )
        )
    }

    override suspend fun onAiInsightGenerated(childId: Long) {
        val child = childRepository.getById(childId) ?: return
        notificationRepository.deleteTypeForChild(child.userId, childId, NotificationType.AI_INSIGHT_READY)
    }

    override suspend fun evaluateAssessmentMissing(childId: Long) {
        val child = childRepository.getById(childId) ?: return
        val childName = formatChildName(child.name)
        if (assessmentRepository.hasAssessment(childId)) return
        if (System.currentTimeMillis() - child.createdAt < ASSESSMENT_MISSING_DELAY_MS) return

        notificationRepository.create(
            AppNotification(
                userId = child.userId,
                childId = child.id,
                type = NotificationType.ASSESSMENT_MISSING,
                title = text(R.string.notif_title_assessment_missing, childName),
                message = text(R.string.notif_body_assessment_missing, childName),
                eventKey = "ASSESSMENT_MISSING:${child.id}",
                destinationTab = NotificationDestinationTab.ANALYTICS
            )
        )
    }

    override suspend fun evaluateSessionInactivity(childId: Long) {
        val child = childRepository.getById(childId) ?: return
        val childName = formatChildName(child.name)
        val latestCompletedSession = sessionRepository
            .getAllSessions(childId)
            .firstOrNull { it.endTime != null }
            ?: return

        val lastCompletedAt = latestCompletedSession.endTime ?: latestCompletedSession.startTime
        if (System.currentTimeMillis() - lastCompletedAt < SESSION_INACTIVITY_DELAY_MS) return

        notificationRepository.create(
            AppNotification(
                userId = child.userId,
                childId = child.id,
                type = NotificationType.SESSION_INACTIVITY,
                title = text(R.string.notif_title_session_inactivity, childName),
                message = text(R.string.notif_body_session_inactivity, childName),
                eventKey = "SESSION_INACTIVITY:${child.id}:${latestCompletedSession.id}",
                destinationTab = NotificationDestinationTab.ANALYTICS
            )
        )
    }

    private fun progressMilestoneFor(progressPercent: Float): Int? = when {
        progressPercent >= 100f -> 100
        progressPercent >= 75f -> 75
        progressPercent >= 50f -> 50
        progressPercent >= 25f -> 25
        else -> null
    }

    protected open fun text(id: Int, vararg formatArgs: Any?): String =
        context.getString(id, *formatArgs)

    private companion object {
        const val ASSESSMENT_MISSING_DELAY_MS = 24L * 60L * 60L * 1000L
        const val SESSION_INACTIVITY_DELAY_MS = 3L * 24L * 60L * 60L * 1000L
    }

    protected open fun formatChildName(name: String): String =
        BidiFormatter.getInstance().unicodeWrap(name)
}
