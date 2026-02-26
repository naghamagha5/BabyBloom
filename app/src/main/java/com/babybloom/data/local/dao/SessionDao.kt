package com.babybloom.data.local.dao

import androidx.room.*
import com.babybloom.data.local.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Update
    suspend fun update(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE childId = :childId ORDER BY startTime DESC")
    fun getSessionsByChild(childId: Long): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE childId = :childId ORDER BY startTime DESC LIMIT :limit")
    suspend fun getRecentSessions(childId: Long, limit: Int): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SessionEntity?
}