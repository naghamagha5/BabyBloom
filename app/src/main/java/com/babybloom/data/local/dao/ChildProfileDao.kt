package com.babybloom.data.local.dao

import androidx.room.*
import com.babybloom.data.local.entity.ChildProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChildProfileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: ChildProfileEntity)

    @Update
    suspend fun update(profile: ChildProfileEntity)

    @Query("SELECT * FROM child_profiles WHERE childId = :childId LIMIT 1")
    suspend fun getByChildId(childId: Long): ChildProfileEntity?

    // Flow version — dashboard auto-refreshes when profile updates
    @Query("SELECT * FROM child_profiles WHERE childId = :childId LIMIT 1")
    fun observeByChildId(childId: Long): Flow<ChildProfileEntity?>
}