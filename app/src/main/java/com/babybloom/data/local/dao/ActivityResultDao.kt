package com.babybloom.data.local.dao

import androidx.room.*
import com.babybloom.data.local.entity.ActivityResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityResultDao {

    @Insert
    suspend fun insert(result: ActivityResultEntity): Long

    @Query("SELECT * FROM activity_results WHERE sessionId = :sessionId")
    suspend fun getBySession(sessionId: Long): List<ActivityResultEntity>

    // All results for a child — used by personalization engine
    @Query("""
        SELECT * FROM activity_results 
        WHERE childId = :childId 
        ORDER BY timestamp DESC
    """)
    suspend fun getByChild(childId: Long): List<ActivityResultEntity>

    // Recent results per skill area — used for difficulty adaptation
    @Query("""
        SELECT ar.* FROM activity_results ar
        INNER JOIN activities a ON ar.activityId = a.id
        WHERE ar.childId = :childId AND a.skillArea = :skillArea
        ORDER BY ar.timestamp DESC
        LIMIT :limit
    """)
    suspend fun getRecentBySkillArea(
        childId: Long,
        skillArea: String,
        limit: Int = 10
    ): List<ActivityResultEntity>

    // Recent results per modality — used for modality scoring
    @Query("""
        SELECT ar.* FROM activity_results ar
        INNER JOIN activities a ON ar.activityId = a.id
        WHERE ar.childId = :childId AND a.modality = :modality
        ORDER BY ar.timestamp DESC
        LIMIT :limit
    """)
    suspend fun getRecentByModality(
        childId: Long,
        modality: String,
        limit: Int = 10
    ): List<ActivityResultEntity>

    // Flow version for the dashboard
    @Query("""
        SELECT * FROM activity_results 
        WHERE childId = :childId 
        ORDER BY timestamp DESC
    """)
    fun observeByChild(childId: Long): Flow<List<ActivityResultEntity>>
}