package com.babybloom.domain.notifications

import android.content.ContextWrapper
import com.babybloom.R
import com.babybloom.data.local.entity.AssessmentResultEntity
import com.babybloom.data.local.entity.AttentionScoreRow
import com.babybloom.domain.model.AiInsight
import com.babybloom.domain.model.AppNotification
import com.babybloom.domain.model.Child
import com.babybloom.domain.model.ChildStatus
import com.babybloom.domain.model.NotificationType
import com.babybloom.domain.model.Session
import com.babybloom.domain.repository.AiInsightRepository
import com.babybloom.domain.repository.AppNotificationRepository
import com.babybloom.domain.repository.AssessmentRepository
import com.babybloom.domain.repository.ChildRepository
import com.babybloom.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParentNotificationServiceTest {

    @Test
    fun `assessment missing creates notification after delay when no assessment exists`() = runTest {
        val child = child(createdAt = now() - twoDays())
        val notifications = FakeAppNotificationRepository()
        val service = service(
            childRepository = FakeChildRepository(child),
            notificationRepository = notifications,
            assessmentRepository = FakeAssessmentRepository(hasAssessment = false),
            sessionRepository = FakeSessionRepository(),
            aiInsightRepository = FakeAiInsightRepository()
        )

        service.evaluateAssessmentMissing(child.id)

        assertEquals(1, notifications.created.size)
        assertEquals(NotificationType.ASSESSMENT_MISSING, notifications.created.single().type)
    }

    @Test
    fun `assessment missing does not create notification before delay`() = runTest {
        val child = child(createdAt = now() - sixHours())
        val notifications = FakeAppNotificationRepository()
        val service = service(
            childRepository = FakeChildRepository(child),
            notificationRepository = notifications,
            assessmentRepository = FakeAssessmentRepository(hasAssessment = false),
            sessionRepository = FakeSessionRepository(),
            aiInsightRepository = FakeAiInsightRepository()
        )

        service.evaluateAssessmentMissing(child.id)

        assertTrue(notifications.created.isEmpty())
    }

    @Test
    fun `assessment missing does not create notification when assessment already exists`() = runTest {
        val child = child(createdAt = now() - twoDays())
        val notifications = FakeAppNotificationRepository()
        val service = service(
            childRepository = FakeChildRepository(child),
            notificationRepository = notifications,
            assessmentRepository = FakeAssessmentRepository(hasAssessment = true),
            sessionRepository = FakeSessionRepository(),
            aiInsightRepository = FakeAiInsightRepository()
        )

        service.evaluateAssessmentMissing(child.id)

        assertTrue(notifications.created.isEmpty())
    }

    @Test
    fun `session inactivity creates notification only after completed session ages past threshold`() = runTest {
        val child = child()
        val completedSession = Session(
            id = 77L,
            userId = child.userId,
            childId = child.id,
            startTime = now() - fourDays(),
            endTime = now() - fourDays() + 30_000L
        )
        val notifications = FakeAppNotificationRepository()
        val service = service(
            childRepository = FakeChildRepository(child),
            notificationRepository = notifications,
            assessmentRepository = FakeAssessmentRepository(hasAssessment = false),
            sessionRepository = FakeSessionRepository(allSessions = listOf(completedSession)),
            aiInsightRepository = FakeAiInsightRepository()
        )

        service.evaluateSessionInactivity(child.id)

        assertEquals(1, notifications.created.size)
        assertEquals(NotificationType.SESSION_INACTIVITY, notifications.created.single().type)
        assertEquals("SESSION_INACTIVITY:${child.id}:${completedSession.id}", notifications.created.single().eventKey)
    }

    @Test
    fun `session inactivity does not create notification when no completed session exists`() = runTest {
        val child = child()
        val incompleteSession = Session(
            id = 88L,
            userId = child.userId,
            childId = child.id,
            startTime = now() - fourDays(),
            endTime = null
        )
        val notifications = FakeAppNotificationRepository()
        val service = service(
            childRepository = FakeChildRepository(child),
            notificationRepository = notifications,
            assessmentRepository = FakeAssessmentRepository(hasAssessment = false),
            sessionRepository = FakeSessionRepository(allSessions = listOf(incompleteSession)),
            aiInsightRepository = FakeAiInsightRepository()
        )

        service.evaluateSessionInactivity(child.id)

        assertTrue(notifications.created.isEmpty())
    }

    @Test
    fun `session inactivity does not create notification before threshold`() = runTest {
        val child = child()
        val recentCompletedSession = Session(
            id = 99L,
            userId = child.userId,
            childId = child.id,
            startTime = now() - oneDay(),
            endTime = now() - oneDay() + 30_000L
        )
        val notifications = FakeAppNotificationRepository()
        val service = service(
            childRepository = FakeChildRepository(child),
            notificationRepository = notifications,
            assessmentRepository = FakeAssessmentRepository(hasAssessment = false),
            sessionRepository = FakeSessionRepository(allSessions = listOf(recentCompletedSession)),
            aiInsightRepository = FakeAiInsightRepository()
        )

        service.evaluateSessionInactivity(child.id)

        assertTrue(notifications.created.isEmpty())
    }

    private fun service(
        childRepository: ChildRepository,
        notificationRepository: FakeAppNotificationRepository,
        assessmentRepository: AssessmentRepository,
        sessionRepository: SessionRepository,
        aiInsightRepository: AiInsightRepository
    ) = object : ParentNotificationService(
        notificationRepository = notificationRepository,
        childRepository = childRepository,
        sessionRepository = sessionRepository,
        aiInsightRepository = aiInsightRepository,
        assessmentRepository = assessmentRepository,
        context = FakeNotificationContext()
    ) {
        override fun formatChildName(name: String): String = name

        override fun text(id: Int, vararg formatArgs: Any?): String = when (id) {
            R.string.notif_title_assessment_missing -> "assessment missing ${formatArgs[0]}"
            R.string.notif_body_assessment_missing -> "assessment body ${formatArgs[0]}"
            R.string.notif_title_session_inactivity -> "inactive ${formatArgs[0]}"
            R.string.notif_body_session_inactivity -> "inactive body ${formatArgs[0]}"
            R.string.notif_title_assessment_available -> "assessment available ${formatArgs[0]}"
            R.string.notif_body_assessment_available -> "assessment available body ${formatArgs[0]}"
            R.string.notif_title_needs_support -> "needs support ${formatArgs[0]}"
            R.string.notif_body_needs_support -> "needs support body ${formatArgs[0]}"
            R.string.notif_title_status_active -> "active ${formatArgs[0]}"
            R.string.notif_body_status_active -> "active body ${formatArgs[0]}"
            R.string.notif_title_status_calm -> "calm ${formatArgs[0]}"
            R.string.notif_body_status_calm -> "calm body ${formatArgs[0]}"
            R.string.notif_title_progress_milestone -> "progress ${formatArgs[0]}"
            R.string.notif_body_progress_milestone -> "progress body ${formatArgs[0]} ${formatArgs[1]}"
            R.string.notif_title_insight_ready -> "insight ${formatArgs[0]}"
            R.string.notif_body_insight_ready -> "insight body ${formatArgs[0]}"
            else -> "string-$id ${formatArgs.joinToString()}"
        }
    }

    private fun child(createdAt: Long = now() - fourDays()) = Child(
        id = 1L,
        userId = 10L,
        name = "Adam",
        age = 6,
        status = ChildStatus.CALM,
        createdAt = createdAt
    )

    private fun now() = System.currentTimeMillis()
    private fun sixHours() = 6L * 60L * 60L * 1000L
    private fun oneDay() = 24L * 60L * 60L * 1000L
    private fun twoDays() = 2L * oneDay()
    private fun fourDays() = 4L * oneDay()
}

