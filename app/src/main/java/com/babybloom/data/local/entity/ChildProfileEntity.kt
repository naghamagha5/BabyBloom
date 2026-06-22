package com.babybloom.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "child_profiles",
    foreignKeys = [ForeignKey(
        entity = ChildEntity::class,
        parentColumns = ["id"],
        childColumns = ["childId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class ChildProfileEntity(
    @PrimaryKey
    val childId: Long,

    // Modality scores
    val visualScore: Float = 0.5f,
    val audioScore: Float = 0.5f,
    val gameScore: Float = 0.5f,
    val visualPreferencePercent: Float = 33.34f,
    val audioPreferencePercent: Float = 33.33f,
    val interactivePreferencePercent: Float = 33.33f,

    // Skill levels 1–5
    val languageLevel: Int = 1,
    val numeracyLevel: Int = 1,
    val motorLevel: Int = 1,

    // Progress within current level (shown in child profile progress bar)
    val languageProgress: Float = 0f,
    val numeracyProgress: Float = 0f,
    val motorProgress: Float = 0f,

    // Computed values
    val dominantModality: String = "VISUAL",
    val weakSkillAreas: String = "",

    val totalSessionCount: Int = 0,
    val totalActivitiesCompleted: Int = 0,
    val overallProgressPercent: Float = 0f,
    val assessmentCompleted: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
)

// ── Mappers ───────────────────────────────────────────────────────────────────

fun ChildProfileEntity.toDomain() = com.babybloom.domain.model.ChildProfile(
    childId                  = childId,
    visualScore              = visualScore,
    audioScore               = audioScore,
    gameScore                = gameScore,
    visualPreferencePercent  = visualPreferencePercent,
    audioPreferencePercent   = audioPreferencePercent,
    interactivePreferencePercent = interactivePreferencePercent,
    languageLevel            = languageLevel,
    numeracyLevel            = numeracyLevel,
    motorLevel               = motorLevel,
    languageProgress         = languageProgress,
    numeracyProgress         = numeracyProgress,
    motorProgress            = motorProgress,
    dominantModality         = dominantModality,
    weakSkillAreas           = weakSkillAreas,
    totalSessionCount        = totalSessionCount,
    totalActivitiesCompleted = totalActivitiesCompleted,
    overallProgressPercent   = overallProgressPercent,
    assessmentCompleted      = assessmentCompleted,
    lastUpdated              = lastUpdated
)

fun com.babybloom.domain.model.ChildProfile.toEntity() = ChildProfileEntity(
    childId                  = childId,
    visualScore              = visualScore,
    audioScore               = audioScore,
    gameScore                = gameScore,
    visualPreferencePercent  = visualPreferencePercent,
    audioPreferencePercent   = audioPreferencePercent,
    interactivePreferencePercent = interactivePreferencePercent,
    languageLevel            = languageLevel,
    numeracyLevel            = numeracyLevel,
    motorLevel               = motorLevel,
    languageProgress         = languageProgress,
    numeracyProgress         = numeracyProgress,
    motorProgress            = motorProgress,
    dominantModality         = dominantModality,
    weakSkillAreas           = weakSkillAreas,
    totalSessionCount        = totalSessionCount,
    totalActivitiesCompleted = totalActivitiesCompleted,
    overallProgressPercent   = overallProgressPercent,
    assessmentCompleted      = assessmentCompleted,
    lastUpdated              = lastUpdated
)
