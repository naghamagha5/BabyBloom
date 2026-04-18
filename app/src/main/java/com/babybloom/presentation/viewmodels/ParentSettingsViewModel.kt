package com.babybloom.presentation.viewmodels

import android.content.Context
import android.media.SoundPool
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babybloom.R
import com.babybloom.data.local.dao.ChildDao
import com.babybloom.data.local.dao.UserDao
import com.babybloom.di.AppSoundSettings
import com.babybloom.di.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
// DATA MODELS
// ─────────────────────────────────────────────────────────────────────────────

data class EditProfileState(
    val name           : String  = "",
    val email          : String  = "",
    val nameError      : String? = null,
    val emailError     : String? = null,
    val isLoading      : Boolean = false,
    val successMessage : String? = null,
    val errorMessage   : String? = null
)

data class ParentUiState(
    val showEditDialog      : Boolean = false,
    val showLogoutDialog    : Boolean = false,
    val showDeleteDialog    : Boolean = false,
    val isLoading           : Boolean = false,
    val navigateToLogin     : Boolean = false,
    val navigateToChangePwd : Boolean = false,
    val errorMessage        : String? = null,
    val successMessage      : String? = null
)

// ─────────────────────────────────────────────────────────────────────────────
// VIEW MODEL
// ─────────────────────────────────────────────────────────────────────────────

