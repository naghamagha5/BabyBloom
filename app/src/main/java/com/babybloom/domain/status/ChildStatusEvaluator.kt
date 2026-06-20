package com.babybloom.domain.status

import com.babybloom.domain.model.ActivityResult
import com.babybloom.domain.model.ChildProfile
import com.babybloom.domain.model.ChildStatus
import com.babybloom.domain.model.Session
import javax.inject.Inject

class ChildStatusEvaluator @Inject constructor() {

    fun evaluate(
        profile: ChildProfile,
        recentResults: List<ActivityResult>,
        recentSessions: List<Session>
    ): ChildStatus {
        if (!profile.assessmentCompleted && recentResults.isEmpty()) return ChildStatus.CALM
        if (!hasRecentSession(recentSessions)) return ChildStatus.CALM
        if (isScoreDeclining(recentResults)) return ChildStatus.NEEDS_SUPPORT
        return ChildStatus.ACTIVE
    }

    private fun hasRecentSession(sessions: List<Session>): Boolean {
        val now = System.currentTimeMillis()
        val windowMs = ACTIVE_WINDOW_DAYS * 24L * 60L * 60L * 1000L
        return sessions.any { it.startTime >= now - windowMs }
    }

    private fun isScoreDeclining(results: List<ActivityResult>): Boolean {
        val recentScores = results
            .sortedBy { it.timestamp }
            .takeLast(5)
            .map { it.score }

        if (recentScores.size < MIN_SCORES_FOR_TREND) return false

        val firstHalf = recentScores.take(recentScores.size / 2)
        val secondHalf = recentScores.takeLast(recentScores.size / 2)
        val trendDelta = secondHalf.average() - firstHalf.average()
        val lowScoreCount = recentScores.takeLast(3).count { it < LOW_SCORE_THRESHOLD }

        return trendDelta <= TREND_DECLINE_THRESHOLD && lowScoreCount >= LOW_SCORE_MIN_COUNT
    }

    private companion object {
        const val ACTIVE_WINDOW_DAYS = 7
        const val MIN_SCORES_FOR_TREND = 3
        const val TREND_DECLINE_THRESHOLD = -0.10
        const val LOW_SCORE_THRESHOLD = 0.40f
        const val LOW_SCORE_MIN_COUNT = 2
    }
}
