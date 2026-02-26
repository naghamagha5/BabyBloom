package com.babybloom.domain.repository

import com.babybloom.domain.model.InteractionEvent

interface InteractionEventRepository {
    suspend fun saveEvent(event: InteractionEvent): Long
    suspend fun getBySessionAndType(
        sessionId: Long,
        eventType: String
    ): List<InteractionEvent>
    suspend fun getByChildAndActivity(
        childId: Long,
        activityId: String
    ): List<InteractionEvent>
}