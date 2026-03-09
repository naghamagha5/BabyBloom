package com.babybloom.domain.model

data class WeeklyChartData(
    val languageScores: List<Float>  = List(6) { 0f },
    val numeracyScores: List<Float>  = List(6) { 0f },
    val motorScores: List<Float>     = List(6) { 0f },
    val attentionScores: List<Float> = List(6) { 0f }
)