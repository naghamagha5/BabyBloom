package com.babybloom.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babybloom.di.SessionManager
import com.babybloom.domain.repository.ChildRepository
import com.babybloom.presentation.screens.ChildStatus
import com.babybloom.presentation.screens.ChildUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─────────────────────────────────────────────
//  UI State
// ─────────────────────────────────────────────
data class MyChildrenUiState(
    val isLoading: Boolean = true,
    val children: List<ChildUiModel> = emptyList(),
    val errorMessage: String? = null
)

// ─────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────
@HiltViewModel
class MyChildrenViewModel @Inject constructor(
    private val childRepository: ChildRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyChildrenUiState())
    val uiState: StateFlow<MyChildrenUiState> = _uiState

    init {
        loadChildren()
    }

    private fun loadChildren() {
        viewModelScope.launch {
            try {
                // Get current logged in userId from SessionManager
                val userId = sessionManager.userId.first()

                if (userId == -1L) {
                    // No user logged in
                    _uiState.value = MyChildrenUiState(
                        isLoading = false,
                        errorMessage = "لم يتم تسجيل الدخول"
                    )
                    return@launch
                }

                // Observe children from Room database
                childRepository.getChildrenByUser(userId).collectLatest { children ->
                    _uiState.value = MyChildrenUiState(
                        isLoading = false,
                        children = children.map { child ->
                            ChildUiModel(
                                id = child.id,
                                name = child.name,
                                ageYears = child.age,
                                // TODO: replace with real progressPercent
                                // when algorithm is ready in database
                                progressPercent = 0,
                                // TODO: replace with child.status when
                                // database is updated to support NEEDS_SUPPORT
                                status = if (child.uiTheme) ChildStatus.CALM
                                else ChildStatus.ACTIVE,
                                avatarRes = resolveAvatar(child.avatar)
                            )
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.value = MyChildrenUiState(
                    isLoading = false,
                    errorMessage = e.message
                )
            }
        }
    }

    // ─────────────────────────────────────────────
    //  Maps avatar string path → drawable resource
    //  TODO: update mapping when more avatars are added
    // ─────────────────────────────────────────────
    private fun resolveAvatar(avatar: String): Int {
        return when (avatar) {
            "avatars/girl_1.webp" -> com.babybloom.R.drawable.avatar_girl_1
            "avatars/girl_2.webp" -> com.babybloom.R.drawable.avatar_girl_2
            "avatars/girl_3.webp" -> com.babybloom.R.drawable.avatar_girl_3
            "avatars/girl_4.webp" -> com.babybloom.R.drawable.avatar_girl_4
            "avatars/girl_5.webp" -> com.babybloom.R.drawable.avatar_girl_5
            "avatars/girl_6.webp" -> com.babybloom.R.drawable.avatar_girl_6
            "avatars/boy_1.webp"  -> com.babybloom.R.drawable.avatar_boy_1
            "avatars/boy_2.webp"  -> com.babybloom.R.drawable.avatar_boy_2
            "avatars/boy_3.webp"  -> com.babybloom.R.drawable.avatar_boy_3
            "avatars/boy_4.webp"  -> com.babybloom.R.drawable.avatar_boy_4
            "avatars/boy_5.webp"  -> com.babybloom.R.drawable.avatar_boy_5
            "avatars/boy_6.webp"  -> com.babybloom.R.drawable.avatar_boy_6
            else                  -> com.babybloom.R.drawable.avatar_girl_1
        }
    }
}