package com.babybloom.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babybloom.di.AppSoundSettings
import com.babybloom.domain.model.ActivityResult
import com.babybloom.domain.model.ActivityWithContent
import com.babybloom.domain.model.InteractionEvent
import com.babybloom.domain.model.Session
import com.babybloom.domain.repository.ActivityRepository
import com.babybloom.domain.repository.ActivityResultRepository
import com.babybloom.domain.repository.ChildRepository
import com.babybloom.domain.repository.InteractionEventRepository
import com.babybloom.domain.repository.SessionRepository
import com.babybloom.domain.repository.UserRepository
import com.babybloom.util.SoundEffect
import com.babybloom.util.attention.AttentionDetector
import com.babybloom.util.attention.AttentionSample
import com.babybloom.util.attention.AttentionTracker
import com.babybloom.util.speech.SpeechRecognitionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ActivitySessionSettings(
    val isCalmMode: Boolean,
    val soundEffectsEnabled: Boolean,
    val backgroundMusicEnabled: Boolean,
    val sessionDurationMs: Long,
    val childId: Long,
    val userId: Long,
    val hasParentPin: Boolean
)

sealed class ActivityUiState {
    object Loading : ActivityUiState()
    data class Playing(
        val activityWithContent: ActivityWithContent,
        val currentIndex: Int = 0,
        val score: Int = 0,
        val totalAttempts: Int = 0,
        val sessionSettings: ActivitySessionSettings,
        val sessionRemainingMs: Long,
        val showOfflineSpeechBanner: Boolean = false,
        val showParentLock: Boolean = false
    ) : ActivityUiState()
    data class Completed(val score: Int, val total: Int) : ActivityUiState()
    data class Error(val message: String) : ActivityUiState()
}

