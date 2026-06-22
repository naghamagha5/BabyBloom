package com.babybloom.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "activities")
data class ActivityEntity(
    @PrimaryKey
    val id: String,                   // e.g. "visual_story_001"
    val title: String,
    val description: String = "",
    val modality: String,             // "VISUAL", "AUDIO", "INTERACTIVE"
    val skillArea: String,            // "LANGUAGE", "NUMERACY", "MOTOR"
    val difficultyLevel: Int,         // 1, 2,  3,  4 or 5
    val activityType: String,         // "STORY", "MATCH", "TRACE", "COUNT","SPEECH" , "DRAG"
    val isActive: Boolean = true,
    val configJson: String = ""      // game config as JSON string

)