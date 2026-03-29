package com.babybloom.data.local.entity

data class ActivityResultWithTitle(
    val activityTitle: String,
    val score: Float,
    val timestamp: Long,
    val duration: Long
)