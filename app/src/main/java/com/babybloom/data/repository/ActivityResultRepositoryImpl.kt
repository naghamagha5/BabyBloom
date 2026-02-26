package com.babybloom.data.repository

import com.babybloom.data.local.dao.ActivityResultDao
import com.babybloom.data.local.entity.ActivityResultEntity
import com.babybloom.domain.model.ActivityResult
import com.babybloom.domain.repository.ActivityResultRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ActivityResultRepositoryImpl @Inject constructor(
    private val activityResultDao: ActivityResultDao
) : ActivityResultRepository {

    override suspend fun saveResult(result: ActivityResult): Long =
        activityResultDao.insert(result.toEntity())

    override suspend fun getBySession(sessionId: Long): List<ActivityResult> =
        activityResultDao.getBySession(sessionId).map { it.toDomain() }

    override suspend fun getByChild(childId: Long): List<ActivityResult> =
        activityResultDao.getByChild(childId).map { it.toDomain() }

    override suspend fun getRecentBySkillArea(childId: Long, skillArea: String, limit: Int): List<ActivityResult> =
        activityResultDao.getRecentBySkillArea(childId, skillArea, limit).map { it.toDomain() }

    override suspend fun getRecentByModality(childId: Long, modality: String, limit: Int): List<ActivityResult> =
        activityResultDao.getRecentByModality(childId, modality, limit).map { it.toDomain() }

    override fun observeByChild(childId: Long): Flow<List<ActivityResult>> =
        activityResultDao.observeByChild(childId).map { it.map { e -> e.toDomain() } }
}

fun ActivityResultEntity.toDomain() = ActivityResult(id, sessionId, childId, activityId, score, duration, correctCount, incorrectCount, timestamp)
fun ActivityResult.toEntity() = ActivityResultEntity(id, sessionId, childId, activityId, score, duration, correctCount, incorrectCount, timestamp)