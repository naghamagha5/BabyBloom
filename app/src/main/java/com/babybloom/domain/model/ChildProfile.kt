package com.babybloom.domain.model

data class ChildProfile(
    val childId: Long,

    // Modality scores — 0.0 to 1.0 (EMA-smoothed)
    val visualScore: Float = 0.5f,
    val audioScore: Float = 0.5f,
    val gameScore: Float = 0.5f,

    // Modality engagement percentages — should sum to 100.
    val visualPreferencePercent: Float = 33.34f,
    val audioPreferencePercent: Float = 33.33f,
    val interactivePreferencePercent: Float = 33.33f,

    // Skill levels — 1 to 5
    val languageLevel: Int = 1,
    val numeracyLevel: Int = 1,
    val motorLevel: Int = 1,

    // Progress within current level — 0.0 to 1.0 (shown in progress bar)
    val languageProgress: Float = 0f,
    val numeracyProgress: Float = 0f,
    val motorProgress: Float = 0f,

    // Computed dominant modality
    val dominantModality: String = "VISUAL",

    // Comma-separated weak skill areas e.g. "LANGUAGE,MOTOR"
    val weakSkillAreas: String = "",

    val totalSessionCount: Int = 0,
    val totalActivitiesCompleted: Int = 0,
    val overallProgressPercent: Float = 0f,
    val assessmentCompleted: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    val weakSkillList: List<String>
        get() = weakSkillAreas.split(",").filter { it.isNotEmpty() }

    val dominantModalityArabic: String
        get() = when (dominantModality) {
            "VISUAL"      -> "بصري"
            "AUDIO"       -> "سمعي"
            "INTERACTIVE" -> "تفاعلي"
            else          -> "بصري"
        }
}