private class FakeNotificationContext : ContextWrapper(null)

private class FakeAppNotificationRepository : AppNotificationRepository {
    val created = mutableListOf<AppNotification>()

    override fun observeByUser(userId: Long): Flow<List<AppNotification>> = flowOf(emptyList())

    override suspend fun create(notification: AppNotification): Long {
        created += notification
        return created.size.toLong()
    }

    override suspend fun markAsRead(id: Long) = Unit
    override suspend fun markAllAsReadForUser(userId: Long) = Unit
    override suspend fun hasUnreadTypeForChild(userId: Long, childId: Long, type: NotificationType): Boolean = false
    override suspend fun deleteTypeForChild(userId: Long, childId: Long, type: NotificationType) = Unit
}

private class FakeChildRepository(
    private val child: Child?
) : ChildRepository {
    override suspend fun createChild(child: Child): Long = child.id
    override suspend fun updateChild(child: Child) = Unit
    override suspend fun deleteChild(child: Child) = Unit
    override fun getChildrenByUser(userId: Long): Flow<List<Child>> = flowOf(listOfNotNull(child))
    override suspend fun getById(id: Long): Child? = child?.takeIf { it.id == id }
    override fun observeById(id: Long): Flow<Child?> = flowOf(getByIdBlocking(id))

    private fun getByIdBlocking(id: Long): Child? = child?.takeIf { it.id == id }
}

private class FakeSessionRepository(
    private val allSessions: List<Session> = emptyList()
) : SessionRepository {
    override suspend fun startSession(session: Session): Long = session.id
    override suspend fun endSession(sessionId: Long, endTime: Long) = Unit
    override suspend fun getSessionById(sessionId: Long): Session? = allSessions.firstOrNull { it.id == sessionId }
    override fun getSessionsByChild(childId: Long): Flow<List<Session>> = flowOf(allSessions.filter { it.childId == childId })
    override suspend fun getRecentSessions(childId: Long, limit: Int): List<Session> =
        allSessions.filter { it.childId == childId }.take(limit)
    override suspend fun getAllSessions(childId: Long): List<Session> =
        allSessions.filter { it.childId == childId }
    override fun countByChild(childId: Long): Flow<Int> = flowOf(allSessions.count { it.childId == childId })
    override suspend fun getAttentionScoresForChart(childId: Long): List<AttentionScoreRow> = emptyList()
}

private class FakeAssessmentRepository(
    private val hasAssessment: Boolean
) : AssessmentRepository {
    override suspend fun save(result: AssessmentResultEntity): Long = 1L
    override suspend fun getLatestForChild(childId: Long): AssessmentResultEntity? = null
    override suspend fun hasAssessment(childId: Long): Boolean = hasAssessment
    override suspend fun deleteForChild(childId: Long) = Unit
}

private class FakeAiInsightRepository : AiInsightRepository {
    override fun getLatestInsight(childId: Long): Flow<AiInsight?> = emptyFlow()
    override suspend fun saveInsight(insight: AiInsight) = Unit
    override suspend fun deleteInsightsForChild(childId: Long) = Unit
    override suspend fun save(insight: AiInsight): Long = 0L
    override suspend fun getLatestForChild(childId: Long): AiInsight? = null
    override suspend fun getAllForChild(childId: Long): List<AiInsight> = emptyList()
    override suspend fun deleteOldForChild(childId: Long, keepLatest: Int) = Unit
}
