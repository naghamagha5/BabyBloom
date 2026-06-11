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
                            val alreadyRevised = savedQueue
                                .take(savedProgress.stepIndex)
                                .filter { it.phase == SessionPhase.REVISION }
                                .mapNotNull { it.contentId?.removeSuffix("_s") }
                                .toSet()
                            if (alreadyRevised.size >= 3) {
                                freshQueue to 0
                            } else {
                                val required = 3 - alreadyRevised.size
                                val continuation = revisionContinuation(
                                    savedQueue.drop(savedProgress.stepIndex),
                                    alreadyRevised,
                                    required
                                )
                                (continuation + freshQueue) to 0
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

    private fun revisionContinuation(
        remaining: List<ActivityLaunchStep>,
        alreadyRevised: Set<String>,
        requiredNewIds: Int
    ): List<ActivityLaunchStep> {
        if (requiredNewIds <= 0) return emptyList()
        val seen = alreadyRevised.toMutableSet()
        var newIds = 0
        var endExclusive = 0
        remaining.forEachIndexed { index, step ->
            if (step.phase != SessionPhase.REVISION) return@forEachIndexed
            val id = step.contentId?.removeSuffix("_s")
            if (id != null && seen.add(id)) newIds++
            endExclusive = index + 1
            if (newIds >= requiredNewIds) return remaining.take(endExclusive)
        }
        return remaining.filter { it.phase == SessionPhase.REVISION }
    }
}
