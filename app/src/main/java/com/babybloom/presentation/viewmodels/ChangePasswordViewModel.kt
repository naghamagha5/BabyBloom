package com.babybloom.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babybloom.data.local.dao.UserDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChangePasswordUiState(
    val fullName             : String  = "",
    val email                : String  = "",
    val newPassword          : String  = "",
    val confirmPassword      : String  = "",
    val newPasswordVisible   : Boolean = false,
    val confirmPwdVisible    : Boolean = false,
    val nameError            : String? = null,
    val emailError           : String? = null,
    val newPasswordError     : String? = null,
    val confirmPasswordError : String? = null,
    val isLoading            : Boolean = false,
    val isSuccess            : Boolean = false,
    val errorMessage         : String? = null
)

@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val userDao: UserDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChangePasswordUiState())
    val uiState: StateFlow<ChangePasswordUiState> = _uiState.asStateFlow()

    // REAL-TIME
    fun onNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(fullName = name, nameError = validateName(name))
    }

    fun onEmailChanged(email: String) {
        _uiState.value = _uiState.value.copy(email = email, emailError = validateEmail(email))
    }

    fun onNewPasswordChanged(password: String) {
        val confirm = _uiState.value.confirmPassword
        _uiState.value = _uiState.value.copy(
            newPassword      = password,
            newPasswordError = validatePassword(password),
            confirmPasswordError = if (confirm.isNotBlank())
                validateConfirmPassword(password, confirm) else null
        )
    }

    fun onConfirmPasswordChanged(confirmPassword: String) {
        _uiState.value = _uiState.value.copy(
            confirmPassword      = confirmPassword,
            confirmPasswordError = validateConfirmPassword(_uiState.value.newPassword, confirmPassword)
        )
    }

    fun toggleNewPasswordVisibility() {
        _uiState.value = _uiState.value.copy(newPasswordVisible = !_uiState.value.newPasswordVisible)
    }

    fun toggleConfirmPasswordVisibility() {
        _uiState.value = _uiState.value.copy(confirmPwdVisible = !_uiState.value.confirmPwdVisible)
    }

    // ON BUTTON CLICK
    fun saveNewPassword() {
        if (!validateAllFields()) return
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                // Step 1 — find user by email
                val user = userDao.getByEmail(state.email.trim().lowercase())
                if (user == null) {
                    _uiState.value = _uiState.value.copy(isLoading = false, emailError = "error_email_not_found")
                    return@launch
                }
                // Step 2 — verify name matches the registered account
                if (!user.name.equals(state.fullName.trim(), ignoreCase = true)) {
                    _uiState.value = _uiState.value.copy(isLoading = false, nameError = "error_name_mismatch")
                    return@launch
                }
                // Step 3 — new password must differ from current
                val newHash = hashPassword(state.newPassword)
                if (newHash == user.passwordHash) {
                    _uiState.value = _uiState.value.copy(isLoading = false, newPasswordError = "error_password_same_as_old")
                    return@launch
                }
                // Step 4 — update Room
                userDao.update(user.copy(passwordHash = newHash))
                _uiState.value = _uiState.value.copy(isLoading = false, isSuccess = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "error_unexpected")
            }
        }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(errorMessage = null) }

    private fun validateAllFields(): Boolean {
        val s = _uiState.value
        val ne = validateName(s.fullName)
        val ee = validateEmail(s.email)
        val pe = validatePassword(s.newPassword)
        val ce = validateConfirmPassword(s.newPassword, s.confirmPassword)
        _uiState.value = _uiState.value.copy(nameError = ne, emailError = ee, newPasswordError = pe, confirmPasswordError = ce)
        return ne == null && ee == null && pe == null && ce == null
    }

    private fun validateName(name: String): String? = when {
        name.isBlank()                  -> "error_name_required"
        name.trim().length < 3          -> "error_name_too_short"
        name.trim().length > 50         -> "error_name_too_long"
        !name.matches(Regex("^[a-zA-Z\\u0600-\\u06FF\\s]+\$")) -> "error_name_invalid_chars"
        else -> null
    }

    private fun validateEmail(email: String): String? = when {
        email.isBlank()    -> "error_email_required"
        email.contains(" ") -> "error_email_has_spaces"
        !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "error_email_invalid_format"
        email.length > 100 -> "error_email_too_long"
        else -> null
    }

    private fun validatePassword(password: String): String? = when {
        password.isBlank()                  -> "error_password_required"
        password.length < 8                 -> "error_password_too_short"
        password.length > 32                -> "error_password_too_long"
        !password.any { it.isUpperCase() }  -> "error_password_no_uppercase"
        !password.any { it.isLowerCase() }  -> "error_password_no_lowercase"
        !password.any { it.isDigit() }      -> "error_password_no_digit"
        !password.any { it in "!@#\$%^&*()_+-=[]{}|;':\",./<>?" } -> "error_password_no_special"
        password.contains(" ")              -> "error_password_has_spaces"
        else -> null
    }

    private fun validateConfirmPassword(password: String, confirm: String): String? = when {
        confirm.isBlank()   -> "error_confirm_required"
        confirm != password -> "error_confirm_mismatch"
        else -> null
    }

    private fun hashPassword(password: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val bytes  = digest.digest(password.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}