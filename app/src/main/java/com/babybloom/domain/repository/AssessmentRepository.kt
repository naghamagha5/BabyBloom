package com.babybloom.domain.repository

import com.babybloom.data.local.entity.AssessmentResultEntity

interface AssessmentRepository {
    suspend fun save(result: AssessmentResultEntity): Long
    suspend fun getLatestForChild(childId: Long): AssessmentResultEntity?
    suspend fun hasAssessment(childId: Long): Boolean
    suspend fun deleteForChild(childId: Long)
}