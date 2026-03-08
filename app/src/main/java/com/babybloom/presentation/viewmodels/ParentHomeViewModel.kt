package com.babybloom.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ParentHomeUiState(
    val isLoading: Boolean = false,
    val totalChildren: Int = 3,
    val activeChildrenToday: Int = 2,
    val childrenNeedingSupport: Int = 1,
    val weeklyAchievementsCount: Int = 18,
    val totalPointsThisWeek: Int = 47,
    val weeklyProgressPercentage: Double = 12.0,
    val aiInsightMessage: String = "أظهر 3 أطفال تحسّنًا في مستوى التركيز مع أنشطة الصباح.",
    val error: String? = null
)

class ParentHomeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ParentHomeUiState())
    val uiState: StateFlow<ParentHomeUiState> = _uiState.asStateFlow()

    init {
        loadHomeScreenData()
    }

    fun loadHomeScreenData() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)

                // Simulate data loading
                kotlinx.coroutines.delay(500)

                // Mock data for now - replace with actual API calls later
                val activeChildren = _uiState.value.activeChildrenToday
                val aiMessage = generateAIInsight(activeChildren)

                _uiState.value = ParentHomeUiState(
                    isLoading = false,
                    totalChildren = _uiState.value.totalChildren,
                    activeChildrenToday = activeChildren,
                    childrenNeedingSupport = _uiState.value.childrenNeedingSupport,
                    weeklyAchievementsCount = _uiState.value.weeklyAchievementsCount,
                    totalPointsThisWeek = _uiState.value.totalPointsThisWeek,
                    weeklyProgressPercentage = _uiState.value.weeklyProgressPercentage,
                    aiInsightMessage = aiMessage
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "حدث خطأ غير متوقع"
                )
            }
        }
    }

    private fun generateAIInsight(activeChildrenToday: Int): String {
        return when {
            activeChildrenToday >= 3 -> "أظهر 3 أطفال تحسّنًا في مستوى التركيز مع أنشطة الصباح."
            activeChildrenToday >= 2 -> "اثنان من الأطفال قدما أداءً رائعًا في أنشطة اليوم."
            activeChildrenToday >= 1 -> "طفل واحد أظهر تحسنًا ملحوظًا في التركيز والمشاركة."
            else -> "تفقد أنشطة أطفالك لليوم وشجعهم على المشاركة."
        }
    }

    fun retryLoadData() {
        loadHomeScreenData()
    }
}