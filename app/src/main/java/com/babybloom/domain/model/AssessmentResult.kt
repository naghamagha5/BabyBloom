package com.babybloom.domain.model

enum class Confidence {
    CONFIRMED,
    PARTIAL,
    SKIPPED
}

enum class Modality {
    AUDIO,
    VISUAL,
    INTERACTIVE
}

data class CategoryAssessment(
    val level: Int,
    val confidence: Confidence
)

data class AssessmentResult(
    val globalLevel: Int,
    val categoryLevels: Map<String, CategoryAssessment>,
    val modalityScores: Map<Modality, Float>,
    val dominantModality: Modality,
    val sessionDurationMs: Long,
    val completionRate: Float,
    val sessionItemCount: Int,
    val hitItemCap: Boolean
)
