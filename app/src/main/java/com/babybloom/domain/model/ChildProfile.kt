package com.babybloom.domain.model

data class ChildProfile(
    val childId: Long,
    val visualScore: Float = 0.5f,
    val audioScore: Float = 0.5f,
    val gameScore: Float = 0.5f,
    val languageLevel: Int = 1,
    val numeracyLevel: Int = 1,
    val motorLevel: Int = 1,
    val totalSessionCount: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)