@HiltViewModel
class ParentViewModel @Inject constructor(
    private val sessionManager : SessionManager,
    private val userDao        : UserDao,
    private val childDao       : ChildDao,
    @ApplicationContext private val context: Context,
    private val appSoundSettings: AppSoundSettings,
    ) : ViewModel() {

    // ── Session data ─────────────────────────────────────────────────────────
    val userName: StateFlow<String> = sessionManager.userName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val userEmail: StateFlow<String> = sessionManager.userEmail
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val createdAt: StateFlow<Long> = sessionManager.createdAt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    // ── Child count ───────────────────────────────────────────────────────────
    val childCount: StateFlow<Int> = sessionManager.userId
        .flatMapLatest { uid ->
            if (uid == -1L) kotlinx.coroutines.flow.flowOf(emptyList())
            else childDao.getChildrenByUser(uid)
        }
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)


    // ── UI state ──────────────────────────────────────────────────────────────
    private val _uiState = MutableStateFlow(ParentUiState())
    val uiState: StateFlow<ParentUiState> = _uiState.asStateFlow()

    private val _editState = MutableStateFlow(EditProfileState())
    val editState: StateFlow<EditProfileState> = _editState.asStateFlow()

    // ── Settings toggles ──────────────────────────────────────────────────────
    val notificationsEnabled = MutableStateFlow(true)
    val soundEnabled get() = appSoundSettings.soundEnabled
    val musicEnabled         = MutableStateFlow(true)

    fun toggleNotifications(enabled: Boolean) {
        notificationsEnabled.value = enabled
        playButtonSound()
    }

    fun toggleSound(enabled: Boolean) {
        playButtonSound()
        appSoundSettings.soundEnabled.value = enabled
    }

    fun toggleMusic(enabled: Boolean) {
        musicEnabled.value = enabled
        playButtonSound()
    }

    // ── Edit Profile Dialog ───────────────────────────────────────────────────
    fun openEditDialog() {
        _editState.value = EditProfileState(
            name  = userName.value,
            email = userEmail.value
        )
        _uiState.value = _uiState.value.copy(showEditDialog = true)
    }

    fun closeEditDialog() {
        _uiState.value  = _uiState.value.copy(showEditDialog = false)
        _editState.value = EditProfileState()
    }

    fun onEditNameChanged(value: String) {
        _editState.value = _editState.value.copy(name = value, nameError = null)
    }

    fun onEditEmailChanged(value: String) {
        _editState.value = _editState.value.copy(email = value, emailError = null)
    }

    fun saveProfileChanges() {
        val state    = _editState.value
        val newName  = state.name.trim()
        val newEmail = state.email.trim()

        var nameError  : String? = null
        var emailError : String? = null

        if (newName.isBlank()) {
            nameError = context.getString(R.string.error_name_required)
        } else if (newName.length < 3) {
            nameError = context.getString(R.string.error_name_min_length)
        } else if (newName.length > 50) {
            nameError = context.getString(R.string.error_name_max_length)
        } else if (!newName.all { it.isLetter() || it.isWhitespace() }) {
            nameError = context.getString(R.string.error_name_invalid_characters)
        }

        if (newEmail.isBlank()) {
            emailError = context.getString(R.string.error_email_required)
        } else if (newEmail.contains(" ")) {
            emailError = context.getString(R.string.error_email_no_spaces)
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) {
            emailError = context.getString(R.string.error_email_invalid_format)
        } else if (newEmail.length > 100) {
            emailError = context.getString(R.string.error_email_too_long)
        }

        if (nameError != null || emailError != null) {
            _editState.value = _editState.value.copy(
                nameError  = nameError,
                emailError = emailError
            )
            return
        }

        if (newName == userName.value && newEmail == userEmail.value) {
            _editState.value = _editState.value.copy(
                errorMessage = context.getString(R.string.error_no_changes)
            )
            return
        }

        viewModelScope.launch {
            _editState.value = _editState.value.copy(isLoading = true)
            try {
                val userId = sessionManager.userId.first()
                val user   = userDao.getById(userId)

                if (user == null) {
                    _editState.value = _editState.value.copy(
                        isLoading    = false,
                        errorMessage = context.getString(R.string.error_generic_unexpected)
                    )
                    return@launch
                }

                if (newEmail != user.email) {
                    val existingUser = userDao.getByEmail(newEmail)
                    if (existingUser != null && existingUser.id != userId) {
                        _editState.value = _editState.value.copy(
                            isLoading  = false,
                            emailError = context.getString(R.string.error_email_taken_by_other)
                        )
                        return@launch
                    }
                }

                val updatedUser = user.copy(name = newName, email = newEmail)
                userDao.update(updatedUser)
                sessionManager.saveSession(userId, newName, newEmail)

                _editState.value = _editState.value.copy(
                    isLoading      = false,
                    successMessage = context.getString(R.string.edit_profile_success)
                )

                kotlinx.coroutines.delay(800)
                closeEditDialog()

            } catch (e: Exception) {
                _editState.value = _editState.value.copy(
                    isLoading    = false,
                    errorMessage = context.getString(R.string.error_generic_unexpected)
                )
            }
        }
    }

    fun clearEditMessages() {
        _editState.value = _editState.value.copy(
            errorMessage   = null,
            successMessage = null
        )
    }

    // ── Logout ────────────────────────────────────────────────────────────────
    fun showLogoutDialog()    { _uiState.value = _uiState.value.copy(showLogoutDialog = true) }
    fun dismissLogoutDialog() { _uiState.value = _uiState.value.copy(showLogoutDialog = false) }

    fun confirmLogout() {
        viewModelScope.launch {
            sessionManager.clearSession()
            _uiState.value = _uiState.value.copy(
                showLogoutDialog = false,
                navigateToLogin  = true
            )
        }
    }

    // ── Delete Account ────────────────────────────────────────────────────────
    fun showDeleteDialog()    { _uiState.value = _uiState.value.copy(showDeleteDialog = true) }
    fun dismissDeleteDialog() { _uiState.value = _uiState.value.copy(showDeleteDialog = false) }

    fun confirmDeleteAccount() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val userId   = sessionManager.userId.first()
                val children = childDao.getChildrenByUser(userId).first()
                children.forEach { childDao.delete(it) }
                userDao.deleteUser(userId)
                sessionManager.clearSession()

                _uiState.value = _uiState.value.copy(
                    isLoading        = false,
                    showDeleteDialog = false,
                    navigateToLogin  = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading        = false,
                    showDeleteDialog = false,
                    errorMessage     = context.getString(R.string.error_delete_account)
                )
            }
        }
    }

    // ── Change Password ───────────────────────────────────────────────────────
    fun onChangePasswordClick() {
        _uiState.value = _uiState.value.copy(navigateToChangePwd = true)
    }

    fun onNavigationHandled() {
        _uiState.value = _uiState.value.copy(
            navigateToLogin     = false,
            navigateToChangePwd = false
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    fun formatJoinDate(timestamp: Long): String {
        val time = if (timestamp == 0L) System.currentTimeMillis() else timestamp
        return SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(Date(time))
    }

    // ── Sound Pool ────────────────────────────────────────────────────────────
    private val soundPool     = SoundPool.Builder().setMaxStreams(3).build()
    private var buttonSoundId : Int     = 0
    private var soundLoaded   : Boolean = false

    init {
        soundPool.setOnLoadCompleteListener { _, _, status -> soundLoaded = status == 0 }
        buttonSoundId = soundPool.load(context, R.raw.button_one, 1)
    }

    fun playButtonSound() {
        if (soundEnabled.value && soundLoaded) {
            soundPool.play(buttonSoundId, 1f, 1f, 0, 0, 1f)
        }
    }

    override fun onCleared() {
        super.onCleared()
        soundPool.release()
    }
}
