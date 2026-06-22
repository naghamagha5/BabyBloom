package com.babybloom.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "app_notifications",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ChildEntity::class,
            parentColumns = ["id"],
            childColumns = ["childId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("userId"),
        Index("childId"),
        Index(value = ["eventKey"], unique = true),
        Index(value = ["userId", "createdAt"])
    ]
)
data class AppNotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: Long,
    val childId: Long? = null,
    val type: String,
    val title: String,
    val message: String,
    val createdAt: Long = System.currentTimeMillis(),
    val readAt: Long? = null,
    val eventKey: String,
    val destinationTab: Int = 0
)
