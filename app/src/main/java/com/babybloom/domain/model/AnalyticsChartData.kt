package com.babybloom.domain.model

data class ChartResolutionData(
    val periodLabels: List<String>    = emptyList(),
    val languageScores: List<Float?>  = emptyList(),
    val numeracyScores: List<Float?>  = emptyList(),
    val motorScores: List<Float?>     = emptyList()
)

data class AnalyticsChartData(
    val weekly: ChartResolutionData = ChartResolutionData(),
    val daily: ChartResolutionData = ChartResolutionData()
)
