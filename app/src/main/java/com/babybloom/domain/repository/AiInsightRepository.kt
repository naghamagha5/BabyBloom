package com.babybloom.domain.repository

import com.babybloom.domain.model.AiInsight
import kotlinx.coroutines.flow.Flow

interface AiInsightRepository {
    fun getLatestInsight(childId: Long): Flow<AiInsight?>
    suspend fun saveInsight(insight: AiInsight)
    suspend fun deleteInsightsForChild(childId: Long)
    suspend fun save(insight: AiInsight): Long
    suspend fun getLatestForChild(childId: Long): AiInsight?
    suspend fun getAllForChild(childId: Long): List<AiInsight>
    suspend fun deleteOldForChild(childId: Long, keepLatest: Int = 3)
}