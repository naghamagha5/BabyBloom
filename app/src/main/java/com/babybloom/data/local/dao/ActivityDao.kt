package com.babybloom.data.local.dao

import androidx.room.*
import com.babybloom.data.local.entity.ActivityEntity

@Dao
interface ActivityDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(activities: List<ActivityEntity>)

    @Query("SELECT * FROM activities WHERE isActive = 1")
    suspend fun getAll(): List<ActivityEntity>

    @Query("""
        SELECT * FROM activities 
        WHERE modality = :modality 
        AND skillArea = :skillArea 
        AND difficultyLevel = :difficulty
        AND isActive = 1
    """)
    suspend fun getFiltered(
        modality: String,
        skillArea: String,
        difficulty: Int
    ): List<ActivityEntity>

    @Query("SELECT * FROM activities WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ActivityEntity?

    @Query("SELECT COUNT(*) FROM activities")
    suspend fun count(): Int
}