package com.babybloom.domain.algorithm

import com.babybloom.data.local.entity.ActivityRecommendationEntity
import com.babybloom.domain.model.ActivityLaunchStep
import com.babybloom.domain.model.ChildProfile
import com.babybloom.domain.model.LearningContent
import com.babybloom.domain.model.SessionPhase
import com.babybloom.domain.repository.ActivityRepository
import com.babybloom.domain.repository.ActivityResultRepository
import com.babybloom.domain.repository.LevelMasteryRepository
import com.babybloom.domain.repository.LearningContentRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionPlannerService @Inject constructor(
    private val activityRepository: ActivityRepository,
    private val learningContentRepository: LearningContentRepository,
    private val activityResultRepository: ActivityResultRepository,
    private val algorithmEngine: AdaptiveAlgorithmEngine,
    private val levelMasteryRepository: LevelMasteryRepository
) {
    private val activityPriority = mapOf(
        "STORY"             to 0,
        "SPEECH"            to 1,
        "LISTEN_AND_CHOOSE" to 2,
        "DRAG"              to 3,
        "MATCH"             to 4,
        "COUNT"             to 5,
        "TRACE"             to 6
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
        val contentDrivenQueue = buildContentDrivenSessionSequence(profile)
        if (contentDrivenQueue.isNotEmpty()) return contentDrivenQueue

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
                            contentId = item.contentId,
                            isTest = false
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
                        contentId = matchingItem.contentId,
                        isTest = false
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

    private suspend fun buildContentDrivenSessionSequence(profile: ChildProfile): List<ActivityLaunchStep> {
        val allContent = listOf(
            learningContentRepository.getByCategory(CATEGORY_LETTER),
            learningContentRepository.getByCategory(CATEGORY_ANIMAL),
            learningContentRepository.getByCategory(CATEGORY_NUMBER),
            learningContentRepository.getByCategory(CATEGORY_COLOR),
            learningContentRepository.getByCategory(CATEGORY_SHAPE)
        ).flatten()
        if (allContent.isEmpty()) return emptyList()

        val contentById = allContent.associateBy { it.id }
        val results = activityResultRepository.getByChild(profile.childId)
        val resultHistory = results
            .filter { it.contentId.isNotBlank() }
            .groupBy { it.contentId.removeSuffix("_s") }
        val scoreByResultId = results.associate { result ->
            result.id to algorithmScore(result)
        }
        val latestScoreByContent = resultHistory.mapValues { (_, contentResults) ->
            contentResults
                .maxByOrNull { it.timestamp }
                ?.let { scoreByResultId[it.id] }
                ?: 0f
        }
        val passedContentIds = latestScoreByContent
            .filterValues { it >= AlgorithmWeights.CONTENT_PASS_THRESHOLD }
            .keys
        val assessmentPassedContentIds = assessmentPassedContentIds(profile.childId, allContent)
        val masteredContentIds = passedContentIds + assessmentPassedContentIds

        val revisionContentQueue = selectRevisionContentIds(
            contentById = contentById,
            resultHistory = resultHistory,
            scoreByResultId = scoreByResultId,
            passedContentIds = masteredContentIds,
            excludedContentIds = emptySet(),
            limit = Int.MAX_VALUE
        )
            .mapNotNull { contentById[it] }

        val learningBatches = buildLearningBatches(
            allContent = allContent,
            resultHistory = resultHistory,
            passedContentIds = masteredContentIds
        )

        val queue = mutableListOf<ActivityLaunchStep>()
        val usedTestStepKeys = mutableSetOf<String>()
        learningBatches.firstOrNull()?.let { learningContentItems ->
            val learningSteps = learningContentItems.flatMap { content ->
                stepsForContent(
                    content = content,
                    allContent = allContent,
                    phase = SessionPhase.LEARNING,
                    profile = profile
                )
            }
            val testSteps = interleaveTestSteps(
                learningContentItems.map { content ->
                    stepsForContent(
                        content = content,
                        allContent = allContent,
                        phase = SessionPhase.TEST,
                        profile = profile
                    )
                }
            )
                .distinctBy { it.exactStepKey() }
            val uniqueTestSteps = testSteps.filter { usedTestStepKeys.add(it.exactStepKey()) }
            val currentLearningIds = learningContentItems.map { it.id }.toSet()
            val revisionBatch = revisionContentQueue
                .filter { it.id !in currentLearningIds }

            val revisionSteps = interleaveTestSteps(
                revisionBatch.map { content ->
                    stepsForContent(
                        content = content,
                        allContent = allContent,
                        phase = SessionPhase.REVISION,
                        profile = profile
                    )
                }
            )
                .filter { usedTestStepKeys.add(it.exactStepKey()) }

            queue += learningSteps
            queue += uniqueTestSteps
            queue += revisionSteps
        }

        if (queue.isEmpty() && revisionContentQueue.isNotEmpty()) {
            queue += interleaveTestSteps(
                revisionContentQueue
                    .map { content ->
                        stepsForContent(
                            content = content,
                            allContent = allContent,
                            phase = SessionPhase.REVISION,
                            profile = profile
                        )
                    }
            )
                .filter { usedTestStepKeys.add(it.exactStepKey()) }
        }

        return queue
            .distinctBy { it.exactStepKey() }
    }

    private fun buildLearningBatches(
        allContent: List<LearningContent>,
        resultHistory: Map<String, List<com.babybloom.domain.model.ActivityResult>>,
        passedContentIds: Set<String>
    ): List<List<LearningContent>> {
        val letterQueue = learningQueueForCategory(allContent, CATEGORY_LETTER, passedContentIds)
        val animalQueue = learningQueueForCategory(allContent, CATEGORY_ANIMAL, passedContentIds)
        val thirdQueues = listOf(CATEGORY_NUMBER, CATEGORY_COLOR, CATEGORY_SHAPE)
            .associateWith { category ->
                learningQueueForCategory(allContent, category, passedContentIds)
            }

        val batches = mutableListOf<List<LearningContent>>()
        val thirdIndexes = mutableMapOf(
            CATEGORY_NUMBER to 0,
            CATEGORY_COLOR to 0,
            CATEGORY_SHAPE to 0
        )
        val maxBatches = listOf(
            letterQueue.size,
            animalQueue.size,
            thirdQueues.values.sumOf { it.size }
        ).maxOrNull() ?: 0
        if (maxBatches == 0) return emptyList()

        var lastThirdCategory = latestThirdCategory(allContent, resultHistory)
        repeat(maxBatches) { index ->
            val thirdCategory = pickThirdCategoryForBatch(
                thirdQueues = thirdQueues,
                thirdIndexes = thirdIndexes,
                previousCategory = lastThirdCategory
            )
            val third = thirdCategory?.let { category ->
                val categoryIndex = thirdIndexes[category] ?: 0
                thirdIndexes[category] = categoryIndex + 1
                thirdQueues[category]?.getOrNull(categoryIndex)
            }
            if (third != null) lastThirdCategory = third.category

            val batch = listOfNotNull(
                letterQueue.getOrNull(index),
                animalQueue.getOrNull(index),
                third
            ).distinctBy { it.id }

            if (batch.isNotEmpty()) batches += batch
        }

        return batches
    }

    private fun learningQueueForCategory(
        allContent: List<LearningContent>,
        category: String,
        passedContentIds: Set<String>
    ): List<LearningContent> =
        allContent
            .filter { it.category == category && it.id !in passedContentIds }
            .sortedBy { it.learningOrder }

    private fun latestThirdCategory(
        allContent: List<LearningContent>,
        resultHistory: Map<String, List<com.babybloom.domain.model.ActivityResult>>
    ): String? {
        val categories = listOf(CATEGORY_NUMBER, CATEGORY_COLOR, CATEGORY_SHAPE)
        return resultHistory
            .flatMap { (contentId, history) ->
                val category = allContent.firstOrNull { it.id == contentId }?.category
                history.map { result -> category to result.timestamp }
            }
            .filter { (category, _) -> category in categories }
            .maxByOrNull { (_, timestamp) -> timestamp }
            ?.first
    }

    private fun pickThirdCategoryForBatch(
        thirdQueues: Map<String, List<LearningContent>>,
        thirdIndexes: Map<String, Int>,
        previousCategory: String?
    ): String? {
        val categories = listOf(CATEGORY_NUMBER, CATEGORY_COLOR, CATEGORY_SHAPE)
        val candidateCategories = categories
            .dropWhile { it != previousCategory }
            .drop(1) + categories.takeWhile { it != previousCategory }
        val rotatedCategories = candidateCategories.ifEmpty { categories }
        return rotatedCategories.firstOrNull { category ->
            val nextIndex = thirdIndexes[category] ?: 0
            thirdQueues[category]?.getOrNull(nextIndex) != null
        }
    }

    private fun selectRevisionContentIds(
        contentById: Map<String, LearningContent>,
        resultHistory: Map<String, List<com.babybloom.domain.model.ActivityResult>>,
        scoreByResultId: Map<Long, Float>,
        passedContentIds: Set<String>,
        excludedContentIds: Set<String>,
        limit: Int
    ): List<String> =
        passedContentIds
            .filter { it !in excludedContentIds && contentById.containsKey(it) }
            .mapNotNull { contentId ->
                val history = resultHistory[contentId].orEmpty()
                val latest = history.maxByOrNull { it.timestamp } ?: return@mapNotNull null
                RevisionCandidate(
                    contentId = contentId,
                    score = scoreByResultId[latest.id] ?: latest.score,
                    lastPassed = latest.timestamp
                )
            }
            .sortedWith(
                compareBy<RevisionCandidate> { it.lastPassed }
                    .thenBy { it.score }
            )
            .take(limit)
            .map { it.contentId }

    private suspend fun stepsForContent(
        content: LearningContent,
        allContent: List<LearningContent>,
        phase: SessionPhase,
        profile: ChildProfile
    ): List<ActivityLaunchStep> {
        val allowedPrefixes = activityPrefixesFor(content.category, phase)
        val allActivities = activityRepository.getAll()
        val candidates = allActivities
            .filter { activity ->
                allowedPrefixes.any { prefix -> activity.id.startsWith(prefix) }
            }
            .filter { activity ->
                phase != SessionPhase.LEARNING ||
                    activity.activityType == "STORY" ||
                    activity.modality == profile.dominantModality
            }
            .sortedWith(
                compareByDescending<com.babybloom.domain.model.Activity> { activity ->
                    if (phase == SessionPhase.LEARNING) modalityPreferenceScore(activity, profile)
                    else 0f
                }.thenBy { activity ->
                    allowedPrefixes.indexOfFirst { prefix -> activity.id.startsWith(prefix) }
                        .takeIf { it >= 0 }
                        ?: Int.MAX_VALUE
                }
            )
            .mapNotNull { activity ->
                val activityWithContent = activityRepository.getActivityWithContent(activity.id)
                    ?: return@mapNotNull null
                val matchingId = matchingContentIdForActivity(
                    content = content,
                    activityId = activity.id,
                    allContent = allContent
                )
                val matchingItem = activityWithContent.contentItems.firstOrNull { item ->
                    item.contentId.removeSuffix("_s") == matchingId.removeSuffix("_s")
                } ?: return@mapNotNull null

                ActivityLaunchStep(
                    activityId = activity.id,
                    contentId = matchingItem.contentId,
                    isTest = phase != SessionPhase.LEARNING,
                    phase = phase
                )
            }
            .distinctBy { step ->
                allowedPrefixes.firstOrNull { prefix -> step.activityId.startsWith(prefix) }
                    ?: step.activityId
            }

        if (phase != SessionPhase.LEARNING) return candidates
        val storySteps = candidates.filter { step ->
            allActivities.firstOrNull { it.id == step.activityId }?.activityType == "STORY"
        }
        return storySteps + candidates.filterNot { it in storySteps }
    }

    private fun modalityPreferenceScore(
        activity: com.babybloom.domain.model.Activity,
        profile: ChildProfile
    ): Float {
        val percentages = mapOf(
            "VISUAL" to profile.visualPreferencePercent,
            "AUDIO" to profile.audioPreferencePercent,
            "INTERACTIVE" to profile.interactivePreferencePercent
        )
        val weights = AlgorithmWeights.ACTIVITY_MODALITY_WEIGHTS[activity.activityType]
            ?: mapOf(activity.modality to 1f)
        return weights.entries.sumOf { (modality, weight) ->
            ((percentages[modality] ?: 0f) * weight).toDouble()
        }.toFloat()
    }

    private fun activityPrefixesFor(
        category: String,
        phase: SessionPhase
    ): List<String> =
        when (category) {
            CATEGORY_LETTER -> when (phase) {
                SessionPhase.LEARNING -> listOf("story_letters", "match_letters", "trace_letters","listen_and_choose_letters")
                SessionPhase.TEST, SessionPhase.REVISION -> listOf(
                    "speech_letters_",
                    "speech_letters_sounds",
                    "match_letters",
                    "trace_letters",
                    "listen_and_choose_letters"
                )
            }
            CATEGORY_ANIMAL -> when (phase) {
                SessionPhase.LEARNING -> listOf("story_animals", "drag_letters", "match_animals","listen_and_choose_animals")
                SessionPhase.TEST, SessionPhase.REVISION -> listOf("speech_animals", "drag_letters", "match_animals", "listen_and_choose_animals")
            }
            CATEGORY_NUMBER -> when (phase) {
                SessionPhase.LEARNING -> listOf("story_numbers", "count_", "drag_numbers", "trace_numbers","listen_and_choose_numbers")
                SessionPhase.TEST, SessionPhase.REVISION -> listOf("speech_numbers", "count_", "drag_numbers", "trace_numbers", "listen_and_choose_numbers")
            }
            CATEGORY_SHAPE -> when (phase) {
                SessionPhase.LEARNING -> listOf("story_shapes", "drag_shapes", "trace_shapes", "match_shapes","listen_and_choose_shapes")
                SessionPhase.TEST, SessionPhase.REVISION -> listOf("speech_shapes", "drag_shapes", "trace_shapes", "match_shapes", "listen_and_choose_shapes")
            }
            CATEGORY_COLOR -> when (phase) {
                SessionPhase.LEARNING -> listOf("story_colors", "drag_colors", "match_colors", "listen_and_choose_colors")
                SessionPhase.TEST, SessionPhase.REVISION -> listOf("speech_colors", "drag_colors", "match_colors", "listen_and_choose_colors")
            }
            else -> emptyList()
        }

    private fun matchingContentIdForActivity(
        content: LearningContent,
        activityId: String,
        allContent: List<LearningContent>
    ): String {
        if (content.category == CATEGORY_ANIMAL && activityId.startsWith("drag_letters")) {
            return allContent.firstOrNull {
                it.category == CATEGORY_LETTER && it.learningOrder == content.learningOrder
            }?.id ?: content.id
        }
        return content.id
    }

    private fun interleaveTestSteps(
        groupedSteps: List<List<ActivityLaunchStep>>
    ): List<ActivityLaunchStep> {
        val shuffledGroups = groupedSteps
            .filter { it.isNotEmpty() }
            .map { it.shuffled() }
            .shuffled()
        val result = mutableListOf<ActivityLaunchStep>()
        val maxSize = shuffledGroups.maxOfOrNull { it.size } ?: 0
        repeat(maxSize) { index ->
            shuffledGroups.forEach { group ->
                group.getOrNull(index)?.let(result::add)
            }
        }
        return result
    }

    private data class RevisionCandidate(
        val contentId: String,
        val score: Float,
        val lastPassed: Long
    )

    private suspend fun assessmentPassedContentIds(
        childId: Long,
        allContent: List<LearningContent>
    ): Set<String> {
        val masteredByCategory = levelMasteryRepository.getAllForChild(childId)
            .groupBy { it.skillArea }
            .mapValues { (_, rows) ->
                rows
                    .filter { it.masteredCount > 0 }
                    .maxOfOrNull { it.level }
                    ?: 0
            }

        return allContent
            .filter { content ->
                val masteredLevel = masteredByCategory[content.category] ?: 0
                content.difficultyLevel <= masteredLevel
            }
            .mapTo(mutableSetOf()) { it.id }
    }

    private suspend fun algorithmScore(result: com.babybloom.domain.model.ActivityResult): Float {
        val activity = activityRepository.getById(result.activityId) ?: return result.score
        val signal = com.babybloom.domain.model.ActivitySignal.from(result, activity)
        return algorithmEngine.computeItemScore(signal)
    }

    private fun ActivityLaunchStep.exactStepKey(): String =
        "${activityId}:${contentId.orEmpty()}:${phase.name}"

    private companion object {
        const val CATEGORY_LETTER = "LETTER_NAME"
        const val CATEGORY_ANIMAL = "ANIMAL"
        const val CATEGORY_NUMBER = "NUMBER"
        const val CATEGORY_COLOR = "COLOR"
        const val CATEGORY_SHAPE = "SHAPE"
    }
}
