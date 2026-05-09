package com.babybloom.domain.model

data class ChildInsight(
    val learningStyle: String = "",
    val strengths: String = "",
    val developmentAreas: String = "",
    val tip1Title: String = "",
    val tip1Detail: String = "",
    val tip2Title: String = "",
    val tip2Detail: String = "",
    val tip3Title: String = "",
    val tip3Detail: String = "",
    val guidanceIntro: String = "",
    val recommended: List<String> = emptyList(),
    val avoid: List<String> = emptyList()
) {
    val isEmpty: Boolean get() = learningStyle.isEmpty() && strengths.isEmpty()
}