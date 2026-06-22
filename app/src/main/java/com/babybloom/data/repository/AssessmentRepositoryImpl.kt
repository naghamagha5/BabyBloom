package com.babybloom.data.repository

import com.babybloom.data.local.dao.AssessmentResultDao
import com.babybloom.data.local.entity.AssessmentResultEntity
import com.babybloom.domain.repository.AssessmentRepository
import javax.inject.Inject

class AssessmentRepositoryImpl @Inject constructor(
    private val dao: AssessmentResultDao
) : AssessmentRepository {

    override suspend fun save(result: AssessmentResultEntity): Long =
        dao.insert(result)

    override suspend fun getLatestForChild(childId: Long): AssessmentResultEntity? =
        dao.getLatestForChild(childId)

    override suspend fun hasAssessment(childId: Long): Boolean =
        dao.hasAssessment(childId)

    override suspend fun deleteForChild(childId: Long) =
        dao.deleteForChild(childId)
}