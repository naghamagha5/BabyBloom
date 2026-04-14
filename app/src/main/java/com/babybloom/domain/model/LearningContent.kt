package com.babybloom.domain.model

import androidx.room.PrimaryKey

data class LearningContent(
    val id: String,
    val labelAr: String,
    val category: String,
    val difficultyLevel: Int = 1,
    val learningOrder: Int = 1

)
