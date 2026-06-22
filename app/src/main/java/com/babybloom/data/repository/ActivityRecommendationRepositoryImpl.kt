package com.babybloom.data.repository

import com.babybloom.data.local.dao.ActivityRecommendationDao
import com.babybloom.data.local.entity.ActivityRecommendationEntity
import com.babybloom.domain.repository.ActivityRecommendationRepository
import javax.inject.Inject

class ActivityRecommendationRepositoryImpl @Inject constructor(
    private val dao: ActivityRecommendationDao
) : ActivityRecommendationRepository {

    override suspend fun insertAll(recommendations: List<ActivityRecommendationEntity>) =
        dao.insertAll(recommendations)

    override suspend fun insert(recommendation: ActivityRecommendationEntity): Long =
        dao.insert(recommendation)

    override suspend fun getPendingForChild(childId: Long): List<ActivityRecommendationEntity> =
        dao.getPendingForChild(childId)

    override suspend fun getForSession(childId: Long, sessionId: Long): List<ActivityRecommendationEntity> =
        dao.getForSession(childId, sessionId)

    override suspend fun markAsPlayed(id: Long, score: Float) =
        dao.markAsPlayed(id, score)

    override suspend fun assignSession(childId: Long, sessionId: Long) =
        dao.assignSession(childId, sessionId)

    override suspend fun deleteOldPlayed(childId: Long, before: Long) =
        dao.deleteOldPlayed(childId, before)

    override suspend fun clearPendingForChild(childId: Long) =
        dao.clearPendingForChild(childId)
}