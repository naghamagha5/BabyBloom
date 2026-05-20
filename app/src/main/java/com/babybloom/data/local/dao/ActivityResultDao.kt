package com.babybloom.data.local.dao

import androidx.room.*
import com.babybloom.data.local.entity.ActivityResultEntity
import com.babybloom.data.local.entity.ActivityResultWithTitle
import com.babybloom.data.local.entity.SkillScoreRow
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityResultDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(result: ActivityResultEntity): Long

    @Query("""
        SELECT COALESCE(a.title, ar.activityId) AS activityTitle,
               ar.score,
               ar.timestamp,
               ar.duration
        FROM activity_results ar
        LEFT JOIN activities a ON ar.activityId = a.id
        WHERE ar.childId = :childId
        ORDER BY ar.timestamp DESC
        LIMIT :limit
    """)
    suspend fun getRecentWithTitle(
        childId: Long,
        limit: Int = 3
    ): List<ActivityResultWithTitle>

    @Query("SELECT * FROM activity_results WHERE sessionId = :sessionId")
    suspend fun getBySession(sessionId: Long): List<ActivityResultEntity>

    @Query("""
        SELECT * FROM activity_results 
        WHERE childId = :childId 
        ORDER BY timestamp DESC
    """)
    suspend fun getByChild(childId: Long): List<ActivityResultEntity>

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

    @Query("""
        SELECT * FROM activity_results 
        WHERE childId = :childId 
        ORDER BY timestamp DESC
    """)
    fun observeByChild(childId: Long): Flow<List<ActivityResultEntity>>

    @Query("""
        SELECT ar.timestamp, ar.score, a.skillArea
        FROM activity_results ar
        LEFT JOIN activities a ON ar.activityId = a.id
        WHERE ar.childId = :childId
          AND a.skillArea IS NOT NULL
        ORDER BY ar.timestamp ASC
    """)
    suspend fun getSkillScoresForChart(childId: Long): List<SkillScoreRow>

    // Multimodal signal queries — used by personalization algorithm

    @Query("""
        SELECT * FROM activity_results 
        WHERE childId = :childId 
          AND attentionScore IS NOT NULL
        ORDER BY timestamp DESC
        LIMIT :limit
    """)
    suspend fun getResultsWithAttention(
        childId: Long,
        limit: Int = 20
    ): List<ActivityResultEntity>

    @Query("""
        SELECT * FROM activity_results 
        WHERE childId = :childId 
          AND speechConfidence IS NOT NULL
        ORDER BY timestamp DESC
        LIMIT :limit
    """)
    suspend fun getResultsWithSpeech(
        childId: Long,
        limit: Int = 20
    ): List<ActivityResultEntity>

    @Query("""
        SELECT * FROM activity_results 
        WHERE childId = :childId 
          AND (motorSkillScore IS NOT NULL OR choiceConfidenceScore IS NOT NULL)
        ORDER BY timestamp DESC
        LIMIT :limit
    """)
    suspend fun getResultsWithTouch(
        childId: Long,
        limit: Int = 20
    ): List<ActivityResultEntity>

    @Query("SELECT * FROM activity_results WHERE sessionId = :sessionId")
    suspend fun getForSession(sessionId: Long): List<ActivityResultEntity>
}
