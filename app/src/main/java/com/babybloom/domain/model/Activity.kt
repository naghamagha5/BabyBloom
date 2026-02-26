package com.babybloom.domain.model

data class Activity(
    val id: String,
    val title: String,
    val description: String = "",
    val modality: String,
    val skillArea: String,
    val difficultyLevel: Int,
    val activityType: String,
    val isActive: Boolean = true,
    val configJson: String = ""
)

