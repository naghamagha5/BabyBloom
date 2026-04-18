package com.babybloom.data.local.dao

import androidx.room.*
import com.babybloom.data.local.entity.LearningContentEntity

@Dao
interface LearningContentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(content: List<LearningContentEntity>): List<Long>

    @Query("SELECT * FROM learning_content WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): LearningContentEntity?

    @Query("SELECT * FROM learning_content WHERE category = :category")
    suspend fun getByCategory(category: String): List<LearningContentEntity>

    @Query("SELECT COUNT(*) FROM learning_content")
    suspend fun count(): Int
}