package com.babybloom.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "activity_content",
    primaryKeys = ["activityId", "orderIndex"],
    foreignKeys = [
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["activityId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = LearningContentEntity::class,
            parentColumns = ["id"],
            childColumns = ["contentId"],
            onDelete = ForeignKey.RESTRICT   // safer than CASCADE for shared content
        )
    ],
    indices = [
        Index("contentId"),
        Index(value = ["activityId", "orderIndex"]) // fast ordered load
    ]
)
data class ActivityContentEntity(
    val activityId: String,
    val orderIndex: Int,            // 0..N defines sequence + allows duplicates
    val contentId: String
)