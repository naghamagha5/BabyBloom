package com.babybloom.data.local.dao

import androidx.room.*
import com.babybloom.data.local.entity.AiInsightEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AiInsightDao {

    @Query("SELECT * FROM ai_insights WHERE childId = :childId ORDER BY generatedAt DESC LIMIT 1")
    fun getLatestInsight(childId: Long): Flow<AiInsightEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInsight(insight: AiInsightEntity)

    @Query("DELETE FROM ai_insights WHERE childId = :childId")
    suspend fun deleteInsightsForChild(childId: Long)
}