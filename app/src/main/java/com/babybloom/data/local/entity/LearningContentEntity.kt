package com.babybloom.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "learning_content")
data class LearningContentEntity(
    @PrimaryKey
    val id: String,                   // e.g. "visual_story_001",
    val labelAr: String,              // Arabic text label e.g. "قطة"
    val category: String,             // "ANIMAL", "NUMBER", "SHAPE", "COLOR"
    val difficultyLevel: Int = 1,
    val learningOrder: Int = 1,
)