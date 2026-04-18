package com.babybloom.domain.model

import androidx.room.PrimaryKey

data class LearningContent(
    val id: String,
    val labelAr: String,
    val code: String,
    val category: String,
    val imagePath: String = "",
    val audioPath: String = "",
    val difficultyLevel: Int = 1
)
