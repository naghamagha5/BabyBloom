package com.babybloom.domain.model

data class ActivitySignal(
    val childId: Long,
    val activityId: String,
    val skillArea: String,
    val modality: String,
    val activityType: String,
    val difficultyLevel: Int,
    val correctCount: Int,
    val incorrectCount: Int,
    val attempts: Int,
    val attentionScore: Float?,
    val motorSkillScore: Float?,
    val choiceConfidenceScore: Float?,
    val speechConfidence: Float?,
    val durationMs: Long,
    val expectedDurationMs: Long = 60_000L
) {
    companion object {
        fun from(result: ActivityResult, activity: Activity): ActivitySignal =
            ActivitySignal(
                childId           = result.childId,
                activityId        = result.activityId,
                skillArea         = activity.skillArea,
                modality          = activity.modality,
                activityType      = activity.activityType,
                difficultyLevel   = activity.difficultyLevel,
                correctCount      = result.correctCount,
                incorrectCount    = result.incorrectCount,
                attempts          = result.attempts,
                attentionScore    = result.attentionScore,
                motorSkillScore   = result.motorSkillScore,
                choiceConfidenceScore = result.choiceConfidenceScore,
                speechConfidence  = result.speechConfidence,
                durationMs        = result.duration,
                expectedDurationMs = 60_000L
            )
    }
}
