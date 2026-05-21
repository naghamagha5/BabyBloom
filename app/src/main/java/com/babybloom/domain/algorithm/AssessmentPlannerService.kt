package com.babybloom.domain.algorithm

import com.babybloom.domain.repository.ActivityRepository
import javax.inject.Inject
import javax.inject.Singleton

enum class AssessmentCategory {
    COLORS,
    SHAPES,
    NUMBERS,
    LETTERS,
    ANIMALS
}

data class AssessmentLaunchStep(
    val activityId: String,
    val contentId: String?,
    val isTest: Boolean = false,
    val category: AssessmentCategory?,
    val level: Int,
    val isWarmUp: Boolean = false
)

@Singleton
class AssessmentPlannerService @Inject constructor(
    private val activityRepository: ActivityRepository
) {
    suspend fun buildWarmUpSequence(): List<AssessmentLaunchStep> =
        listOfNotNull(
            warmUpStep("drag_numbers_d1", "number_1"),
            warmUpStep("trace_letters_d1", "letter_alef"),
            warmUpStep("match_letters_d1", "letter_ba")
        )

    suspend fun nextProbe(
        category: AssessmentCategory,
        level: Int,
        probeIndex: Int
    ): AssessmentLaunchStep? {
        val candidates = activityIdsFor(category, level)
            .mapNotNull { activityId -> activityRepository.getActivityWithContent(activityId) }
            .filter { it.activity.activityType != "STORY" && it.activity.isActive }

        val candidate = candidates.getOrNull(probeIndex % candidates.size.coerceAtLeast(1))
            ?: return fallbackProbe(category, level, probeIndex)

        val contentItems = candidate.contentItems.ifEmpty { return null }
        val content = contentItems[probeIndex % contentItems.size]

        return AssessmentLaunchStep(
            activityId = candidate.activity.id,
            contentId = content.contentId,
            isTest = true,
            category = category,
            level = candidate.activity.difficultyLevel,
            isWarmUp = false
        )
    }

    private suspend fun fallbackProbe(
        category: AssessmentCategory,
        requestedLevel: Int,
        probeIndex: Int
    ): AssessmentLaunchStep? {
        for (level in requestedLevel downTo 1) {
            val candidates = activityIdsFor(category, level)
                .mapNotNull { activityId -> activityRepository.getActivityWithContent(activityId) }
                .filter { it.activity.activityType != "STORY" && it.activity.isActive }

            if (candidates.isNotEmpty()) {
                val candidate = candidates[probeIndex % candidates.size]
                val content = candidate.contentItems.getOrNull(probeIndex % candidate.contentItems.size.coerceAtLeast(1))
                    ?: return null
                return AssessmentLaunchStep(
                    activityId = candidate.activity.id,
                    contentId = content.contentId,
                    isTest = true,
                    category = category,
                    level = candidate.activity.difficultyLevel,
                    isWarmUp = false
                )
            }
        }
        return null
    }

    private suspend fun warmUpStep(activityId: String, preferredContentId: String): AssessmentLaunchStep? {
        val activity = activityRepository.getActivityWithContent(activityId) ?: return null
        val contentId = activity.contentItems.firstOrNull { it.contentId == preferredContentId }?.contentId
            ?: activity.contentItems.firstOrNull()?.contentId
            ?: return null

        return AssessmentLaunchStep(
            activityId = activityId,
            contentId = contentId,
            isTest = false,
            category = null,
            level = activity.activity.difficultyLevel,
            isWarmUp = true
        )
    }

    private fun activityIdsFor(category: AssessmentCategory, level: Int): List<String> =
        when (category) {
            AssessmentCategory.COLORS -> listOf("drag_colors_d$level")
            AssessmentCategory.SHAPES -> listOf("trace_shapes_d$level")
            AssessmentCategory.NUMBERS -> listOf(
                "count_d$level",
                "drag_numbers_d$level"
            )
            AssessmentCategory.LETTERS -> listOf(
                "drag_letters_d$level",
                "match_letters_d$level",
                "trace_letters_d$level"
            )
            AssessmentCategory.ANIMALS -> listOf(
                "speech_animals_d$level"
            )
        }
}
