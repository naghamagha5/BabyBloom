package com.babybloom.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.babybloom.data.local.entity.ChildProfileSnapshotEntity

@Dao
interface ChildProfileSnapshotDao {
    @Insert
    suspend fun insert(snapshot: ChildProfileSnapshotEntity)

    @Query("SELECT * FROM child_profile_snapshots WHERE childId = :childId ORDER BY capturedAt ASC")
    suspend fun getForChild(childId: Long): List<ChildProfileSnapshotEntity>
}
