package com.babybloom.domain.repository

import com.babybloom.data.local.entity.AttentionScoreRow
import com.babybloom.domain.model.Session
import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    suspend fun startSession(session: Session): Long
    suspend fun endSession(sessionId: Long, endTime: Long)
    fun getSessionsByChild(childId: Long): Flow<List<Session>>
    suspend fun getRecentSessions(childId: Long, limit: Int): List<Session>
    fun countByChild(childId: Long): Flow<Int>
    suspend fun getAttentionScoresForChart(childId: Long): List<AttentionScoreRow>
}