@HiltViewModel
class ActivityViewModel @Inject constructor(
    private val activityRepository: ActivityRepository,
    private val activityResultRepository: ActivityResultRepository,
    private val childRepository: ChildRepository,
    private val userRepository: UserRepository,
    private val sessionRepository: SessionRepository,
    private val interactionEventRepository: InteractionEventRepository,
    private val speechRecognitionManager: SpeechRecognitionManager,
    private val appSoundSettings: AppSoundSettings,
    private val attentionDetector: AttentionDetector
) : ViewModel() {

    private val _uiState = MutableStateFlow<ActivityUiState>(ActivityUiState.Loading)
    val uiState: StateFlow<ActivityUiState> = _uiState.asStateFlow()

    private var sessionId: Long = 0L
    private var activityStartMs: Long = 0L
    private val attentionTracker = AttentionTracker()
    private var timerJob: Job? = null

    // ── Load ──────────────────────────────────────────────────────────────────

    fun loadActivity(activityId: String, sessionId: Long, childId: Long) {
        viewModelScope.launch {
            _uiState.value = ActivityUiState.Loading

            val child = childRepository.getById(childId) ?: run {
                _uiState.value = ActivityUiState.Error("Child not found")
                return@launch
            }

            val user = userRepository.getById(child.userId) ?: run {
                _uiState.value = ActivityUiState.Error("User not found")
                return@launch
            }

            // Create a real session row so ActivityResult FK doesn't fail
            val realSessionId = if (sessionId == 0L) {
                sessionRepository.startSession(
                    Session(
                        userId = child.userId,
                        childId = childId,
                        startTime = System.currentTimeMillis(),
                        endTime = null,
                        isAssessment = false,
                        attentionScore = 0f
                    )
                )
            } else {
                sessionId
            }
            this@ActivityViewModel.sessionId = realSessionId

            val data = activityRepository.getActivityWithContent(activityId) ?: run {
                _uiState.value = ActivityUiState.Error("Activity not found")
                return@launch
            }

            val settings = ActivitySessionSettings(
                isCalmMode = child.uiTheme,
                soundEffectsEnabled = child.soundEffectEnabled,
                backgroundMusicEnabled = child.backgroundMusicEnabled,
                sessionDurationMs = child.sessionDurationMinutes * 60_000L,
                childId = child.id,
                userId = child.userId,
                hasParentPin = user.parentLockPin != null
            )

            appSoundSettings.startSession(
                backgroundMusicEnabled = settings.backgroundMusicEnabled,
                soundEffectsEnabled = settings.soundEffectsEnabled
            )

            activityStartMs = System.currentTimeMillis()
            attentionTracker.reset()

            _uiState.value = ActivityUiState.Playing(
                activityWithContent = data,
                sessionSettings = settings,
                sessionRemainingMs = settings.sessionDurationMs
            )
            startSessionTimer(settings.sessionDurationMs)
        }
    }

    // ── Session Timer ─────────────────────────────────────────────────────────

    private fun startSessionTimer(durationMs: Long) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var remaining = durationMs
            while (remaining > 0) {
                delay(1_000)
                remaining -= 1_000
                val current = _uiState.value as? ActivityUiState.Playing ?: break
                _uiState.value = current.copy(sessionRemainingMs = remaining)
            }
            if (remaining <= 0) {
                val current = _uiState.value as? ActivityUiState.Playing ?: return@launch
                _uiState.value = ActivityUiState.Completed(
                    current.score,
                    current.activityWithContent.contentItems.size
                )
            }
        }
    }

    // ── Answer Submission ─────────────────────────────────────────────────────

    fun onAnswerSubmitted(
        isCorrect: Boolean,
        contentId: String,
        responseTimeMs: Long,
        attempts: Int = 1,
        speechConfidence: Float? = null,
        touchComplexity: Float? = null,
        scoreOverride: Float? = null
    ) {
        val current = _uiState.value as? ActivityUiState.Playing ?: return
        val attentionScore = attentionTracker.computeScore().takeIf { it > 0f }

        if (isCorrect) appSoundSettings.playSoundEffect(SoundEffect.CORRECT)
        else appSoundSettings.playSoundEffect(SoundEffect.WRONG)

        viewModelScope.launch {
            activityResultRepository.saveResult(
                ActivityResult(
                    sessionId        = sessionId,
                    childId          = current.sessionSettings.childId,
                    activityId       = current.activityWithContent.activity.id,
                    contentId        = contentId,
                    score            = if (isCorrect) 1f else 0f,
                    duration         = responseTimeMs,
                    correctCount     = if (isCorrect) 1 else 0,
                    incorrectCount   = if (!isCorrect) 1 else 0,
                    attempts         = attempts,
                    speechConfidence = speechConfidence,
                    touchComplexity  = touchComplexity,
                    attentionScore   = attentionScore
                )
            )
        }

        val newScore  = if (isCorrect) current.score + 1 else current.score
        val nextIndex = current.currentIndex + 1

        if (nextIndex >= current.activityWithContent.contentItems.size) {
            appSoundSettings.playSoundEffect(SoundEffect.COMPLETE)
            _uiState.value = ActivityUiState.Completed(
                newScore,
                current.activityWithContent.contentItems.size
            )
        } else {
            attentionTracker.reset()
            _uiState.value = current.copy(
                currentIndex  = nextIndex,
                score         = newScore,
                totalAttempts = current.totalAttempts + 1
            )
        }
    }

    // ── Attention Tracking ────────────────────────────────────────────────────

    fun onAttentionSample(sample: AttentionSample?) {
        attentionTracker.record(sample)
        sample?.let {
            val current = _uiState.value as? ActivityUiState.Playing ?: return
            viewModelScope.launch {
                interactionEventRepository.saveEvent(
                    InteractionEvent(
                        sessionId  = sessionId,
                        childId    = current.sessionSettings.childId,
                        activityId = current.activityWithContent.activity.id,
                        eventType  = "GAZE",
                        eventData  = """{"eulerY":${it.eulerY},"eulerX":${it.eulerX},"eyeOpenProb":${it.eyeOpenProbability},"attentive":${it.isAttentive}}"""
                    )
                )
            }
        }
    }
    fun saveTraceInteractionEvent(
        contentId: String,
        touchComplexity: Float,
        avgStrokeLength: Float,
        correctionCount: Int
    ) {
        val cur = _uiState.value as? ActivityUiState.Playing ?: return
        viewModelScope.launch {
            interactionEventRepository.saveEvent(InteractionEvent(
                sessionId  = sessionId,
                childId    = cur.sessionSettings.childId,
                activityId = cur.activityWithContent.activity.id,
                eventType  = "TOUCH",
                eventData  = """{"contentId":"$contentId","touchComplexity":$touchComplexity,"avgStrokeLength":$avgStrokeLength,"correctionCount":$correctionCount}"""
            ))
        }
    }
    // ── Parent Lock ───────────────────────────────────────────────────────────

    fun requestExit() {
        val current = _uiState.value as? ActivityUiState.Playing ?: return
        if (current.sessionSettings.hasParentPin) {
            _uiState.value = current.copy(showParentLock = true)
        } else {
            onForceExit()
        }
    }

    fun dismissParentLock() {
        val current = _uiState.value as? ActivityUiState.Playing ?: return
        _uiState.value = current.copy(showParentLock = false)
    }

    fun verifyPin(
        enteredPin: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val current = _uiState.value as? ActivityUiState.Playing ?: return
        viewModelScope.launch {
            if (userRepository.verifyParentLockPin(
                    current.sessionSettings.userId, enteredPin
                )
            ) {
                onSuccess()
            } else {
                onError("الرقم غلط، حاول مرة أخرى")
            }
        }
    }

    fun verifyPassword(
        enteredPassword: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val current = _uiState.value as? ActivityUiState.Playing ?: return
        viewModelScope.launch {
            if (userRepository.verifyParentPassword(
                    current.sessionSettings.userId, enteredPassword
                )
            ) {
                onSuccess()
            } else {
                onError("كلمة المرور غلط")
            }
        }
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    private fun onForceExit() {
        timerJob?.cancel()
        appSoundSettings.stopSession()
    }

    override fun onCleared() {
        super.onCleared()
        appSoundSettings.stopSession()
        timerJob?.cancel()
    }
}