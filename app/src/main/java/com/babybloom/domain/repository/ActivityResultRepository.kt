package com.babybloom.domain.repository

import com.babybloom.domain.model.ActivityResult
import kotlinx.coroutines.flow.Flow

interface ActivityResultRepository {
    suspend fun saveResult(result: ActivityResult): Long
    suspend fun getBySession(sessionId: Long): List<ActivityResult>
    suspend fun getByChild(childId: Long): List<ActivityResult>
    suspend fun getRecentBySkillArea(childId: Long, skillArea: String, limit: Int): List<ActivityResult>
    suspend fun getRecentByModality(childId: Long, modality: String, limit: Int): List<ActivityResult>
    fun observeByChild(childId: Long): Flow<List<ActivityResult>>
}