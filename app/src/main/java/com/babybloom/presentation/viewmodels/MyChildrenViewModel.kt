package com.babybloom.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babybloom.R
import com.babybloom.di.SessionManager
import com.babybloom.domain.repository.ChildRepository
import com.babybloom.domain.repository.ChildProfileRepository
import com.babybloom.presentation.screens.ChildUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MyChildrenUiState(
    val isLoading: Boolean = true,
    val children: List<ChildUiModel> = emptyList(),
    val errorMessage: String? = null
)

@HiltViewModel
class MyChildrenViewModel @Inject constructor(
    private val childRepository        : ChildRepository,
    private val childProfileRepository : ChildProfileRepository,
    private val sessionManager         : SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyChildrenUiState())
    val uiState: StateFlow<MyChildrenUiState> = _uiState

    init { loadChildren() }

    private fun loadChildren() {
        viewModelScope.launch {
            try {
                val userId = sessionManager.userId.first()
                if (userId == -1L) {
                    _uiState.value = MyChildrenUiState(
                        isLoading    = false,
                        errorMessage = "لم يتم تسجيل الدخول"
                    )
                    return@launch
                }

                childRepository.getChildrenByUser(userId).collectLatest { children ->
                    val uiModels = children.map { child ->
                        val profile = childProfileRepository.getByChildId(child.id)

                        val progressPercent = if (profile != null) {
                            // Average the three skill progress bars (each 0.0–1.0)
                            // then blend with normalized level (levels 1–5 → 0.0–1.0)
                            val progressAvg = (profile.languageProgress +
                                    profile.numeracyProgress  +
                                    profile.motorProgress) / 3f

                            val levelAvg = (profile.languageLevel +
                                    profile.numeracyLevel  +
                                    profile.motorLevel).toFloat() / 15f  // max = 5+5+5

                            ((progressAvg * 0.7f + levelAvg * 0.3f) * 100)
                                .toInt()
                                .coerceIn(0, 100)
                        } else 0

                        ChildUiModel(
                            id              = child.id,
                            name            = child.name,
                            ageYears        = child.age,
                            progressPercent = progressPercent,
                            status          = child.status,
                            avatarRes       = resolveAvatar(child.avatar)
                        )
                    }
                    _uiState.value = MyChildrenUiState(isLoading = false, children = uiModels)
                }
            } catch (e: Exception) {
                _uiState.value = MyChildrenUiState(
                    isLoading    = false,
                    errorMessage = e.message
                )
            }
        }
    }

    private fun resolveAvatar(avatar: String): Int = when (avatar) {
        "avatars/girl_1.webp", "avatar_girl_1" -> R.drawable.avatar_girl_1
        "avatars/girl_2.webp", "avatar_girl_2" -> R.drawable.avatar_girl_2
        "avatars/girl_3.webp", "avatar_girl_3" -> R.drawable.avatar_girl_3
        "avatars/girl_4.webp", "avatar_girl_4" -> R.drawable.avatar_girl_4
        "avatars/girl_5.webp", "avatar_girl_5" -> R.drawable.avatar_girl_5
        "avatars/girl_6.webp", "avatar_girl_6" -> R.drawable.avatar_girl_6
        "avatars/boy_1.webp",  "avatar_boy_1"  -> R.drawable.avatar_boy_1
        "avatars/boy_2.webp",  "avatar_boy_2"  -> R.drawable.avatar_boy_2
        "avatars/boy_3.webp",  "avatar_boy_3"  -> R.drawable.avatar_boy_3
        "avatars/boy_4.webp",  "avatar_boy_4"  -> R.drawable.avatar_boy_4
        "avatars/boy_5.webp",  "avatar_boy_5"  -> R.drawable.avatar_boy_5
        "avatars/boy_6.webp",  "avatar_boy_6"  -> R.drawable.avatar_boy_6
        else                                    -> R.drawable.avatar_girl_1
    }
}