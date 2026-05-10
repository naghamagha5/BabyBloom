package com.babybloom.presentation.viewmodels

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babybloom.di.AppSoundSettings
import com.babybloom.domain.algorithm.AdaptiveAlgorithmEngine
import com.babybloom.domain.algorithm.AlgorithmWeights
import com.babybloom.domain.model.ActivityLaunchStep
import com.babybloom.domain.model.ActivityResult
import com.babybloom.domain.model.ActivitySignal
import com.babybloom.domain.model.AlgorithmOutput
import com.babybloom.domain.model.ActivityWithContent
import com.babybloom.domain.model.InteractionEvent
import com.babybloom.domain.model.Session
import com.babybloom.domain.model.SessionDecision
import com.babybloom.domain.repository.ActivityRepository
import com.babybloom.domain.repository.ActivityResultRepository
import com.babybloom.domain.repository.ChildProfileRepository
import com.babybloom.domain.repository.ChildRepository
import com.babybloom.domain.repository.InteractionEventRepository
import com.babybloom.domain.repository.LevelMasteryRepository
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
    val hasParentPin: Boolean,
    val isAssessment: Boolean
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
    data class Completed(
        val score: Int,
        val total: Int,
        val sessionId: Long,
        val decision: SessionDecision? = null
    ) : ActivityUiState()
    data class Error(val message: String) : ActivityUiState()
}

