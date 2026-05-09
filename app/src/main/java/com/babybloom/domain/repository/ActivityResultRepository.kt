package com.babybloom.domain.repository

import com.babybloom.data.local.entity.SkillScoreRow
import com.babybloom.domain.model.ActivityResult
import com.babybloom.domain.model.RecentActivity
import kotlinx.coroutines.flow.Flow

interface ActivityResultRepository {
    suspend fun saveResult(result: ActivityResult): Long
    suspend fun getBySession(sessionId: Long): List<ActivityResult>
    suspend fun getByChild(childId: Long): List<ActivityResult>
    suspend fun getRecentBySkillArea(
        childId: Long,
        skillArea: String,
        limit: Int = 10
    ): List<ActivityResult>
    suspend fun getRecentByModality(
        childId: Long,
        modality: String,
        limit: Int = 10
    ): List<ActivityResult>
    fun observeByChild(childId: Long): Flow<List<ActivityResult>>
    suspend fun getRecentActivities(childId: Long, limit: Int = 3): List<RecentActivity>
    suspend fun getSkillScoresForChart(childId: Long): List<SkillScoreRow>

    // New — used by personalization algorithm to get multimodal signals
    suspend fun getResultsWithAttention(
        childId: Long,
        limit: Int = 20
    ): List<ActivityResult>
    suspend fun getResultsWithSpeech(
        childId: Long,
        limit: Int = 20
    ): List<ActivityResult>
    suspend fun getResultsWithTouch(
        childId: Long,
        limit: Int = 20
    ): List<ActivityResult>

    suspend fun getForSession(sessionId: Long): List<ActivityResult>
}