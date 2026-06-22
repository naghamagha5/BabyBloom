package com.babybloom.domain.model

data class ActivityResult(
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
    val touchQualityScore: Float? = null,
    val attentionScore: Float? = null,
    val timestamp: Long = System.currentTimeMillis()
)
