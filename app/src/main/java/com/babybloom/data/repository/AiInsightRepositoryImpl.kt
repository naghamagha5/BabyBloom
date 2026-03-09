package com.babybloom.data.repository

import com.babybloom.data.local.dao.AiInsightDao
import com.babybloom.data.local.entity.AiInsightEntity
import com.babybloom.domain.model.AiInsight
import com.babybloom.domain.repository.AiInsightRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class AiInsightRepositoryImpl @Inject constructor(
    private val dao: AiInsightDao
) : AiInsightRepository {

    override fun getLatestInsight(childId: Long): Flow<AiInsight?> =
        dao.getLatestInsight(childId).map { it?.toDomain() }

    override suspend fun saveInsight(insight: AiInsight) =
        dao.insertInsight(insight.toEntity())

    override suspend fun deleteInsightsForChild(childId: Long) =
        dao.deleteInsightsForChild(childId)

    // ── Mappers ────────────────────────────────────────────────────────
    private fun AiInsightEntity.toDomain() = AiInsight(
        id          = id,
        childId     = childId,
        insightText = insightText,
        generatedAt = generatedAt
    )

    private fun AiInsight.toEntity() = AiInsightEntity(
        id          = id,
        childId     = childId,
        insightText = insightText,
        generatedAt = generatedAt
    )
}