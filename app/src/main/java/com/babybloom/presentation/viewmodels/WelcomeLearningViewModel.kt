package com.babybloom.presentation.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babybloom.di.NormalSessionProgressStore
import com.babybloom.domain.algorithm.SessionPlannerService
import com.babybloom.domain.model.ActivityLaunchStep
import com.babybloom.domain.model.SessionPhase
import com.babybloom.domain.repository.ChildProfileRepository
import com.babybloom.domain.repository.ChildRepository
import com.babybloom.domain.repository.SessionRepository
import com.babybloom.util.SessionQueueCodec
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WelcomeLearningUiState(
    val childId: Long = 0L,
    val childName: String = "",
    val isCalmMode: Boolean = false,
    val isLoading: Boolean = true,
    val sessionQueue: List<ActivityLaunchStep> = emptyList(),
    val encodedQueue: String = "",
    val sessionId: Long = 0L,
    val stepIndex: Int = 0
)

internal object RevisionContinuationPlanner {
    fun remainingOnly(remaining: List<ActivityLaunchStep>): List<ActivityLaunchStep> =
        remaining.filter { it.phase == SessionPhase.REVISION }

    private data class RevisionBatchPlan(
        val batches: List<LinkedHashSet<String>>,
        val batchIndexByQueueIndex: Map<Int, Int>
    )

    fun remainingCurrentBatchOnly(
        fullQueue: List<ActivityLaunchStep>,
        currentStepIndex: Int,
        batchSize: Int = 3
    ): List<ActivityLaunchStep> {
        val currentBatchIds = currentBatchContentIds(fullQueue, currentStepIndex, batchSize).toSet()
        if (currentBatchIds.isEmpty()) return emptyList()
        val contiguousRevisionTail = fullQueue
            .drop(currentStepIndex)
            .takeWhile { it.phase == SessionPhase.REVISION }
        return contiguousRevisionTail
            .filter { step ->
                step.phase == SessionPhase.REVISION &&
                    (step.targetContentId ?: step.contentId)?.removeSuffix("_s") in currentBatchIds
            }
    }

    private fun groupedRevisionBatches(
        fullQueue: List<ActivityLaunchStep>,
        batchSize: Int = 3
    ): RevisionBatchPlan {
        val grouped = mutableListOf<LinkedHashSet<String>>()
        val batchIndexByQueueIndex = mutableMapOf<Int, Int>()
        var currentBatch = linkedSetOf<String>()
        var currentBatchIndex = 0
        var hasSeenRevisionInCurrentBlock = false
        fullQueue.forEachIndexed { queueIndex, step ->
            if (step.phase != SessionPhase.REVISION) {
                if (hasSeenRevisionInCurrentBlock && currentBatch.isNotEmpty()) {
                    grouped += currentBatch
                    currentBatch = linkedSetOf()
                    currentBatchIndex++
                }
                hasSeenRevisionInCurrentBlock = false
                return@forEachIndexed
            }
            hasSeenRevisionInCurrentBlock = true
            val contentId = (step.targetContentId ?: step.contentId)?.removeSuffix("_s") ?: return@forEachIndexed
            if (contentId !in currentBatch && currentBatch.size >= batchSize) {
                grouped += currentBatch
                currentBatch = linkedSetOf()
                currentBatchIndex++
            }
            currentBatch += contentId
            batchIndexByQueueIndex[queueIndex] = currentBatchIndex
        }
        if (currentBatch.isNotEmpty()) grouped += currentBatch
        return RevisionBatchPlan(
            batches = grouped,
            batchIndexByQueueIndex = batchIndexByQueueIndex
        )
    }

    fun currentBatchContentIds(
        fullQueue: List<ActivityLaunchStep>,
        currentStepIndex: Int,
        batchSize: Int = 3
    ): List<String> {
        val plan = groupedRevisionBatches(fullQueue, batchSize)
        val batchIndex = plan.batchIndexByQueueIndex[currentStepIndex] ?: return emptyList()
        return plan.batches.getOrNull(batchIndex)?.toList().orEmpty()
    }

    fun isFirstBatch(
        fullQueue: List<ActivityLaunchStep>,
        currentStepIndex: Int,
        batchSize: Int = 3
    ): Boolean {
        val plan = groupedRevisionBatches(fullQueue, batchSize)
        return plan.batchIndexByQueueIndex[currentStepIndex] == 0
    }

