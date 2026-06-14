package com.babybloom.data.local.dao

import androidx.room.*
import com.babybloom.data.local.entity.AttentionScoreRow
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

    @Query("SELECT * FROM sessions WHERE childId = :childId ORDER BY startTime DESC")
    suspend fun getAllSessions(childId: Long): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SessionEntity?

    @Query("SELECT COUNT(*) FROM sessions WHERE childId = :childId")
    fun countByChild(childId: Long): Flow<Int>

    @Query("""
    SELECT startTime, attentionScore 
    FROM sessions 
    WHERE childId = :childId 
    ORDER BY startTime ASC
""")
    suspend fun getAttentionScoresForChart(childId: Long): List<AttentionScoreRow>

    @Query("UPDATE sessions SET endTime = :endTime WHERE id = :sessionId")
    suspend fun endSession(sessionId: Long, endTime: Long)
}
