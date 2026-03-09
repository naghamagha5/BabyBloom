package com.babybloom.domain.repository

import com.babybloom.domain.model.AiInsight
import kotlinx.coroutines.flow.Flow

interface AiInsightRepository {
    fun getLatestInsight(childId: Long): Flow<AiInsight?>
    suspend fun saveInsight(insight: AiInsight)
    suspend fun deleteInsightsForChild(childId: Long)
}