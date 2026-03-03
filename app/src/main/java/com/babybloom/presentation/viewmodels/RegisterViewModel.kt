package com.babybloom.presentation.viewmodels

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babybloom.R
import com.babybloom.data.repository.AuthRepository
import com.babybloom.data.repository.AuthResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RegisterUiState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null,
    val nameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null
)

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val repository: AuthRepository,
    private val app: Application
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    // ── REAL-TIME: called as user types ───────────────────────────────────
    fun onNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(nameError = validateName(name))
    }

    fun onEmailChanged(email: String) {
        _uiState.value = _uiState.value.copy(emailError = validateEmail(email))
    }

    fun onPasswordChanged(password: String, confirmPassword: String) {
        _uiState.value = _uiState.value.copy(
            passwordError = validatePassword(password),
            confirmPasswordError = if (confirmPassword.isNotBlank())
                validateConfirmPassword(password, confirmPassword) else null
        )
    }

    fun onConfirmPasswordChanged(password: String, confirmPassword: String) {
        _uiState.value = _uiState.value.copy(
            confirmPasswordError = validateConfirmPassword(password, confirmPassword)
        )
    }

    // ── ON BUTTON CLICK: validate all then save to Room ───────────────────
    fun register(
        name: String,
        email: String,
        password: String,
        confirmPassword: String
    ) {
        if (!validateAllFields(name, email, password, confirmPassword)) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            when (val result = repository.register(name, email, password)) {
                is AuthResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isSuccess = true
                    )
                }
                is AuthResult.Error -> {
                    when (result.message) {
                        "EMAIL_EXISTS" -> {
                            _uiState.value = _uiState.value.copy(
                                isLoading  = false,
                                emailError = app.getString(R.string.error_email_already_exists)
                            )
                        }
                        else -> {
                            _uiState.value = _uiState.value.copy(
                                isLoading    = false,
                                errorMessage = app.getString(R.string.error_generic_unexpected)
                            )
                        }
                    }
                }
                is AuthResult.InvalidCredentials -> {
                    // register() never returns this but the when must be exhaustive
                    _uiState.value = _uiState.value.copy(
                        isLoading    = false,
                        errorMessage = app.getString(R.string.error_generic_unexpected)
                    )
                }
            }
        }
    }

    // ── Validate all at once (used on button click) ────────────────────────
    private fun validateAllFields(
        name: String,
        email: String,
        password: String,
        confirmPassword: String
    ): Boolean {
        val nameError            = validateName(name)
        val emailError           = validateEmail(email)
        val passwordError        = validatePassword(password)
        val confirmPasswordError = validateConfirmPassword(password, confirmPassword)

        _uiState.value = _uiState.value.copy(
            nameError            = nameError,
            emailError           = emailError,
            passwordError        = passwordError,
            confirmPasswordError = confirmPasswordError
        )

        return nameError == null && emailError == null &&
                passwordError == null && confirmPasswordError == null
    }

    // ── Individual validators ──────────────────────────────────────────────
    private fun validateName(name: String): String? = when {
        name.isBlank()                -> app.getString(R.string.error_name_required)
        name.trim().length < 3        -> app.getString(R.string.error_name_min_length)
        name.trim().length > 50       -> app.getString(R.string.error_name_max_length)
        !name.matches(Regex("^[a-zA-Z\\u0600-\\u06FF\\s]+\$")) ->
            app.getString(R.string.error_name_invalid_characters)
        else -> null
    }

    private fun validateEmail(email: String): String? = when {
        email.isBlank()               -> app.getString(R.string.error_email_required)
        email.contains(" ")           -> app.getString(R.string.error_email_no_spaces)
        !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() ->
            app.getString(R.string.error_email_invalid_format)
        email.length > 100            -> app.getString(R.string.error_email_too_long)
        else -> null
    }

    private fun validatePassword(password: String): String? = when {
        password.isBlank()            -> app.getString(R.string.error_password_required)
        password.length < 8           -> app.getString(R.string.error_password_min_length)
        password.length > 32          -> app.getString(R.string.error_password_max_length)
        !password.any { it.isUpperCase() } -> app.getString(R.string.error_password_no_uppercase)
        !password.any { it.isLowerCase() } -> app.getString(R.string.error_password_no_lowercase)
        !password.any { it.isDigit() }     -> app.getString(R.string.error_password_no_digit)
        !password.any { it in "!@#\$%^&*()_+-=[]{}|;':\",./<>?" } ->
            app.getString(R.string.error_password_no_special_char)
        password.contains(" ")        -> app.getString(R.string.error_password_no_spaces)
        else -> null
    }

    private fun validateConfirmPassword(password: String, confirmPassword: String): String? = when {
        confirmPassword.isBlank()     -> app.getString(R.string.error_confirm_password_required)
        confirmPassword != password   -> app.getString(R.string.error_passwords_mismatch)
        else -> null
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}