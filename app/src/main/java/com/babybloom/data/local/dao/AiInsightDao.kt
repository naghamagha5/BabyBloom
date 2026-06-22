package com.babybloom.data.local.dao

import androidx.room.*
import com.babybloom.data.local.entity.AiInsightEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AiInsightDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AiInsightEntity): Long

    @Query("""
        SELECT * FROM ai_insights 
        WHERE childId = :childId 
        ORDER BY generatedAt DESC 
        LIMIT 1
    """)
    fun observeLatestForChild(childId: Long): Flow<AiInsightEntity?>

    @Query("""
        SELECT * FROM ai_insights 
        WHERE childId = :childId 
        ORDER BY generatedAt DESC 
        LIMIT 1
    """)
    suspend fun getLatestForChild(childId: Long): AiInsightEntity?

    @Query("""
        SELECT * FROM ai_insights 
        WHERE childId = :childId 
        ORDER BY generatedAt DESC
    """)
    suspend fun getAllForChild(childId: Long): List<AiInsightEntity>

    @Query("""
        DELETE FROM ai_insights 
        WHERE childId = :childId 
        AND id NOT IN (
            SELECT id FROM ai_insights 
            WHERE childId = :childId 
            ORDER BY generatedAt DESC 
            LIMIT :keepLatest
        )
    """)
    suspend fun deleteOldForChild(childId: Long, keepLatest: Int)

    @Query("DELETE FROM ai_insights WHERE childId = :childId")
    suspend fun deleteByChildId(childId: Long)
}