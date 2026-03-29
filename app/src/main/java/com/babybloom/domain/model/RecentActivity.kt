package com.babybloom.domain.model

data class RecentActivity(
    val name: String,
    val score: Float,
    val timeAgoLabel: String,   // pre-formatted Arabic string, e.g. "منذ 3 ساعات"
    val durationMs: Long
)