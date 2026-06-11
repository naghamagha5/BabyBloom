package com.babybloom.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babybloom.data.local.entity.AssessmentResultEntity
import com.babybloom.domain.algorithm.AssessmentCategory
import com.babybloom.domain.algorithm.AssessmentLaunchStep
import com.babybloom.domain.algorithm.AssessmentPlannerService
import com.babybloom.domain.model.AssessmentResult
import com.babybloom.domain.model.CategoryAssessment
import com.babybloom.domain.model.ChildProfile
import com.babybloom.domain.model.Confidence
import com.babybloom.domain.model.Modality
import com.babybloom.domain.model.Session
import com.babybloom.domain.repository.ActivityResultRepository
import com.babybloom.domain.repository.AssessmentRepository
import com.babybloom.domain.repository.ChildProfileRepository
import com.babybloom.domain.repository.ChildRepository
import com.babybloom.domain.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

sealed class AssessmentUiState {
    object Loading : AssessmentUiState()
    data class Intro(
        val childName: String,
        val isCalmMode: Boolean
    ) : AssessmentUiState()
    data class Playing(
        val currentActivityId: String,
        val currentContentId: String?,
        val sessionId: Long,
        val currentIndex: Int,
        val totalCount: Int,
        val isTest: Boolean
    ) : AssessmentUiState()
    object Bootstrapping : AssessmentUiState()
    data class Complete(
        val correctCount: Int,
        val totalCount: Int
    ) : AssessmentUiState()
    data class Error(val message: String) : AssessmentUiState()
}

private enum class StaircaseStatus {
    TESTING,
    CONVERGED,
    PARTIAL
}

private enum class AssessmentDomain {
    LANGUAGE,
    NUMERACY,
    MOTOR
}

private data class LevelKey(
    val category: AssessmentCategory,
    val level: Int
)

private data class CategoryState(
    val category: AssessmentCategory,
    var currentLevel: Int,
    var status: StaircaseStatus = StaircaseStatus.TESTING,
    var ceilingLevel: Int? = null,
    var lastProbeOrder: Int = -1,
    var probeCount: Int = 0,
    val currentLevelOutcomes: MutableList<Boolean> = mutableListOf(),
    val levelOutcomes: MutableMap<Int, MutableList<Boolean>> = mutableMapOf()
)

private data class ProbeRecord(
    val category: AssessmentCategory,
    val level: Int,
    val activityId: String,
    val contentId: String?,
    val isCorrect: Boolean
)

