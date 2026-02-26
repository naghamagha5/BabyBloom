package com.babybloom.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "learning_content")
data class LearningContentEntity(
    @PrimaryKey
    val id: String,                   // e.g. "visual_story_001",
    val labelAr: String,              // Arabic text label e.g. "قطة"
    val code: String,                 // representational code for the arabic language
    val category: String,             // "ANIMAL", "NUMBER", "SHAPE", "COLOR"
    val imagePath: String = "",       // asset path e.g. "activities/visual/cat.webp"
    val audioPath: String = "",       // asset path e.g. "activities/audio/cat.ogg"
    val difficultyLevel: Int = 1
)