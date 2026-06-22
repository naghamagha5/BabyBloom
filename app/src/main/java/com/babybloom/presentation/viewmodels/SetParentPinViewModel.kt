package com.babybloom.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babybloom.di.SessionManager
import com.babybloom.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SetParentPinUiState(
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class SetParentPinViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetParentPinUiState())
    val uiState: StateFlow<SetParentPinUiState> = _uiState.asStateFlow()

    fun savePin(pin: String, confirmPin: String, onSaved: () -> Unit) {
        val error = when {
            pin.length != 4 || pin.any { !it.isDigit() } -> "يجب أن يتكون الرقم السري من 4 أرقام"
            pin != confirmPin -> "الرقمان غير متطابقين"
            else -> null
        }

        if (error != null) {
            _uiState.value = _uiState.value.copy(errorMessage = error)
            return
        }

        viewModelScope.launch {
            _uiState.value = SetParentPinUiState(isSaving = true)
            runCatching {
                val userId = sessionManager.userId.first()
                check(userId >= 0) { "No registered parent session" }
                userRepository.setParentLockPin(userId, pin)
            }.onSuccess {
                _uiState.value = SetParentPinUiState()
                onSaved()
            }.onFailure {
                _uiState.value = SetParentPinUiState(
                    errorMessage = "تعذر حفظ الرقم السري. حاول مرة أخرى"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
