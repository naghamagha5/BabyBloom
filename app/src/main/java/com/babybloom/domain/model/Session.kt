package com.babybloom.domain.model

data class Session(
    val id: Long = 0,
    val userId: Long,
    val childId: Long,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val isAssessment: Boolean = false,
    val attentionScore: Float = 0f
)