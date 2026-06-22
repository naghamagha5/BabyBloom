package com.babybloom.data.repository

import com.babybloom.data.local.dao.AppNotificationDao
import com.babybloom.data.local.entity.AppNotificationEntity
import com.babybloom.domain.model.AppNotification
import com.babybloom.domain.model.NotificationType
import com.babybloom.domain.repository.AppNotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class AppNotificationRepositoryImpl @Inject constructor(
    private val dao: AppNotificationDao
) : AppNotificationRepository {

    override fun observeByUser(userId: Long): Flow<List<AppNotification>> =
        dao.observeByUser(userId).map { list -> list.map { it.toDomain() } }

    override suspend fun create(notification: AppNotification): Long =
        dao.insert(notification.toEntity())

    override suspend fun markAsRead(id: Long) =
        dao.markAsRead(id, System.currentTimeMillis())

    override suspend fun markAllAsReadForUser(userId: Long) =
        dao.markAllAsReadForUser(userId, System.currentTimeMillis())

    override suspend fun hasUnreadTypeForChild(userId: Long, childId: Long, type: NotificationType): Boolean =
        dao.hasUnreadTypeForChild(userId, childId, type.name)

    override suspend fun deleteTypeForChild(userId: Long, childId: Long, type: NotificationType) =
        dao.deleteTypeForChild(userId, childId, type.name)
}

private fun AppNotification.toEntity() = AppNotificationEntity(
    id = id,
    userId = userId,
    childId = childId,
    type = type.name,
    title = title,
    message = message,
    createdAt = createdAt,
    readAt = readAt,
    eventKey = eventKey,
    destinationTab = destinationTab
)

private fun AppNotificationEntity.toDomain() = AppNotification(
    id = id,
    userId = userId,
    childId = childId,
    type = NotificationType.valueOf(type),
    title = title,
    message = message,
    createdAt = createdAt,
    readAt = readAt,
    eventKey = eventKey,
    destinationTab = destinationTab
)
