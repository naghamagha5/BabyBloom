package com.babybloom.data.local.dao

import androidx.room.*
import com.babybloom.data.local.entity.LevelMasteryEntity

@Dao
interface LevelMasteryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LevelMasteryEntity)

    @Query("""
        SELECT * FROM level_mastery 
        WHERE childId = :childId AND skillArea = :skillArea AND level = :level
    """)
    suspend fun get(childId: Long, skillArea: String, level: Int): LevelMasteryEntity?

    @Query("SELECT * FROM level_mastery WHERE childId = :childId AND skillArea = :skillArea")
    suspend fun getForSkill(childId: Long, skillArea: String): List<LevelMasteryEntity>

    @Query("SELECT * FROM level_mastery WHERE childId = :childId")
    suspend fun getAllForChild(childId: Long): List<LevelMasteryEntity>

    @Query("""
        UPDATE level_mastery 
        SET masteredCount = masteredCount + 1, lastUpdated = :now 
        WHERE childId = :childId AND skillArea = :skillArea AND level = :level
    """)
    suspend fun incrementMastered(
        childId: Long,
        skillArea: String,
        level: Int,
        now: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM level_mastery WHERE childId = :childId")
    suspend fun deleteAllForChild(childId: Long)
}