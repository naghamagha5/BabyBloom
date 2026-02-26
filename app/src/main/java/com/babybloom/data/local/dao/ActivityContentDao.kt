package com.babybloom.data.local.dao

import androidx.room.*
import com.babybloom.data.local.entity.ActivityContentEntity
import com.babybloom.data.local.entity.LearningContentEntity

@Dao
interface ActivityContentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(links: List<ActivityContentEntity>)

    // Get all LearningContent for a specific activity, ordered by orderIndex
    @Query("""
        SELECT lc.* FROM learning_content lc
        INNER JOIN activity_content ac ON lc.id = ac.contentId
        WHERE ac.activityId = :activityId
        ORDER BY ac.orderIndex ASC
    """)
    suspend fun getContentForActivity(activityId: String): List<LearningContentEntity>
}