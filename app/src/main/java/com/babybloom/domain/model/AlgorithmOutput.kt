package com.babybloom.domain.model

data class AlgorithmOutput(
    val updatedProfile: ChildProfile,
    val shouldRepeat: Boolean,
    val nextDifficultyLevel: Int,
    val nextSkillAreaSuggestion: String,
    val nextModalitySuggestion: String,
    val aiInsightPayload: AiInsightPayload
)