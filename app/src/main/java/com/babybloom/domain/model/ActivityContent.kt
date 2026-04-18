package com.babybloom.domain.model

data class ActivityContent(
    val activityId: String,
    val contentId: String,
    val orderIndex: Int = 0,
    val labelAr: String = "",
    val category: String = "",
    val learningOrder: Int = 0
)