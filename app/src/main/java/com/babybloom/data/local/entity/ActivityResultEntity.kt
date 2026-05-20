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
    val contentId: String,
    val score: Float,
    val duration: Long,
    val correctCount: Int,
    val incorrectCount: Int,
    val attempts: Int = 1,
    val speechConfidence: Float? = null,
    val motorSkillScore: Float? = null,
    val choiceConfidenceScore: Float? = null,
    val attentionScore: Float? = null,
    val timestamp: Long = System.currentTimeMillis()
)
