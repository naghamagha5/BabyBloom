package com.babybloom.domain.repository

import com.babybloom.domain.model.AppNotification
import com.babybloom.domain.model.NotificationType
import kotlinx.coroutines.flow.Flow

interface AppNotificationRepository {
    fun observeByUser(userId: Long): Flow<List<AppNotification>>
    suspend fun create(notification: AppNotification): Long
    suspend fun markAsRead(id: Long)
    suspend fun markAllAsReadForUser(userId: Long)
    suspend fun hasUnreadTypeForChild(userId: Long, childId: Long, type: NotificationType): Boolean
    suspend fun deleteTypeForChild(userId: Long, childId: Long, type: NotificationType)
}
