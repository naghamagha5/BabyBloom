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
        val latestRecentSession = latestRecentSession(recentSessions) ?: return ChildStatus.CALM
        if (needsSupportForSession(latestRecentSession, recentResults)) return ChildStatus.NEEDS_SUPPORT
        return ChildStatus.ACTIVE
    }

    private fun latestRecentSession(sessions: List<Session>): Session? {
        val now = System.currentTimeMillis()
        val windowMs = ACTIVE_WINDOW_DAYS * 24L * 60L * 60L * 1000L
        return sessions
            .filter { it.startTime >= now - windowMs }
            .maxByOrNull { it.startTime }
    }

    private fun needsSupportForSession(session: Session, results: List<ActivityResult>): Boolean {
        val sessionScores = results
            .filter { it.sessionId == session.id }
            .map { it.score }

        if (sessionScores.isEmpty()) return false

        val badScoreCount = sessionScores.count { it < LOW_SCORE_THRESHOLD }
        val goodScoreCount = sessionScores.size - badScoreCount

        return badScoreCount >= goodScoreCount
    }

    private companion object {
        const val ACTIVE_WINDOW_DAYS = 7
        const val LOW_SCORE_THRESHOLD = 0.50f
    }
}
