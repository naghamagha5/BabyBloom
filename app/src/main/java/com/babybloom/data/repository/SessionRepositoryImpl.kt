package com.babybloom.data.repository

import com.babybloom.data.local.dao.SessionDao
import com.babybloom.data.local.entity.SessionEntity
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

    override suspend fun endSession(sessionId: Long, endTime: Long) {
        val session = sessionDao.getById(sessionId) ?: return
        sessionDao.update(session.copy(endTime = endTime))
    }

    override fun getSessionsByChild(childId: Long): Flow<List<Session>> =
        sessionDao.getSessionsByChild(childId).map { it.map { e -> e.toDomain() } }

    override suspend fun getRecentSessions(childId: Long, limit: Int): List<Session> =
        sessionDao.getRecentSessions(childId, limit).map { it.toDomain() }
}

fun SessionEntity.toDomain() = Session(id, userId, childId, startTime, endTime, isAssessment, attentionScore)
fun Session.toEntity() = SessionEntity(id, userId, childId, startTime, endTime, isAssessment, attentionScore)