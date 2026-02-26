package com.babybloom.data.local.dao

import androidx.room.*
import com.babybloom.data.local.entity.InteractionEventEntity

@Dao
interface InteractionEventDao {

    @Insert
    suspend fun insert(event: InteractionEventEntity): Long

    @Query("""
        SELECT * FROM interaction_events 
        WHERE sessionId = :sessionId AND eventType = :eventType
    """)
    suspend fun getBySessionAndType(
        sessionId: Long,
        eventType: String
    ): List<InteractionEventEntity>

    @Query("""
        SELECT * FROM interaction_events
        WHERE childId = :childId AND activityId = :activityId
        ORDER BY timestamp DESC
    """)
    suspend fun getByChildAndActivity(
        childId: Long,
        activityId: String
    ): List<InteractionEventEntity>
}