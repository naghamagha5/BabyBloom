package com.babybloom.domain.algorithm

import com.babybloom.domain.model.Activity
import com.babybloom.domain.model.ActivityLaunchStep
import com.babybloom.domain.repository.ActivityRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssessmentPlannerService @Inject constructor(
    private val activityRepository: ActivityRepository
) {
    private val assessmentActivityIds = listOf(
        "trace_letters_d1",
        "speech_letters_d1",
        "drag_letters_d1",
        "count_numbers_d1"
    )

    /**
     * Returns a balanced set of assessment activities:
     * 2 per skill area (1 visual + 1 interactive) × 3 areas = 6 total.
     * All at ASSESSMENT_START_DIFFICULTY.
     */
    suspend fun buildAssessmentPlan(childAge: Int): List<Activity> {
        val plan = mutableListOf<Activity>()
        val difficulty = AlgorithmWeights.ASSESSMENT_START_DIFFICULTY

        for (skill in listOf("LANGUAGE", "NUMERACY", "MOTOR")) {
            val allForSkill = activityRepository.getActivitiesForPlanning(
                skillArea       = skill,
                difficultyLevel = difficulty
            )

            // Pick 1 visual + 1 interactive (or any 2 if modalities not available)
            val visual      = allForSkill.firstOrNull { it.modality == "VISUAL"      }
            val interactive = allForSkill.firstOrNull { it.modality == "INTERACTIVE" }
            val audio       = allForSkill.firstOrNull { it.modality == "AUDIO"       }

            val selected = listOfNotNull(visual, interactive, audio)
                .distinctBy { it.id }
                .take(AlgorithmWeights.ASSESSMENT_ACTIVITIES_PER_SKILL)

            // Fallback: take any if still not enough
            if (selected.size < AlgorithmWeights.ASSESSMENT_ACTIVITIES_PER_SKILL) {
                val fallback = allForSkill
                    .filter { a -> selected.none { it.id == a.id } }
                    .take(AlgorithmWeights.ASSESSMENT_ACTIVITIES_PER_SKILL - selected.size)
                plan.addAll(selected + fallback)
            } else {
                plan.addAll(selected)
            }
        }

        return plan.shuffled()
    }

    suspend fun buildAssessmentSequence(childAge: Int): List<ActivityLaunchStep> {
        val activities = assessmentActivityIds.mapNotNull { activityId ->
            activityRepository.getActivityWithContent(activityId)
        }

        if (activities.isEmpty()) return emptyList()

        val groupedByContent = linkedMapOf<String, MutableList<ActivityLaunchStep>>()

        activities.forEach { activityWithContent ->
            activityWithContent.contentItems.forEach { item ->
                val normalizedContentId = item.contentId.removeSuffix("_s")
                groupedByContent
                    .getOrPut(normalizedContentId) { mutableListOf() }
                    .add(
                        ActivityLaunchStep(
                            activityId = activityWithContent.activity.id,
                            contentId = item.contentId
                        )
                    )
            }
        }

        return groupedByContent.values.flatten()
    }
}