    fun prependReplayBatchToFreshQueue(
        freshQueue: List<ActivityLaunchStep>,
        replayBatch: List<ActivityLaunchStep>,
        replayContentIds: Set<String>
    ): List<ActivityLaunchStep> {
        if (replayBatch.isEmpty()) return freshQueue
        val firstRevisionIndex = freshQueue.indexOfFirst { it.phase == SessionPhase.REVISION }
        if (firstRevisionIndex < 0) return freshQueue + replayBatch

        val beforeRevision = freshQueue.take(firstRevisionIndex)
        val freshRevision = freshQueue.drop(firstRevisionIndex)
            .filterNot { step -> (step.targetContentId ?: step.contentId)?.removeSuffix("_s") in replayContentIds }
        return beforeRevision + replayBatch + freshRevision
    }
}

@HiltViewModel
class WelcomeLearningViewModel @Inject constructor(
    private val childRepository: ChildRepository,
    private val childProfileRepository: ChildProfileRepository,
    private val sessionRepository: SessionRepository,
    private val sessionPlannerService: SessionPlannerService,
    private val normalSessionProgressStore: NormalSessionProgressStore,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val childId: Long = checkNotNull(savedStateHandle["childId"])

    private val _uiState = MutableStateFlow(WelcomeLearningUiState())
    val uiState: StateFlow<WelcomeLearningUiState> = _uiState.asStateFlow()

    init {
        observeChild()
    }

    private fun observeChild() {
        viewModelScope.launch {
            childRepository.observeById(childId).collect { child ->
                _uiState.update { state ->
                    state.copy(
                        childId = child?.id ?: state.childId,
                        childName = child?.name ?: state.childName,
                        isCalmMode = child?.uiTheme ?: state.isCalmMode,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun prepareSession() {
        viewModelScope.launch {
            val child = childRepository.getById(childId) ?: return@launch
            val profile = childProfileRepository.getByChildId(childId) ?: return@launch
            val freshQueue = sessionPlannerService.buildSessionSequence(profile)
            val savedProgress = normalSessionProgressStore.getForChild(childId)
            if (savedProgress != null) {
                val savedSession = sessionRepository.getSessionById(savedProgress.sessionId)
                val savedQueue = SessionQueueCodec.decode(savedProgress.encodedQueue)
                val savedStep = savedQueue.getOrNull(savedProgress.stepIndex)

                if (savedSession?.endTime == null && savedSession?.isAssessment == false) {
                    sessionRepository.endSession(savedProgress.sessionId, System.currentTimeMillis())
                }

                if (savedStep != null) {
                    val (resumeQueue, resumeIndex) = when (savedStep.phase) {
                        SessionPhase.LEARNING -> savedQueue to
                            savedQueue.indexOfFirst { it.phase == SessionPhase.LEARNING }.coerceAtLeast(0)
                        SessionPhase.TEST -> savedQueue to savedProgress.stepIndex
                        SessionPhase.REVISION -> {
                            if (RevisionContinuationPlanner.isFirstBatch(savedQueue, savedProgress.stepIndex)) {
                                val remainingRevision = revisionCurrentBatchContinuation(
                                    fullQueue = savedQueue,
                                    stepIndex = savedProgress.stepIndex
                                )
                                if (remainingRevision.isEmpty()) freshQueue to 0
                                else (remainingRevision + freshQueue) to 0
                            } else {
                                val batchContentIds = RevisionContinuationPlanner.currentBatchContentIds(
                                    fullQueue = savedQueue,
                                    currentStepIndex = savedProgress.stepIndex
                                )
                                val replayBatch = sessionPlannerService.buildRevisionStepsForContentIds(
                                    profile = profile,
                                    contentIds = batchContentIds
                                )
                                val mergedQueue = RevisionContinuationPlanner.prependReplayBatchToFreshQueue(
                                    freshQueue = freshQueue,
                                    replayBatch = replayBatch,
                                    replayContentIds = batchContentIds.toSet()
                                )
                                mergedQueue to 0
                            }
                        }
                    }
                    _uiState.update { state ->
                        state.copy(
                            sessionQueue = resumeQueue,
                            encodedQueue = SessionQueueCodec.encode(resumeQueue),
                            sessionId = 0L,
                            stepIndex = resumeIndex
                        )
                    }
                    normalSessionProgressStore.clear()
                    return@launch
                }
                normalSessionProgressStore.clear()
            }

            _uiState.update { state ->
                state.copy(
                    sessionQueue = freshQueue,
                    encodedQueue = SessionQueueCodec.encode(freshQueue),
                    sessionId = 0L,
                    stepIndex = 0
                )
            }
        }
    }

    private fun revisionContinuation(remaining: List<ActivityLaunchStep>): List<ActivityLaunchStep> =
        RevisionContinuationPlanner.remainingOnly(remaining)

    private fun revisionCurrentBatchContinuation(
        fullQueue: List<ActivityLaunchStep>,
        stepIndex: Int
    ): List<ActivityLaunchStep> =
        RevisionContinuationPlanner.remainingCurrentBatchOnly(fullQueue, stepIndex)
}
