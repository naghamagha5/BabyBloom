package com.babybloom.presentation.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babybloom.domain.repository.ChildRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WelcomeLearningUiState(
    val childName  : String  = "",
    val isCalmMode : Boolean = false,   // mirrors Child.uiTheme  (false = ACTIVE, true = CALM)
    val isLoading  : Boolean = true
)

@HiltViewModel
class WelcomeLearningViewModel @Inject constructor(
    private val childRepository: ChildRepository,
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
                        childName  = child?.name  ?: state.childName,
                        isCalmMode = child?.uiTheme ?: state.isCalmMode,
                        isLoading  = false
                    )
                }
            }
        }
    }
}