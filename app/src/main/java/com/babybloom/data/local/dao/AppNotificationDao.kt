package com.babybloom.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.babybloom.data.local.entity.AppNotificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppNotificationDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: AppNotificationEntity): Long

    @Query("""
        SELECT * FROM app_notifications
        WHERE userId = :userId
        ORDER BY createdAt DESC
    """)
    fun observeByUser(userId: Long): Flow<List<AppNotificationEntity>>

    @Query("""
        UPDATE app_notifications
        SET readAt = :readAt
        WHERE id = :id AND readAt IS NULL
    """)
    suspend fun markAsRead(id: Long, readAt: Long)

    @Query("""
        UPDATE app_notifications
        SET readAt = :readAt
        WHERE userId = :userId AND readAt IS NULL
    """)
    suspend fun markAllAsReadForUser(userId: Long, readAt: Long)

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM app_notifications
            WHERE userId = :userId
              AND childId = :childId
              AND type = :type
              AND readAt IS NULL
        )
    """)
    suspend fun hasUnreadTypeForChild(userId: Long, childId: Long, type: String): Boolean

    @Query("""
        DELETE FROM app_notifications
        WHERE userId = :userId
          AND childId = :childId
          AND type = :type
    """)
    suspend fun deleteTypeForChild(userId: Long, childId: Long, type: String)
}
