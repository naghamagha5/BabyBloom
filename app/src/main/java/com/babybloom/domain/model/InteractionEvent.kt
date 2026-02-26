package com.babybloom.domain.model

data class InteractionEvent(
    val id: Long = 0,
    val sessionId: Long,
    val childId: Long,
    val activityId: String,
    val eventType: String,  // "SPEECH", "TOUCH", "GAZE"
    val eventData: String,  // JSON string
    val timestamp: Long = System.currentTimeMillis()
)