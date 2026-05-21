package com.babybloom.presentation.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babybloom.di.NormalSessionProgressStore
import com.babybloom.domain.algorithm.SessionPlannerService
import com.babybloom.domain.model.ActivityLaunchStep
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
            val savedProgress = normalSessionProgressStore.getForChild(childId)
            if (savedProgress != null) {
                val savedSession = sessionRepository.getSessionById(savedProgress.sessionId)
                val isActiveNormalSession = savedSession != null &&
                        savedSession.endTime == null &&
                        !savedSession.isAssessment
                val durationMs = child.sessionDurationMinutes * 60_000L
                val savedRemainingMs = savedProgress.remainingMs
                    ?: savedSession?.let { durationMs - (System.currentTimeMillis() - it.startTime) }
                val hasTimeRemaining = savedRemainingMs != null && savedRemainingMs > 0L
                val savedQueue = SessionQueueCodec.decode(savedProgress.encodedQueue)
                val savedStep = savedQueue.getOrNull(savedProgress.stepIndex)

                if (isActiveNormalSession && hasTimeRemaining && savedStep != null) {
                    _uiState.update { state ->
                        state.copy(
                            sessionQueue = savedQueue,
                            encodedQueue = savedProgress.encodedQueue,
                            sessionId = savedProgress.sessionId,
                            stepIndex = savedProgress.stepIndex
                        )
                    }
                    return@launch
                }
                if (isActiveNormalSession && !hasTimeRemaining) {
                    sessionRepository.endSession(
                        savedProgress.sessionId,
                        System.currentTimeMillis()
                    )
                }
                normalSessionProgressStore.clear()
            }

            val profile = childProfileRepository.getByChildId(childId) ?: return@launch
            val queue = sessionPlannerService.buildSessionSequence(profile)

            _uiState.update { state ->
                state.copy(
                    sessionQueue = queue,
                    encodedQueue = SessionQueueCodec.encode(queue),
                    sessionId = 0L,
                    stepIndex = 0
                )
            }
        }
    }
}
