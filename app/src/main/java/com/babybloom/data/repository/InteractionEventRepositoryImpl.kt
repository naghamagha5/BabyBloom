package com.babybloom.data.repository

import com.babybloom.data.local.dao.InteractionEventDao
import com.babybloom.data.local.entity.InteractionEventEntity
import com.babybloom.domain.model.InteractionEvent
import com.babybloom.domain.repository.InteractionEventRepository
import javax.inject.Inject

class InteractionEventRepositoryImpl @Inject constructor(
    private val interactionEventDao: InteractionEventDao
) : InteractionEventRepository {

    override suspend fun saveEvent(event: InteractionEvent): Long =
        interactionEventDao.insert(event.toEntity())

    override suspend fun getBySessionAndType(
        sessionId: Long,
        eventType: String
    ): List<InteractionEvent> =
        interactionEventDao.getBySessionAndType(sessionId, eventType)
            .map { it.toDomain() }

    override suspend fun getByChildAndActivity(
        childId: Long,
        activityId: String
    ): List<InteractionEvent> =
        interactionEventDao.getByChildAndActivity(childId, activityId)
            .map { it.toDomain() }
}

fun InteractionEventEntity.toDomain() = InteractionEvent(
    id, sessionId, childId, activityId, eventType, eventData, timestamp
)
fun InteractionEvent.toEntity() = InteractionEventEntity(
    id, sessionId, childId, activityId, eventType, eventData, timestamp
)