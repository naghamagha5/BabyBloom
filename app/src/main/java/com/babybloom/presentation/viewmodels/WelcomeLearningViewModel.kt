package com.babybloom.presentation.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babybloom.domain.algorithm.SessionPlannerService
import com.babybloom.domain.model.ActivityLaunchStep
import com.babybloom.domain.repository.ChildProfileRepository
import com.babybloom.domain.repository.ChildRepository
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
    val encodedQueue: String = ""
)

@HiltViewModel
class WelcomeLearningViewModel @Inject constructor(
    private val childRepository: ChildRepository,
    private val childProfileRepository: ChildProfileRepository,
    private val sessionPlannerService: SessionPlannerService,
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
            val profile = childProfileRepository.getByChildId(childId) ?: return@launch
            val queue = sessionPlannerService.buildSessionSequence(profile)

            _uiState.update { state ->
                state.copy(
                    sessionQueue = queue,
                    encodedQueue = SessionQueueCodec.encode(queue)
                )
            }
        }
    }
}
