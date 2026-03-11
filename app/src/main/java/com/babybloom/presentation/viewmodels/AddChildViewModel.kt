package com.babybloom.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babybloom.data.local.dao.ChildDao
import com.babybloom.data.local.entity.ChildEntity
import com.babybloom.di.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddChildUiState(
    val childName       : String   = "",
    val childAge        : String   = "",
    val hasDifficulties : Boolean  = false,
    val notes           : String   = "",
    val isGirlSelected  : Boolean? = null,
    val selectedAvatar  : String   = "",
    val nameError       : String?  = null,
    val ageError        : String?  = null,
    val genderError     : String?  = null,
    val avatarError     : String?  = null,
    val isLoading       : Boolean  = false,
    val isSaved         : Boolean  = false,
    val errorMessage    : String?  = null
)

@HiltViewModel
class AddChildViewModel @Inject constructor(
    private val childDao      : ChildDao,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddChildUiState())
    val uiState: StateFlow<AddChildUiState> = _uiState.asStateFlow()

    // ── REAL-TIME ──────────────────────────────────────────────────────────

    fun onNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(
            childName = name,
            nameError = validateName(name)
        )
    }

    fun onAgeChanged(age: String) {
        // Allow only digits, max 2 characters (range 1–20)
        val digitsOnly = age.filter { it.isDigit() }.take(2)
        _uiState.value = _uiState.value.copy(
            childAge = digitsOnly,
            ageError = validateAge(digitsOnly)
        )
    }

    fun onHasDifficultiesChanged(has: Boolean) {
        _uiState.value = _uiState.value.copy(
            hasDifficulties = has,
            notes = if (!has) "" else _uiState.value.notes  // clear notes when No
        )
    }

    fun onNotesChanged(notes: String) {
        _uiState.value = _uiState.value.copy(notes = notes)
    }

    fun onGenderChanged(isGirl: Boolean) {
        _uiState.value = _uiState.value.copy(
            isGirlSelected = isGirl,
            genderError    = null,
            selectedAvatar = ""
        )
    }

    fun onAvatarSelected(avatarPath: String) {
        _uiState.value = _uiState.value.copy(
            selectedAvatar = avatarPath,
            avatarError    = null
        )
    }

    // ── SAVE ───────────────────────────────────────────────────────────────

    fun saveChild() {
        if (!validateAllFields()) return

        val state = _uiState.value

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val userId = sessionManager.userId.first()

                if (userId == -1L) {
                    _uiState.value = _uiState.value.copy(
                        isLoading    = false,
                        errorMessage = "error_saving_child"
                    )
                    return@launch
                }

                // ── notes column stores two things: ────────────────────────
                // • If hasDifficulties = false → "" (empty, no difficulties)
                // • If hasDifficulties = true and notes blank → "has_difficulties"
                //   (user said yes but wrote nothing)
                // • If hasDifficulties = true and notes filled → the actual text
                //   (user described the difficulty)
                val notesValue = when {
                    !state.hasDifficulties       -> ""
                    state.notes.isBlank()        -> "has_difficulties"
                    else                         -> state.notes.trim()
                }

                val child = ChildEntity(
                    userId = userId,
                    name   = state.childName.trim(),
                    age    = state.childAge.toInt(),
                    notes  = notesValue,
                    avatar = state.selectedAvatar,
                    status = "ACTIVE"
                )

                childDao.insert(child)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isSaved   = true
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading    = false,
                    errorMessage = "error_saving_child"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    // ── VALIDATE ALL ───────────────────────────────────────────────────────

    private fun validateAllFields(): Boolean {
        val state = _uiState.value

        val nameError   = validateName(state.childName)
        val ageError    = validateAge(state.childAge)
        val genderError = if (state.isGirlSelected == null) "error_gender_required" else null
        val avatarError = if (state.selectedAvatar.isBlank()) "error_avatar_required" else null

        _uiState.value = _uiState.value.copy(
            nameError   = nameError,
            ageError    = ageError,
            genderError = genderError,
            avatarError = avatarError
        )

        return nameError == null && ageError == null &&
                genderError == null && avatarError == null
    }

    // ── VALIDATORS ─────────────────────────────────────────────────────────

    private fun validateName(name: String): String? = when {
        name.isBlank()                  -> "error_name_required"
        name.trim().length < 2          -> "error_name_too_short"
        name.trim().length > 50         -> "error_name_too_long"
        !name.matches(Regex("^[a-zA-Z\\u0600-\\u06FF\\s]+\$")) -> "error_name_invalid_chars"
        else                            -> null
    }

    private fun validateAge(age: String): String? = when {
        age.isBlank()             -> "error_age_required"
        age.toIntOrNull() == null -> "error_age_not_number"
        age.toInt() < 1           -> "error_age_too_low"
        age.toInt() > 20          -> "error_age_too_high"   // ← changed from 9 to 20
        else                      -> null
    }
}