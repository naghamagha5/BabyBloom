package com.babybloom.domain.status

import com.babybloom.domain.model.ActivityResult
import com.babybloom.domain.model.ChildProfile
import com.babybloom.domain.model.ChildStatus
import com.babybloom.domain.model.Session
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ChildStatusEvaluatorTest {

    private lateinit var evaluator: ChildStatusEvaluator

    @Before
    fun setUp() {
        evaluator = ChildStatusEvaluator()
    }

    @Test
    fun `no assessment and no results returns calm`() {
        val status = evaluator.evaluate(
            profile = profile(assessmentCompleted = false),
            recentResults = emptyList(),
            recentSessions = emptyList()
        )

        assertEquals(ChildStatus.CALM, status)
    }

    @Test
    fun `assessment done and no session in last seven days returns calm`() {
        val status = evaluator.evaluate(
            profile = profile(assessmentCompleted = true),
            recentResults = listOf(result(score = 0.60f, timestamp = nowMinusDays(10))),
            recentSessions = listOf(session(startTime = nowMinusDays(8)))
        )

        assertEquals(ChildStatus.CALM, status)
    }

    @Test
    fun `recent session and fewer than three scores returns needs support when scores are tied`() {
        val status = evaluator.evaluate(
            profile = profile(assessmentCompleted = true),
            recentResults = listOf(
                result(score = 0.60f, timestamp = nowMinusHours(6)),
                result(score = 0.30f, timestamp = nowMinusHours(3))
            ),
            recentSessions = listOf(session(id = 1L, startTime = nowMinusDays(1)))
        )

        assertEquals(ChildStatus.NEEDS_SUPPORT, status)
    }

    @Test
    fun `recent session with most scores good returns active`() {
        val status = evaluator.evaluate(
            profile = profile(assessmentCompleted = true),
            recentResults = listOf(
                result(score = 0.85f, timestamp = nowMinusHours(1)),
                result(score = 0.75f, timestamp = nowMinusHours(2)),
                result(score = 0.65f, timestamp = nowMinusHours(3)),
                result(score = 0.55f, timestamp = nowMinusHours(4)),
                result(score = 0.45f, timestamp = nowMinusHours(5))
            ),
            recentSessions = listOf(session(id = 1L, startTime = nowMinusDays(2)))
        )

        assertEquals(ChildStatus.ACTIVE, status)
    }

    @Test
    fun `recent session with all scores bad from the beginning returns needs support`() {
        val status = evaluator.evaluate(
            profile = profile(assessmentCompleted = true),
            recentResults = listOf(
                result(score = 0.28f, timestamp = nowMinusHours(1)),
                result(score = 0.35f, timestamp = nowMinusHours(2)),
                result(score = 0.30f, timestamp = nowMinusHours(3)),
                result(score = 0.22f, timestamp = nowMinusHours(4)),
                result(score = 0.18f, timestamp = nowMinusHours(5))
            ),
            recentSessions = listOf(session(id = 1L, startTime = nowMinusDays(1)))
        )

        assertEquals(ChildStatus.NEEDS_SUPPORT, status)
    }

    @Test
    fun `recent session with mostly bad scores returns needs support`() {
        val status = evaluator.evaluate(
            profile = profile(assessmentCompleted = true),
            recentResults = listOf(
                result(score = 0.28f, timestamp = nowMinusHours(1)),
                result(score = 0.35f, timestamp = nowMinusHours(2)),
                result(score = 0.38f, timestamp = nowMinusHours(3)),
                result(score = 0.72f, timestamp = nowMinusHours(4)),
                result(score = 0.80f, timestamp = nowMinusHours(5))
            ),
            recentSessions = listOf(session(id = 1L, startTime = nowMinusDays(1)))
        )

        assertEquals(ChildStatus.NEEDS_SUPPORT, status)
    }

    @Test
    fun `recent session with half bad and half good returns needs support`() {
        val status = evaluator.evaluate(
            profile = profile(assessmentCompleted = true),
            recentResults = listOf(
                result(score = 0.65f, timestamp = nowMinusHours(1)),
                result(score = 0.20f, timestamp = nowMinusHours(2))
            ),
            recentSessions = listOf(session(id = 1L, startTime = nowMinusDays(3)))
        )

        assertEquals(ChildStatus.NEEDS_SUPPORT, status)
    }

    @Test
    fun `most recent recent session is used when older sessions were worse`() {
        val status = evaluator.evaluate(
            profile = profile(assessmentCompleted = true),
            recentResults = listOf(
                result(score = 0.85f, timestamp = nowMinusHours(1), sessionId = 2L),
                result(score = 0.78f, timestamp = nowMinusHours(2), sessionId = 2L),
                result(score = 0.22f, timestamp = nowMinusDays(2), sessionId = 1L),
                result(score = 0.18f, timestamp = nowMinusDays(2) - 1_000L, sessionId = 1L)
            ),
            recentSessions = listOf(
                session(id = 2L, startTime = nowMinusHours(2)),
                session(id = 1L, startTime = nowMinusDays(2))
            )
        )

        assertEquals(ChildStatus.ACTIVE, status)
    }

    private fun profile(assessmentCompleted: Boolean) = ChildProfile(
        childId = 1L,
        assessmentCompleted = assessmentCompleted
    )

    private fun result(score: Float, timestamp: Long, sessionId: Long = 1L) = ActivityResult(
        sessionId = sessionId,
        childId = 1L,
        activityId = "activity_$timestamp",
        contentId = "content_$timestamp",
        score = score,
        duration = 30_000L,
        correctCount = 1,
        incorrectCount = 0,
        timestamp = timestamp
    )

    private fun session(id: Long = 1L, startTime: Long) = Session(
        id = id,
        userId = 1L,
        childId = 1L,
        startTime = startTime
    )

    private fun nowMinusDays(days: Long): Long =
        System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L

    private fun nowMinusHours(hours: Long): Long =
        System.currentTimeMillis() - hours * 60L * 60L * 1000L
}
