package com.babybloom.data.repository

import com.babybloom.data.local.dao.SessionDao
import com.babybloom.data.local.entity.SessionEntity
import com.babybloom.data.local.entity.AttentionScoreRow
import com.babybloom.domain.model.Session
import com.babybloom.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SessionRepositoryImpl @Inject constructor(
    private val sessionDao: SessionDao
) : SessionRepository {

    override suspend fun startSession(session: Session): Long =
        sessionDao.insert(session.toEntity())

    override suspend fun endSession(sessionId: Long, endTime: Long) =
        sessionDao.endSession(sessionId, endTime)

    override suspend fun getSessionById(sessionId: Long): Session? =
        sessionDao.getById(sessionId)?.toDomain()

    override fun getSessionsByChild(childId: Long): Flow<List<Session>> =
        sessionDao.getSessionsByChild(childId).map { list -> list.map { it.toDomain() } }

    override suspend fun getRecentSessions(childId: Long, limit: Int): List<Session> =
        sessionDao.getRecentSessions(childId, limit).map { it.toDomain() }

    override fun countByChild(childId: Long): Flow<Int> =
        sessionDao.countByChild(childId)

    override suspend fun getAttentionScoresForChart(childId: Long): List<AttentionScoreRow> =
        sessionDao.getAttentionScoresForChart(childId)
}

// ── Mappers ───────────────────────────────────────────────────────────────────

fun SessionEntity.toDomain() = Session(
    id             = id,
    userId         = userId,
    childId        = childId,
    startTime      = startTime,
    endTime        = endTime,
    isAssessment   = isAssessment,
    attentionScore = attentionScore
)

fun Session.toEntity() = SessionEntity(
    id             = id,
    userId         = userId,
    childId        = childId,
    startTime      = startTime,
    endTime        = endTime,
    isAssessment   = isAssessment,
    attentionScore = attentionScore
)
