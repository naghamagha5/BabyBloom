package com.babybloom.domain.repository

import com.babybloom.data.local.entity.ActivityRecommendationEntity

interface ActivityRecommendationRepository {
    suspend fun insertAll(recommendations: List<ActivityRecommendationEntity>)
    suspend fun insert(recommendation: ActivityRecommendationEntity): Long
    suspend fun getPendingForChild(childId: Long): List<ActivityRecommendationEntity>
    suspend fun getForSession(childId: Long, sessionId: Long): List<ActivityRecommendationEntity>
    suspend fun markAsPlayed(id: Long, score: Float)
    suspend fun assignSession(childId: Long, sessionId: Long)
    suspend fun deleteOldPlayed(childId: Long, before: Long)
    suspend fun clearPendingForChild(childId: Long)
}