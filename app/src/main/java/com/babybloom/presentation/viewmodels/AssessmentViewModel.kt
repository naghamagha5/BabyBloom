package com.babybloom.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babybloom.data.local.entity.AssessmentResultEntity
import com.babybloom.domain.algorithm.AdaptiveAlgorithmEngine
import com.babybloom.domain.algorithm.AssessmentPlannerService
import com.babybloom.domain.model.ActivityLaunchStep
import com.babybloom.domain.model.ActivitySignal
import com.babybloom.domain.model.Session
import com.babybloom.domain.repository.ActivityRepository
import com.babybloom.domain.repository.ActivityResultRepository
import com.babybloom.domain.repository.AssessmentRepository
import com.babybloom.domain.repository.ChildProfileRepository
import com.babybloom.domain.repository.ChildRepository
import com.babybloom.domain.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AssessmentUiState {
    object Loading       : AssessmentUiState()
    data class Intro(
        val childName: String,
        val isCalmMode: Boolean
    ) : AssessmentUiState()
    data class Playing(
        val currentActivityId : String,
        val currentContentId  : String?,
        val sessionId         : Long,
        val currentIndex      : Int,
        val totalCount        : Int
    ) : AssessmentUiState()
    object Bootstrapping : AssessmentUiState()
    object Complete      : AssessmentUiState()
    data class Error(val message: String) : AssessmentUiState()
}

@HiltViewModel
class AssessmentViewModel @Inject constructor(
    private val assessmentPlannerService : AssessmentPlannerService,
    private val algorithmEngine          : AdaptiveAlgorithmEngine,
    private val assessmentRepository     : AssessmentRepository,
    private val childProfileRepository   : ChildProfileRepository,
    private val childRepository          : ChildRepository,
    private val sessionRepository        : SessionRepository,
    private val activityRepository       : ActivityRepository,
    private val activityResultRepository : ActivityResultRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<AssessmentUiState>(AssessmentUiState.Loading)
    val uiState: StateFlow<AssessmentUiState> = _uiState.asStateFlow()

    private var childId      : Long           = 0L
    private var sessionId    : Long           = 0L
    private var activityPlan : List<ActivityLaunchStep> = emptyList()
    private var currentIndex : Int            = 0

    // ── Init ──────────────────────────────────────────────────────────────

    fun startAssessment(childId: Long) {
        this.childId = childId
        viewModelScope.launch {
            val child = childRepository.getById(childId) ?: run {
                _uiState.value = AssessmentUiState.Error("Child not found")
                return@launch
            }
            val profile = childProfileRepository.getByChildId(childId)
            if (profile?.assessmentCompleted == true) {
                _uiState.value = AssessmentUiState.Complete
                return@launch
            }
            _uiState.value = AssessmentUiState.Intro(
                childName = child.name,
                isCalmMode = child.uiTheme
            )
        }
    }

    // ── Begin ─────────────────────────────────────────────────────────────

    fun beginActivities() {
        viewModelScope.launch {
            val child = childRepository.getById(childId) ?: run {
                _uiState.value = AssessmentUiState.Error("Child not found")
                return@launch
            }

            activityPlan = assessmentPlannerService.buildAssessmentSequence(child.age)
            if (activityPlan.isEmpty()) {
                _uiState.value = AssessmentUiState.Error("لا توجد أنشطة تقييم متاحة")
                return@launch
            }

            sessionId = sessionRepository.startSession(
                Session(
                    userId       = child.userId,
                    childId      = childId,
                    startTime    = System.currentTimeMillis(),
                    endTime      = null,
                    isAssessment = true,
                    attentionScore = 0f
                )
            )

            currentIndex = 0
            _uiState.value = AssessmentUiState.Playing(
                currentActivityId = activityPlan[0].activityId,
                currentContentId  = activityPlan[0].contentId,
                sessionId         = sessionId,
                currentIndex      = 0,
                totalCount        = activityPlan.size
            )
        }
    }

    // ── After each activity ───────────────────────────────────────────────

    fun onActivityComplete(score: Int, total: Int) {
        currentIndex++
        if (currentIndex >= activityPlan.size) {
            bootstrapProfile()
        } else {
            _uiState.value = AssessmentUiState.Playing(
                currentActivityId = activityPlan[currentIndex].activityId,
                currentContentId  = activityPlan[currentIndex].contentId,
                sessionId         = sessionId,
                currentIndex      = currentIndex,
                totalCount        = activityPlan.size
            )
        }
    }

    // ── Bootstrap ─────────────────────────────────────────────────────────

    private fun bootstrapProfile() {
        viewModelScope.launch {
            _uiState.value = AssessmentUiState.Bootstrapping

            // Build signals from DB results + activity metadata
            val dbResults = activityResultRepository.getForSession(sessionId)
            val signals = dbResults.mapNotNull { result ->
                val activity = activityRepository.getById(result.activityId)
                    ?: return@mapNotNull null
                ActivitySignal.from(result, activity)
            }

            val bootstrapped = algorithmEngine.bootstrapProfileFromAssessment(childId, signals)

            // Save or update the profile
            childProfileRepository.upsert(bootstrapped)

            // Record assessment completion
            assessmentRepository.save(
                AssessmentResultEntity(
                    childId              = childId,
                    initialLanguageLevel = bootstrapped.languageLevel,
                    initialNumeracyLevel = bootstrapped.numeracyLevel,
                    initialMotorLevel    = bootstrapped.motorLevel,
                    dominantModality     = bootstrapped.dominantModality
                )
            )

            // Close session
            sessionRepository.endSession(sessionId, System.currentTimeMillis())

            _uiState.value = AssessmentUiState.Complete
        }
    }
}
