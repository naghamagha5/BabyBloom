package com.babybloom.data.local.dao

import androidx.room.*
import com.babybloom.data.local.entity.ChildEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChildDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(child: ChildEntity): Long

    @Update
    suspend fun update(child: ChildEntity)

    @Delete
    suspend fun delete(child: ChildEntity)

    @Query("SELECT * FROM children WHERE userId = :userId ORDER BY createdAt ASC")
    fun getChildrenByUser(userId: Long): Flow<List<ChildEntity>>

    @Query("SELECT * FROM children WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ChildEntity?

    @Query("SELECT COUNT(*) FROM children")
    suspend fun countAll(): Int

    @Query("SELECT * FROM children WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<ChildEntity?>
}