package com.babybloom.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "activity_results",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ChildEntity::class,
            parentColumns = ["id"],
            childColumns = ["childId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["activityId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId"), Index("childId"), Index("activityId")]
)
data class ActivityResultEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val childId: Long,
    val activityId: String,
    val contentId: String,           // which content item this result is for
    val score: Float,                // 0.0 to 1.0
    val duration: Long,              // milliseconds
    val correctCount: Int,
    val incorrectCount: Int,
    val attempts: Int = 1,           // total attempts before correct/give-up
    val speechConfidence: Float? = null,   // 0.0–1.0, null if not a speech activity
    val touchComplexity: Float? = null,    // 0.0–1.0, null if no touch tracking
    val attentionScore: Float? = null,     // 0.0–1.0, average over activity duration
    val timestamp: Long = System.currentTimeMillis()
)