package com.babybloom.presentation.viewmodels

import android.app.Application
import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babybloom.R
import com.babybloom.data.repository.AuthRepository
import com.babybloom.data.repository.AuthResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── UI State ─────────────────────────────────────────────────────────────────
data class LoginUiState(
    val email: String          = "",
    val password: String       = "",
    val emailError: String?    = null,
    val passwordError: String? = null,
    val isLoading: Boolean     = false,
    val isPasswordVisible: Boolean = false,
    val loginError: String?    = null,    // shown as Snackbar / inline error
    val navigateToHome: Boolean = false
)

// ─── ViewModel ────────────────────────────────────────────────────────────────
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val app: Application
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value, emailError = null, loginError = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, passwordError = null, loginError = null) }
    }

    fun onTogglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun onNavigationHandled() {
        _uiState.update { it.copy(navigateToHome = false) }
    }

    fun onLoginClick() {
        val state = _uiState.value

        // 1. Validate
        val emailError    = validateEmail(state.email)
        val passwordError = validatePassword(state.password)

        if (emailError != null || passwordError != null) {
            _uiState.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }

        // 2. Call repository
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loginError = null) }

            when (val result = authRepository.login(state.email, state.password)) {
                is AuthResult.Success -> {
                    _uiState.update { it.copy(isLoading = false, navigateToHome = true) }
                }
                is AuthResult.InvalidCredentials -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loginError = app.getString(R.string.error_invalid_credentials)
                        )
                    }
                }
                is AuthResult.Error -> {
                    _uiState.update {
                        it.copy(isLoading = false, loginError = result.message)
                    }
                }
            }
        }
    }

    // ─── Validation helpers ───────────────────────────────────────────────
    private fun validateEmail(email: String): String? = when {
        email.isBlank()                             -> app.getString(R.string.error_empty_email)
        !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> app.getString(R.string.error_invalid_email)
        else                                        -> null
    }

    private fun validatePassword(password: String): String? = when {
        password.isBlank()   -> app.getString(R.string.error_empty_password)
        password.length < 6  -> app.getString(R.string.error_short_password)
        else                 -> null
    }
}