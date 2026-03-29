package com.babybloom.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "children",
    foreignKeys = [ForeignKey(
        entity = UserEntity::class,
        parentColumns = ["id"],
        childColumns = ["userId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("userId")]
)
data class ChildEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: Long,
    val name: String,
    val age: Int,
    val notes: String = "",
    val avatar: String = "",          // asset path e.g. "avatars/bear.webp"
    val musicEnabled: Boolean = true,
    val reducedAnimation: Boolean = false,
    val uiTheme: Boolean = false,  // "false" for "ACTIVE" and "true" for "CALM"
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "ACTIVE",
    val sessionDurationMinutes: Int = 10,
    val backgroundMusicEnabled: Boolean = true
)
