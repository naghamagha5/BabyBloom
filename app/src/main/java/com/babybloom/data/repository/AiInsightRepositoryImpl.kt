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
        dao.observeLatestForChild(childId).map { it?.toDomain() }

    override suspend fun saveInsight(insight: AiInsight) {
        dao.insert(insight.toEntity())
    }

    override suspend fun deleteInsightsForChild(childId: Long) {
        dao.deleteByChildId(childId)
    }

    override suspend fun save(insight: AiInsight): Long =
        dao.insert(insight.toEntity())

    override suspend fun getLatestForChild(childId: Long): AiInsight? =
        dao.getLatestForChild(childId)?.toDomain()

    override suspend fun getAllForChild(childId: Long): List<AiInsight> =
        dao.getAllForChild(childId).map { it.toDomain() }

    override suspend fun deleteOldForChild(childId: Long, keepLatest: Int) =
        dao.deleteOldForChild(childId, keepLatest)
}

// ── Mappers ───────────────────────────────────────────────────────────────────

private fun AiInsight.toEntity() = AiInsightEntity(
    id          = id,
    childId     = childId,
    insightText = insightText,
    generatedAt = generatedAt
)

private fun AiInsightEntity.toDomain() = AiInsight(
    id          = id,
    childId     = childId,
    insightText = insightText,
    generatedAt = generatedAt
)
