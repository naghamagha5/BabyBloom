package com.babybloom.data.local.dao

import androidx.room.*
import com.babybloom.data.local.entity.AssessmentResultEntity

@Dao
interface AssessmentResultDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(result: AssessmentResultEntity): Long

    @Query("""
        SELECT * FROM assessment_results 
        WHERE childId = :childId 
        ORDER BY completedAt DESC 
        LIMIT 1
    """)
    suspend fun getLatestForChild(childId: Long): AssessmentResultEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM assessment_results WHERE childId = :childId)")
    suspend fun hasAssessment(childId: Long): Boolean

    @Query("DELETE FROM assessment_results WHERE childId = :childId")
    suspend fun deleteForChild(childId: Long)
}