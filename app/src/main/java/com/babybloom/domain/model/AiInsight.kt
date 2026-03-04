package com.babybloom.domain.model

data class AiInsight(
    val id: Long = 0,
    val childId: Long,
    val insightText: String,
    val generatedAt: Long
)