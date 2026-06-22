package com.babybloom.domain.model

data class AiInsightPayload(
    val structuredJson: String,
    val naturalLanguagePrompt: String
)