@HiltViewModel
class AssessmentViewModel @Inject constructor(
    private val assessmentPlannerService: AssessmentPlannerService,
    private val assessmentRepository: AssessmentRepository,
    private val childProfileRepository: ChildProfileRepository,
    private val childRepository: ChildRepository,
    private val sessionRepository: SessionRepository,
    private val activityResultRepository: ActivityResultRepository
) : ViewModel() {

    private companion object {
        const val SCORED_ITEM_CAP = 20
        const val ASSESSMENT_TOTAL_DISPLAY = 23
        const val LIMITED_POOL_REVISIT_GAP = 2
    }

    private val _uiState = MutableStateFlow<AssessmentUiState>(AssessmentUiState.Loading)
    val uiState: StateFlow<AssessmentUiState> = _uiState.asStateFlow()

    private var childId: Long = 0L
    private var sessionId: Long = 0L
    private var sessionStartMs: Long = 0L
    private var currentStep: AssessmentLaunchStep? = null
    private var displayIndex: Int = 0
    private var scoredItemCount: Int = 0
    private var correctShownCount: Int = 0
    private var probeOrder: Int = 0
    private var warmUpQueue: List<AssessmentLaunchStep> = emptyList()
    private var warmUpIndex: Int = 0
    private var categoryStates: LinkedHashMap<AssessmentCategory, CategoryState> = linkedMapOf()
    private val probeRecords = mutableListOf<ProbeRecord>()
    private val warmUpContentIds = mutableSetOf<String>()
    private val usedContentIds = mutableSetOf<String>()
    private val usedProbeKeys = mutableSetOf<String>()
    private val levelContentPools = mutableMapOf<LevelKey, MutableSet<String>>()
    private val domainCounts = mutableMapOf<AssessmentDomain, Int>()

    fun startAssessment(childId: Long) {
        this.childId = childId
        viewModelScope.launch {
            val child = childRepository.getById(childId) ?: run {
                _uiState.value = AssessmentUiState.Error("Child not found")
                return@launch
            }
            val profile = childProfileRepository.getByChildId(childId)
            if (profile?.assessmentCompleted == true) {
                _uiState.value = AssessmentUiState.Complete(0, 0)
                return@launch
            }
            _uiState.value = AssessmentUiState.Intro(
                childName = child.name,
                isCalmMode = child.uiTheme
            )
        }
    }

    fun beginActivities() {
        viewModelScope.launch {
            val child = childRepository.getById(childId) ?: run {
                _uiState.value = AssessmentUiState.Error("Child not found")
                return@launch
            }

            warmUpQueue = assessmentPlannerService.buildWarmUpSequence()
            categoryStates = initialCategoryStates()
            probeRecords.clear()
            warmUpContentIds.clear()
            usedContentIds.clear()
            usedProbeKeys.clear()
            levelContentPools.clear()
            domainCounts.clear()
            warmUpIndex = 0
            displayIndex = 0
            scoredItemCount = 0
            correctShownCount = 0
            probeOrder = 0

            sessionStartMs = System.currentTimeMillis()
            sessionId = sessionRepository.startSession(
                Session(
                    userId = child.userId,
                    childId = childId,
                    startTime = sessionStartMs,
                    endTime = null,
                    isAssessment = true,
                    attentionScore = 0f
                )
            )

            advanceToNextStep()
        }
    }

    fun onActivityComplete(score: Int, total: Int) {
        val finishedStep = currentStep ?: return
        val isCorrect = total > 0 && score >= total
        if (isCorrect) correctShownCount++

        if (finishedStep.isWarmUp) {
            warmUpIndex++
        } else {
            val category = finishedStep.category ?: return
            scoredItemCount++
            val domain = domainOf(category)
            domainCounts[domain] = (domainCounts[domain] ?: 0) + 1
            probeRecords.add(
                ProbeRecord(
                    category = category,
                    level = finishedStep.level,
                    activityId = finishedStep.activityId,
                    contentId = finishedStep.contentId,
                    isCorrect = isCorrect
                )
            )
            updateStaircase(category, finishedStep.level, isCorrect)
        }

        displayIndex++
        advanceToNextStep()
    }

    private fun advanceToNextStep() {
        viewModelScope.launch {
            if (warmUpIndex < warmUpQueue.size) {
                showStep(warmUpQueue[warmUpIndex])
                return@launch
            }

            if (scoredItemCount >= SCORED_ITEM_CAP) {
                categoryStates.values
                    .filter { it.status == StaircaseStatus.TESTING }
                    .forEach { it.status = StaircaseStatus.PARTIAL }
                bootstrapProfile(hitItemCap = true)
                return@launch
            }

            while (true) {
                val nextCategory = pickNextCategory()

                if (nextCategory == null) {
                    bootstrapProfile(hitItemCap = false)
                    return@launch
                }

                val step = findUnusedStep(nextCategory)

                if (step == null) {
                    nextCategory.status = StaircaseStatus.CONVERGED
                    nextCategory.ceilingLevel = (nextCategory.currentLevel - 1).coerceIn(1, 5)
                    continue
                }

                nextCategory.lastProbeOrder = probeOrder++
                showStep(step)
                return@launch
            }
        }
    }

    private fun showStep(step: AssessmentLaunchStep) {
        currentStep = step
        if (step.isWarmUp) {
            step.contentId?.let(warmUpContentIds::add)
        } else {
            step.contentId?.let(usedContentIds::add)
            usedProbeKeys.add(probeKey(step))
        }
        _uiState.value = AssessmentUiState.Playing(
            currentActivityId = step.activityId,
            currentContentId = step.contentId,
            sessionId = sessionId,
            currentIndex = displayIndex,
            totalCount = ASSESSMENT_TOTAL_DISPLAY,
            isTest = step.isTest
        )
    }

    private suspend fun findUnusedStep(categoryState: CategoryState): AssessmentLaunchStep? {
        val levelKey = LevelKey(categoryState.category, categoryState.currentLevel)
        val contentPool = levelContentPools.getOrPut(levelKey) { linkedSetOf() }
        val availableSteps = assessmentPlannerService
            .availableProbes(categoryState.category, categoryState.currentLevel)
            .filterNot { step -> step.contentId != null && step.contentId in warmUpContentIds }

        if (availableSteps.isEmpty()) return null

        val availableContentIds = availableSteps.mapNotNull { it.contentId }.distinct()
        val maxDistinctContentIds = availableContentIds.size.coerceAtMost(3)
        val allowsContentReuse = availableContentIds.size in 1..2

        if (contentPool.size < maxDistinctContentIds) {
            availableContentIds
                .filter { it !in usedContentIds && it !in contentPool }
                .take(maxDistinctContentIds - contentPool.size)
                .forEach(contentPool::add)
        }

        if (contentPool.isEmpty()) {
            availableContentIds.take(maxDistinctContentIds).forEach(contentPool::add)
        }

        val eligibleSteps = availableSteps.filter { step ->
            step.contentId == null || step.contentId in contentPool
        }
        if (eligibleSteps.isEmpty()) return null

        val offset = categoryState.probeCount++
        val rotatedEligibleSteps = eligibleSteps.rotate(offset)

        rotatedEligibleSteps.firstOrNull { step ->
            val contentId = step.contentId
            probeKey(step) !in usedProbeKeys &&
                (contentId == null || contentId !in usedContentIds)
        }?.let { return it }

        if (!allowsContentReuse) return null

        return rotatedEligibleSteps.firstOrNull()
    }

    private fun updateStaircase(
        category: AssessmentCategory,
        probedLevel: Int,
        isCorrect: Boolean
    ) {
        val state = categoryStates[category] ?: return
        state.levelOutcomes.getOrPut(probedLevel) { mutableListOf() }.add(isCorrect)
        state.currentLevelOutcomes.add(isCorrect)

        val outcomes = state.currentLevelOutcomes
        if (outcomes.size < 2) return

        val firstTwo = outcomes.take(2)
        when {
            firstTwo.all { it } -> advanceLevel(state)
            firstTwo.none { it } -> converge(state, state.currentLevel - 1)
            outcomes.size >= 3 && outcomes[2] -> advanceLevel(state)
            outcomes.size >= 3 -> converge(state, state.currentLevel)
        }
    }

    private fun advanceLevel(state: CategoryState) {
        if (state.currentLevel >= 5) {
            converge(state, 5)
            return
        }
        state.currentLevel += 1
        state.currentLevelOutcomes.clear()
    }

    private fun converge(state: CategoryState, ceilingLevel: Int) {
        state.status = StaircaseStatus.CONVERGED
        state.ceilingLevel = ceilingLevel.coerceIn(1, 5)
        state.currentLevelOutcomes.clear()
    }

    private fun bootstrapProfile(hitItemCap: Boolean) {
        viewModelScope.launch {
            _uiState.value = AssessmentUiState.Bootstrapping

            val result = buildAssessmentResult(hitItemCap)
            val profile = buildProfileFromResult(result)
            childProfileRepository.upsert(profile)

            assessmentRepository.save(
                AssessmentResultEntity(
                    childId = childId,
                    initialLanguageLevel = profile.languageLevel,
                    initialNumeracyLevel = profile.numeracyLevel,
                    initialMotorLevel = profile.motorLevel,
                    dominantModality = profile.dominantModality
                )
            )

            sessionRepository.endSession(sessionId, System.currentTimeMillis())
            _uiState.value = AssessmentUiState.Complete(
                correctCount = correctShownCount,
                totalCount = displayIndex
            )
        }
    }

    private suspend fun buildAssessmentResult(hitItemCap: Boolean): AssessmentResult {
        val categoryLevels = categoryStates.mapValues { (_, state) ->
            val confidence = when (state.status) {
                StaircaseStatus.CONVERGED -> Confidence.CONFIRMED
                StaircaseStatus.PARTIAL -> Confidence.PARTIAL
                StaircaseStatus.TESTING -> Confidence.PARTIAL
            }
            CategoryAssessment(
                level = (state.ceilingLevel ?: state.currentLevel).coerceIn(1, 5),
                confidence = confidence
            )
        }.mapKeys { it.key.name.lowercase() }

        val modalityScores = computeModalityScores()
        val dominantModality = modalityScores.maxByOrNull { it.value }?.key ?: Modality.VISUAL

        return AssessmentResult(
            globalLevel = computeGlobalLevel(),
            categoryLevels = categoryLevels,
            modalityScores = modalityScores,
            dominantModality = dominantModality,
            sessionDurationMs = System.currentTimeMillis() - sessionStartMs,
            completionRate = (scoredItemCount.toFloat() / SCORED_ITEM_CAP).coerceIn(0f, 1f),
            sessionItemCount = scoredItemCount,
            hitItemCap = hitItemCap || categoryStates.values.any { it.status == StaircaseStatus.PARTIAL }
        )
    }

    private fun buildProfileFromResult(result: AssessmentResult): ChildProfile {
        fun categoryLevel(category: AssessmentCategory): Int =
            result.categoryLevels[category.name.lowercase()]?.level ?: 1

        val languageLevel = ((categoryLevel(AssessmentCategory.LETTERS) + categoryLevel(AssessmentCategory.ANIMALS)) / 2f)
            .roundToInt()
            .coerceIn(1, 5)
        val numeracyLevel = categoryLevel(AssessmentCategory.NUMBERS)
        val motorLevel = ((categoryLevel(AssessmentCategory.COLORS) + categoryLevel(AssessmentCategory.SHAPES)) / 2f)
            .roundToInt()
            .coerceIn(1, 5)

        val visual = result.modalityScores[Modality.VISUAL] ?: 0.5f
        val audio = result.modalityScores[Modality.AUDIO] ?: 0.5f
        val interactive = result.modalityScores[Modality.INTERACTIVE] ?: 0.5f
        val modalityTotal = (visual + audio + interactive).takeIf { it > 0f } ?: 1f
        val visualPercent = visual / modalityTotal * 100f
        val audioPercent = audio / modalityTotal * 100f
        val interactivePercent = 100f - visualPercent - audioPercent

        val weakSkills = buildList {
            if (languageLevel <= 1) add("LANGUAGE")
            if (numeracyLevel <= 1) add("NUMERACY")
            if (motorLevel <= 1) add("MOTOR")
        }.joinToString(",")

        return ChildProfile(
            childId = childId,
            visualScore = visual,
            audioScore = audio,
            gameScore = interactive,
            visualPreferencePercent = visualPercent,
            audioPreferencePercent = audioPercent,
            interactivePreferencePercent = interactivePercent,
            languageLevel = languageLevel,
            numeracyLevel = numeracyLevel,
            motorLevel = motorLevel,
            languageProgress = skillProgress(languageLevel),
            numeracyProgress = skillProgress(numeracyLevel),
            motorProgress = skillProgress(motorLevel),
            dominantModality = result.dominantModality.name,
            weakSkillAreas = weakSkills,
            totalActivitiesCompleted = result.sessionItemCount,
            assessmentCompleted = true
        )
    }

    private fun computeGlobalLevel(): Int {
        val recordsByLevel = probeRecords.groupBy { it.level }
        return (1..5)
            .filter { level ->
                val records = recordsByLevel[level].orEmpty()
                records.isNotEmpty() && records.count { it.isCorrect }.toFloat() / records.size >= 0.75f
            }
            .maxOrNull()
            ?: 1
    }

    private suspend fun computeModalityScores(): Map<Modality, Float> {
        val dbResults = activityResultRepository.getForSession(sessionId)
        val weightedScores = mutableMapOf<Modality, Float>()
        val weights = mutableMapOf<Modality, Float>()

        probeRecords.forEach { record ->
            val dbResult = dbResults.lastOrNull {
                it.activityId == record.activityId && it.contentId == record.contentId
            }
            val multimodalSignals = buildList {
                add(dbResult?.attentionScore ?: 0.5f)
                if (record.activityId.startsWith("speech_")) {
                    dbResult?.speechConfidence?.let(::add)
                }
                if (record.activityId.startsWith("trace_") ||
                    record.activityId.startsWith("drag_") ||
                    record.activityId.startsWith("match_")) {
                    dbResult?.touchQualityScore?.let(::add)
                }
            }
            val score = multimodalSignals.average().toFloat().coerceIn(0f, 1f)
            modalityWeights(record.activityId).forEach { (modality, weight) ->
                weightedScores[modality] = (weightedScores[modality] ?: 0f) + score * weight
                weights[modality] = (weights[modality] ?: 0f) + weight
            }
        }

        return Modality.values().associateWith { modality ->
            val totalWeight = weights[modality] ?: 0f
            if (totalWeight == 0f) 0.5f
            else ((weightedScores[modality] ?: 0f) / totalWeight).coerceIn(0f, 1f)
        }
    }

    private fun modalityWeights(activityId: String): Map<Modality, Float> =
        when {
            activityId.startsWith("speech_") -> mapOf(Modality.AUDIO to 0.8f, Modality.VISUAL to 0.2f)
            activityId.startsWith("match_") -> mapOf(Modality.VISUAL to 0.9f, Modality.INTERACTIVE to 0.1f)
            activityId.startsWith("trace_") -> mapOf(Modality.INTERACTIVE to 0.8f, Modality.VISUAL to 0.2f)
            activityId.startsWith("count_") -> mapOf(Modality.VISUAL to 0.9f, Modality.INTERACTIVE to 0.1f)
            activityId.startsWith("drag_") -> mapOf(Modality.INTERACTIVE to 0.8f, Modality.VISUAL to 0.2f)
            else -> mapOf(Modality.VISUAL to 1f)
        }

    private fun skillProgress(level: Int): Float =
        (level.toFloat() / 5f).coerceIn(0f, 1f)

    private fun initialCategoryStates(): LinkedHashMap<AssessmentCategory, CategoryState> =
        linkedMapOf(
            AssessmentCategory.COLORS to CategoryState(AssessmentCategory.COLORS, currentLevel = 2),
            AssessmentCategory.SHAPES to CategoryState(AssessmentCategory.SHAPES, currentLevel = 2),
            AssessmentCategory.NUMBERS to CategoryState(AssessmentCategory.NUMBERS, currentLevel = 1),
            AssessmentCategory.LETTERS to CategoryState(AssessmentCategory.LETTERS, currentLevel = 1),
            AssessmentCategory.ANIMALS to CategoryState(AssessmentCategory.ANIMALS, currentLevel = 1)
        )

    private suspend fun pickNextCategory(): CategoryState? {
        val activeStates = categoryStates.values
            .filter { it.status == StaircaseStatus.TESTING }
        if (activeStates.isEmpty()) return null

        val limitedPoolInProgress = activeStates
            .filter {
                it.currentLevelOutcomes.isNotEmpty() &&
                    hasLimitedContentPool(it.category, it.currentLevel) &&
                    canRevisitLimitedPool(it)
            }
            .minWithOrNull(compareBy<CategoryState> { it.lastProbeOrder }.thenBy { it.category.ordinal })
        if (limitedPoolInProgress != null) return limitedPoolInProgress

        val domain = listOf(AssessmentDomain.LANGUAGE, AssessmentDomain.NUMERACY, AssessmentDomain.MOTOR)
            .filter { domain ->
                (domainCounts[domain] ?: 0) < domainQuota(domain) &&
                    activeStates.any { domainOf(it.category) == domain }
            }
            .minWithOrNull(
                compareBy<AssessmentDomain> {
                    (domainCounts[it] ?: 0).toFloat() / domainQuota(it).toFloat()
                }.thenBy { it.ordinal }
            )
            ?: activeStates
                .map { domainOf(it.category) }
                .distinct()
                .minWithOrNull(compareBy<AssessmentDomain> { domainCounts[it] ?: 0 }.thenBy { it.ordinal })
            ?: return null

        return activeStates
            .filter { domainOf(it.category) == domain }
            .minWithOrNull(compareBy<CategoryState> { it.lastProbeOrder }.thenBy { it.category.ordinal })
    }

    private fun domainOf(category: AssessmentCategory): AssessmentDomain =
        when (category) {
            AssessmentCategory.LETTERS,
            AssessmentCategory.ANIMALS -> AssessmentDomain.LANGUAGE
            AssessmentCategory.NUMBERS -> AssessmentDomain.NUMERACY
            AssessmentCategory.COLORS,
            AssessmentCategory.SHAPES -> AssessmentDomain.MOTOR
        }

    private fun domainQuota(domain: AssessmentDomain): Int =
        when (domain) {
            AssessmentDomain.LANGUAGE -> 10
            AssessmentDomain.NUMERACY -> 6
            AssessmentDomain.MOTOR -> 4
        }

    private suspend fun hasLimitedContentPool(
        category: AssessmentCategory,
        level: Int
    ): Boolean {
        val availableContentIds = assessmentPlannerService.availableContentIds(category, level)
            .filterNot { it in warmUpContentIds }
        return availableContentIds.size in 1..2
    }

    private fun canRevisitLimitedPool(state: CategoryState): Boolean =
        probeOrder - state.lastProbeOrder >= LIMITED_POOL_REVISIT_GAP

    private fun <T> List<T>.rotate(offset: Int): List<T> {
        if (isEmpty()) return this
        val start = offset.mod(size)
        return drop(start) + take(start)
    }

    private fun probeKey(step: AssessmentLaunchStep): String =
        buildString {
            append(step.category?.name ?: "WARM_UP")
            append('|')
            append(step.level)
            append('|')
            append(step.activityId)
            append('|')
            append(step.contentId ?: "NO_CONTENT")
        }

}
