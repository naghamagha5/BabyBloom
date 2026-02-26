package com.babybloom.domain.model

data class ActivityResult(
    val id: Long = 0,
    val sessionId: Long,
    val childId: Long,
    val activityId: String,
    val score: Float,
    val duration: Long,
    val correctCount: Int,
    val incorrectCount: Int,
    val timestamp: Long = System.currentTimeMillis()
)

