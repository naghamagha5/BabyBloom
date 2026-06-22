package com.babybloom.data.local.dao

import androidx.room.*
import com.babybloom.data.local.entity.ActivityRecommendationEntity

@Dao
interface ActivityRecommendationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(recommendations: List<ActivityRecommendationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recommendation: ActivityRecommendationEntity): Long

    @Query("""
        SELECT * FROM activity_recommendations 
        WHERE childId = :childId AND wasPlayed = 0 
        ORDER BY recommendedAt DESC
    """)
    suspend fun getPendingForChild(childId: Long): List<ActivityRecommendationEntity>

    @Query("""
        SELECT * FROM activity_recommendations 
        WHERE childId = :childId AND sessionId = :sessionId 
        ORDER BY recommendedAt ASC
    """)
    suspend fun getForSession(childId: Long, sessionId: Long): List<ActivityRecommendationEntity>

    @Query("""
        SELECT * FROM activity_recommendations 
        WHERE childId = :childId 
        ORDER BY recommendedAt DESC 
        LIMIT :limit
    """)
    suspend fun getRecentForChild(childId: Long, limit: Int = 20): List<ActivityRecommendationEntity>

    @Query("""
        UPDATE activity_recommendations 
        SET wasPlayed = 1, outcomeScore = :score 
        WHERE id = :id
    """)
    suspend fun markAsPlayed(id: Long, score: Float)

    @Query("""
        UPDATE activity_recommendations 
        SET sessionId = :sessionId 
        WHERE childId = :childId AND wasPlayed = 0
    """)
    suspend fun assignSession(childId: Long, sessionId: Long)

    @Query("""
        DELETE FROM activity_recommendations 
        WHERE childId = :childId AND wasPlayed = 1 AND recommendedAt < :before
    """)
    suspend fun deleteOldPlayed(childId: Long, before: Long)

    @Query("DELETE FROM activity_recommendations WHERE childId = :childId AND wasPlayed = 0")
    suspend fun clearPendingForChild(childId: Long)
}