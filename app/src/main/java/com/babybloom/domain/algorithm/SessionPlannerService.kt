package com.babybloom.domain.algorithm

import com.babybloom.data.local.entity.ActivityRecommendationEntity
import com.babybloom.domain.model.ActivityLaunchStep
import com.babybloom.domain.model.ChildProfile
import com.babybloom.domain.repository.ActivityRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionPlannerService @Inject constructor(
    private val activityRepository: ActivityRepository
) {
    private val activityPriority = mapOf(
        "STORY" to 0,
        "SPEECH" to 1,
        "DRAG" to 2,
        "MATCH" to 3,
        "COUNT" to 4,
        "TRACE" to 5
    )

    /**
     * Builds an ordered activity queue for a session.
     * Prioritizes weak skill areas and dominant modality.
     */
    suspend fun planSession(
        profile: ChildProfile,
        sessionActivityCount: Int = AlgorithmWeights.SESSION_ACTIVITY_COUNT
    ): List<ActivityRecommendationEntity> {

        val weakSkills  = profile.weakSkillList
        val weakCount   = (sessionActivityCount * AlgorithmWeights.WEAK_SKILL_ACTIVITY_RATIO).toInt()
            .coerceAtLeast(if (weakSkills.isEmpty()) 0 else 1)
        val normalCount = sessionActivityCount - weakCount

        val chosen = mutableListOf<String>()  // activity IDs picked so far
        val result = mutableListOf<ActivityRecommendationEntity>()

        // 1. Weak skill slots — prefer dominant modality
        repeat(weakCount) { i ->
            val targetSkill = weakSkills.getOrElse(i % weakSkills.size.coerceAtLeast(1)) {
                weakSkills.firstOrNull() ?: "LANGUAGE"
            }
            val level = profile.levelFor(targetSkill)
            val candidates = getPlanningCandidates(targetSkill, level)

            val candidate = candidates
                .filter { it.modality == profile.dominantModality && it.id !in chosen }
                .randomOrNull()
                ?: candidates.filter { it.id !in chosen }.randomOrNull()

            candidate?.let {
                chosen.add(it.id)
                result.add(
                    ActivityRecommendationEntity(
                        childId    = profile.childId,
                        activityId = it.id,
                        sessionId  = 0L,
                        reason     = "WEAK_SKILL"
                    )
                )
            }
        }

        // 2. Normal slots — balanced across skill areas
        val skillCycle = listOf("LANGUAGE", "NUMERACY", "MOTOR")
        repeat(normalCount) { i ->
            val targetSkill = skillCycle[i % skillCycle.size]
            val level = profile.levelFor(targetSkill)
            val candidates = getPlanningCandidates(targetSkill, level)

            val candidate = candidates
                .filter { it.id !in chosen }
                .randomOrNull()

            candidate?.let {
                chosen.add(it.id)
                result.add(
                    ActivityRecommendationEntity(
                        childId = profile.childId,
                        activityId = it.id,
                        sessionId = 0L,
                        reason = "BALANCED"
                    )
                )
            }
        }

        return result
    }

    suspend fun buildSessionSequence(
        profile: ChildProfile,
        sessionActivityCount: Int = AlgorithmWeights.SESSION_ACTIVITY_COUNT
    ): List<ActivityLaunchStep> {
        val plannedActivities = planSession(profile, sessionActivityCount)
        if (plannedActivities.isEmpty()) return emptyList()

        val allActivities = activityRepository.getAll()
        val activitySkillById = mutableMapOf<String, String>()
        val groupedByContent = linkedMapOf<String, MutableList<ActivityLaunchStep>>()

        plannedActivities.forEach { recommendation ->
            val activityWithContent = activityRepository
                .getActivityWithContent(recommendation.activityId)
                ?: return@forEach
            activitySkillById[activityWithContent.activity.id] = activityWithContent.activity.skillArea

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

        return groupedByContent.entries.flatMap { (normalizedContentId, steps) ->
            val skillArea = steps.firstNotNullOfOrNull { step ->
                activitySkillById[step.activityId]
            } ?: "LANGUAGE"
            val storySteps = allActivities
                .filter { activity ->
                    activity.activityType == "STORY" && activity.skillArea == skillArea
                }
                .mapNotNull { storyActivity ->
                    val storyWithContent = activityRepository.getActivityWithContent(storyActivity.id) ?: return@mapNotNull null
                    val matchingItem = storyWithContent.contentItems.firstOrNull {
                        it.contentId.removeSuffix("_s") == normalizedContentId
                    } ?: return@mapNotNull null

                    ActivityLaunchStep(
                        activityId = storyActivity.id,
                        contentId = matchingItem.contentId
                    )
                }

            (storySteps + steps)
                .distinctBy { "${it.activityId}:${it.contentId.orEmpty()}" }
                .sortedBy { step ->
                    activityPriority[
                        allActivities.firstOrNull { it.id == step.activityId }?.activityType
                    ] ?: Int.MAX_VALUE
                }
        }
    }

    private suspend fun getPlanningCandidates(skillArea: String, level: Int): List<com.babybloom.domain.model.Activity> {
        val exact = activityRepository.getActivitiesForPlanning(skillArea, level)
        if (exact.isNotEmpty()) return exact

        for (fallbackLevel in level - 1 downTo 1) {
            val fallback = activityRepository.getActivitiesForPlanning(skillArea, fallbackLevel)
            if (fallback.isNotEmpty()) return fallback
        }

        return activityRepository.getAll().filter { it.skillArea == skillArea }
    }

    private fun ChildProfile.levelFor(skillArea: String) = when (skillArea) {
        "LANGUAGE" -> languageLevel
        "NUMERACY" -> numeracyLevel
        "MOTOR"    -> motorLevel
        else       -> 1
    }
}
