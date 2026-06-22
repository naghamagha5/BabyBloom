package com.babybloom.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.babybloom.data.local.entity.LevelMasteryEntity

@Dao
interface LevelMasteryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LevelMasteryEntity)

    @Query(
        """
        SELECT * FROM level_mastery
        WHERE childId = :childId AND skillArea = :skillArea AND level = :level AND contentId = ''
        """
    )
    suspend fun get(childId: Long, skillArea: String, level: Int): LevelMasteryEntity?

    @Query("SELECT * FROM level_mastery WHERE childId = :childId AND skillArea = :skillArea AND contentId = ''")
    suspend fun getForSkill(childId: Long, skillArea: String): List<LevelMasteryEntity>

    @Query("SELECT * FROM level_mastery WHERE childId = :childId AND contentId = ''")
    suspend fun getAllForChild(childId: Long): List<LevelMasteryEntity>

    @Query("SELECT * FROM level_mastery WHERE childId = :childId AND contentId = :contentId LIMIT 1")
    suspend fun getByContentId(childId: Long, contentId: String): LevelMasteryEntity?

    @Query("SELECT * FROM level_mastery WHERE childId = :childId AND contentId != ''")
    suspend fun getContentScoresForChild(childId: Long): List<LevelMasteryEntity>

    @Query(
        """
        UPDATE level_mastery
        SET masteredCount = masteredCount + 1, lastUpdated = :now
        WHERE childId = :childId AND skillArea = :skillArea AND level = :level AND contentId = ''
        """
    )
    suspend fun incrementMastered(
        childId: Long,
        skillArea: String,
        level: Int,
        now: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM level_mastery WHERE childId = :childId")
    suspend fun deleteAllForChild(childId: Long)
}
