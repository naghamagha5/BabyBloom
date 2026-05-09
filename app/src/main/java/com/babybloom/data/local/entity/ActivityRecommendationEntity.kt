package com.babybloom.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "activity_recommendations")
data class ActivityRecommendationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val childId: Long,
    val activityId: String,
    val sessionId: Long,
    val reason: String,              // "WEAK_SKILL", "REINFORCE", "LEVEL_UP", "ASSESSMENT"
    val recommendedAt: Long = System.currentTimeMillis(),
    val wasPlayed: Boolean = false,
    val outcomeScore: Float? = null  // filled after completion
)