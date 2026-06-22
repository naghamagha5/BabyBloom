package com.babybloom.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "assessment_results")
data class AssessmentResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val childId: Long,
    val initialLanguageLevel: Int,
    val initialNumeracyLevel: Int,
    val initialMotorLevel: Int,
    val dominantModality: String,
    val completedAt: Long = System.currentTimeMillis()
)