@HiltViewModel
class ActivityViewModel @Inject constructor(
    private val activityRepository: ActivityRepository,
    private val activityResultRepository: ActivityResultRepository,
    private val childProfileRepository: ChildProfileRepository,
    private val childRepository: ChildRepository,
    private val userRepository: UserRepository,
    private val sessionRepository: SessionRepository,
    private val interactionEventRepository: InteractionEventRepository,
    private val levelMasteryRepository: LevelMasteryRepository,
    private val algorithmEngine: AdaptiveAlgorithmEngine,
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
    private var sessionQueue: List<ActivityLaunchStep> = emptyList()
    private var currentStepIndex: Int = 0
    private var currentStep: ActivityLaunchStep? = null
    private var lastAlgorithmOutput: AlgorithmOutput? = null

    // ── Load ──────────────────────────────────────────────────────────────────

    fun loadActivity(
        activityId: String,
        sessionId: Long,
        childId: Long,
        contentId: String? = null,
        queue: List<ActivityLaunchStep> = emptyList(),
        stepIndex: Int = 0,
        isAssessment: Boolean = false
    ) {
        viewModelScope.launch {
            _uiState.value = ActivityUiState.Loading
            lastAlgorithmOutput = null
            currentStepIndex = stepIndex
            currentStep = ActivityLaunchStep(activityId = activityId, contentId = contentId)
            sessionQueue = if (queue.isEmpty()) listOfNotNull(currentStep) else queue

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
                        isAssessment = isAssessment,
                        attentionScore = 0f
                    )
                )
            } else {
                sessionId
            }
            this@ActivityViewModel.sessionId = realSessionId

            val data = activityRepository.getActivityWithContent(activityId)?.let { activityWithContent ->
                if (contentId.isNullOrBlank()) {
                    activityWithContent
                } else {
                    val filteredItems = activityWithContent.contentItems
                        .filter { it.contentId == contentId }
                    if (filteredItems.isEmpty()) null
                    else activityWithContent.copy(contentItems = filteredItems)
                }
            } ?: run {
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
                hasParentPin = user.parentLockPin != null,
                isAssessment = isAssessment
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
            if (isAssessment) {
                timerJob?.cancel()
            } else {
                startSessionTimer(settings.sessionDurationMs)
            }
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
                    current.activityWithContent.contentItems.size,
                    sessionId = this@ActivityViewModel.sessionId,
                    lastAlgorithmOutput?.let { resolveDecision(it) }
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

        // Calculate metrics based on total attempts
        val finalCorrectCount = if (isCorrect) 1 else 0
        val finalIncorrectCount = (attempts - finalCorrectCount).coerceAtLeast(0)
        val calculatedScore = if (attempts > 0) {
            finalCorrectCount.toFloat() / attempts.toFloat()
        } else 0f

        viewModelScope.launch {
            val activityId = current.activityWithContent.activity.id

            activityResultRepository.saveResult(
                ActivityResult(
                    sessionId        = sessionId,
                    childId          = current.sessionSettings.childId,
                    activityId       = activityId,
                    contentId        = contentId,
                    score            = scoreOverride ?: calculatedScore,
                    duration         = responseTimeMs,
                    correctCount     = finalCorrectCount,
                    incorrectCount   = finalIncorrectCount,
                    attempts         = attempts,
                    speechConfidence = speechConfidence,
                    touchComplexity  = touchComplexity,
                    attentionScore   = attentionScore
                )
            )

            val activity = activityRepository.getById(activityId) ?: return@launch
            val signal = ActivitySignal(
                childId = current.sessionSettings.childId,
                activityId = activityId,
                skillArea = activity.skillArea,
                modality = activity.modality,
                activityType = activity.activityType,
                difficultyLevel = activity.difficultyLevel,
                correctCount = finalCorrectCount,
                incorrectCount = finalIncorrectCount,
                attempts = attempts,
                attentionScore = attentionScore,
                touchComplexity = touchComplexity,
                speechConfidence = speechConfidence,
                durationMs = responseTimeMs,
                expectedDurationMs = 60_000L
            )
            val profile = childProfileRepository.getByChildId(current.sessionSettings.childId)
            if (profile != null) {
                val output = algorithmEngine.processActivityResult(signal, profile)
                childProfileRepository.upsert(output.updatedProfile)
                lastAlgorithmOutput = output

                if (algorithmEngine.computeItemScore(signal) >= AlgorithmWeights.LEVEL_UP_THRESHOLD) {
                    levelMasteryRepository.incrementMastered(
                        childId = current.sessionSettings.childId,
                        skillArea = activity.skillArea,
                        level = activity.difficultyLevel
                    )
                }
            }

            // For the UI score, keep incrementing by 1 if the child eventually got it right,
            // while the DB row keeps the normalized item score.
            val newScore  = if (isCorrect) current.score + 1 else current.score
            val nextIndex = current.currentIndex + 1

            if (nextIndex >= current.activityWithContent.contentItems.size) {
                appSoundSettings.playSoundEffect(SoundEffect.COMPLETE)
                _uiState.value = ActivityUiState.Completed(
                    newScore,
                    current.activityWithContent.contentItems.size,
                    sessionId = this@ActivityViewModel.sessionId,
                    lastAlgorithmOutput?.let { resolveDecision(it) }
                )
            } else {
                attentionTracker.reset()
                _uiState.value = current.copy(
                    currentIndex  = nextIndex,
                    score         = newScore,
                    totalAttempts = current.totalAttempts + attempts
                )
            }
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

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    suspend fun analyzeAttention(imageProxy: ImageProxy): AttentionSample? =
        attentionDetector.analyze(imageProxy)

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
            try {
                val ok = userRepository.verifyParentLockPin(
                    current.sessionSettings.userId, enteredPin
                )
                if (ok) onSuccess() else onError("الرقم خطاء، حاول مرة أخرى")
            } catch (e: Exception) {
                onError("حدث خطأ، حاول مرة أخرى")
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
            try {
                val ok = userRepository.verifyParentPassword(
                    current.sessionSettings.userId, enteredPassword
                )
                if (ok) onSuccess() else onError("كلمة المرور خطاء، حاول مرة أخرى")
            } catch (e: Exception) {
                onError("حدث خطأ، حاول مرة أخرى")
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

    private fun resolveDecision(output: AlgorithmOutput): SessionDecision? {
        val step = currentStep ?: return null
        return algorithmEngine.resolveSessionDecision(
            output = output,
            currentStep = step,
            queue = sessionQueue,
            currentIndex = currentStepIndex
        )
    }
}
