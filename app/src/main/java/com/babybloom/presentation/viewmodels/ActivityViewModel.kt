package com.babybloom.presentation.viewmodels

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babybloom.di.AppSoundSettings
import com.babybloom.di.NormalSessionProgress
import com.babybloom.di.NormalSessionProgressStore
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
import com.babybloom.domain.repository.LearningContentRepository
import com.babybloom.domain.repository.SessionRepository
import com.babybloom.domain.repository.UserRepository
import com.babybloom.util.attention.AttentionDetector
import com.babybloom.util.attention.AttentionSample
import com.babybloom.util.attention.AttentionTracker
import com.babybloom.util.SessionQueueCodec
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
    val isAssessment: Boolean,
    val isTest: Boolean
)

sealed class ActivityUiState {
    object Loading : ActivityUiState()
    data class Playing(
        val activityWithContent: ActivityWithContent,
        val currentIndex: Int = 0,
        val stepIndex: Int = 0,
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
        val activityId: String = "",
        val contentId: String? = null,
        val stepIndex: Int = 0,
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
    private val learningContentRepository: LearningContentRepository,
    private val algorithmEngine: AdaptiveAlgorithmEngine,
    private val speechRecognitionManager: SpeechRecognitionManager,
    private val appSoundSettings: AppSoundSettings,
    private val normalSessionProgressStore: NormalSessionProgressStore,
    private val attentionDetector: AttentionDetector
) : ViewModel() {

    private val _uiState = MutableStateFlow<ActivityUiState>(ActivityUiState.Loading)
    val uiState: StateFlow<ActivityUiState> = _uiState.asStateFlow()

    private var sessionId: Long = 0L
    private var activityStartMs: Long = 0L
    private val attentionTracker = AttentionTracker()
    private var latestAttentionSample: AttentionSample? = null
    private var latestAttentionSampleMs: Long = 0L
    private var timerJob: Job? = null
    private var loadJob: Job? = null
    private var loadRequestId: Long = 0L
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
        isAssessment: Boolean = false,
        isTest: Boolean = false
    ) {
        loadJob?.cancel()
        val requestId = ++loadRequestId
        _uiState.value = ActivityUiState.Loading
        loadJob = viewModelScope.launch {
            lastAlgorithmOutput = null
            currentStepIndex = stepIndex

            // ── isTest resolution ─────────────────────────────────────────────
            //
            // Priority order:
            //   1. The ActivityLaunchStep at [stepIndex] in the queue is always
            //      authoritative — it carries the flag set by AssessmentPlannerService
            //      or SessionPlannerService (warm-up → false, probe → true).
            //   2. If no queue was provided (direct single-activity launch), fall
            //      back to the raw isTest parameter passed by the caller.
            //
            // This ensures that within an assessment session:
            //   • Warm-up steps  (isWarmUp=true)  → isTest=false → learning layout
            //   • Probe steps    (isWarmUp=false)  → isTest=true  → assessment layout
            //
            // When isAssessment=false (normal session):
            //   • All steps are built with isTest=false by SessionPlannerService.
            //   • In future, the algorithm can flip isTest=true on individual steps
            //     inside SessionPlannerService without touching this view model.
            val stepInQueue = queue.getOrNull(stepIndex)
            val effectiveIsTest = when {
                stepInQueue != null -> stepInQueue.isTest   // queue step is always authoritative
                queue.isNotEmpty()  -> isTest               // step missing (shouldn't happen); safe fallback
                else                -> isTest               // no queue — direct single-activity launch
            }

            currentStep = ActivityLaunchStep(
                activityId = activityId,
                contentId  = contentId,
                isTest     = effectiveIsTest,
                phase      = stepInQueue?.phase
                    ?: if (effectiveIsTest) com.babybloom.domain.model.SessionPhase.TEST
                    else com.babybloom.domain.model.SessionPhase.LEARNING
            )
            sessionQueue = if (queue.isEmpty()) listOfNotNull(currentStep) else queue

            val child = childRepository.getById(childId) ?: run {
                if (requestId != loadRequestId) return@launch
                _uiState.value = ActivityUiState.Error("Child not found")
                return@launch
            }

            val user = userRepository.getById(child.userId) ?: run {
                if (requestId != loadRequestId) return@launch
                _uiState.value = ActivityUiState.Error("User not found")
                return@launch
            }

            // Create a real session row so ActivityResult FK doesn't fail.
            // Existing session IDs keep their original start time, so the
            // visible timer spans the whole learning session across activities.
            val now = System.currentTimeMillis()
            val realSessionStartMs: Long
            val realSessionId = if (sessionId == 0L) {
                realSessionStartMs = now
                sessionRepository.startSession(
                    Session(
                        userId       = child.userId,
                        childId      = childId,
                        startTime    = realSessionStartMs,
                        endTime      = null,
                        isAssessment = isAssessment,
                        attentionScore = 0f
                    )
                )
            } else {
                val existingSession = sessionRepository.getSessionById(sessionId)
                realSessionStartMs = existingSession?.startTime ?: now
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
                if (requestId != loadRequestId) return@launch
                _uiState.value = ActivityUiState.Error("Activity not found")
                return@launch
            }

            if (requestId != loadRequestId) return@launch

            val settings = ActivitySessionSettings(
                isCalmMode             = child.uiTheme,
                soundEffectsEnabled    = child.soundEffectEnabled,
                backgroundMusicEnabled = child.backgroundMusicEnabled,
                sessionDurationMs      = child.sessionDurationMinutes * 60_000L,
                childId                = child.id,
                userId                 = child.userId,
                hasParentPin           = user.parentLockPin != null,
                isAssessment           = isAssessment,
                isTest                 = effectiveIsTest   // resolved above
            )

            appSoundSettings.startSession(
                backgroundMusicEnabled = settings.backgroundMusicEnabled,
                soundEffectsEnabled    = settings.soundEffectsEnabled
            )

            activityStartMs = System.currentTimeMillis()
            attentionTracker.reset()

            val savedProgress = if (!isAssessment) {
                normalSessionProgressStore.getForChild(childId)
            } else {
                null
            }
            val sessionRemainingMs = savedProgress
                ?.takeIf { it.sessionId == realSessionId }
                ?.remainingMs
                ?.coerceIn(0L, settings.sessionDurationMs)
                ?: remainingSessionMs(
                    durationMs = settings.sessionDurationMs,
                    startedAtMs = realSessionStartMs
                )

            _uiState.value = ActivityUiState.Playing(
                activityWithContent = data,
                stepIndex           = stepIndex,
                sessionSettings     = settings,
                sessionRemainingMs  = sessionRemainingMs
            )
            if (!isAssessment) {
                saveNormalSessionProgress(sessionRemainingMs)
            }
            if (isAssessment) {
                timerJob?.cancel()
            } else {
                startSessionTimer(sessionRemainingMs)
            }
        }
    }

    // ── Session Timer ─────────────────────────────────────────────────────────

    private fun startSessionTimer(initialRemainingMs: Long) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var remaining = initialRemainingMs
            var lastTickMs = System.currentTimeMillis()
            while (true) {
                val now = System.currentTimeMillis()
                remaining = (remaining - (now - lastTickMs)).coerceAtLeast(0L)
                lastTickMs = now
                val current = _uiState.value as? ActivityUiState.Playing ?: break
                _uiState.value = current.copy(sessionRemainingMs = remaining)
                saveNormalSessionProgress(remaining)
                if (remaining <= 0) {
                    updateProfileForCompletedNormalTestSteps(current.sessionSettings.childId)
                    sessionRepository.endSession(
                        this@ActivityViewModel.sessionId,
                        System.currentTimeMillis()
                    )
                    saveNormalSessionProgress(0L)
                    val (sessionScore, sessionTotal) = getSessionScore()
                    _uiState.value = ActivityUiState.Completed(
                        sessionScore,
                        sessionTotal,
                        sessionId  = this@ActivityViewModel.sessionId,
                        activityId = current.activityWithContent.activity.id,
                        contentId  = current.activityWithContent.contentItems
                            .getOrNull(current.currentIndex)
                            ?.contentId,
                        stepIndex  = currentStepIndex,
                        decision   = null
                    )
                    break
                }
                delay(1_000)
            }
        }
    }

    private fun remainingSessionMs(durationMs: Long, startedAtMs: Long): Long =
        (durationMs - (System.currentTimeMillis() - startedAtMs)).coerceAtLeast(0L)

    // ── Answer Submission ─────────────────────────────────────────────────────

    fun onAnswerSubmitted(
        isCorrect: Boolean,
        contentId: String,
        responseTimeMs: Long,
        attempts: Int = 1,
        speechConfidence: Float? = null,
        touchQualityScore: Float? = null,
        scoreOverride: Float? = null
    ) {
        val current = _uiState.value as? ActivityUiState.Playing ?: return
        val expectedContentId = current.activityWithContent.contentItems
            .getOrNull(current.currentIndex)
            ?.contentId
            ?: return
        if (contentId != expectedContentId) return

        val fallbackAttentionScore = latestAttentionSample
            ?.takeIf { System.currentTimeMillis() - latestAttentionSampleMs <= 5_000L }
            ?.attentionScore
        val rawAttentionScore = when {
            attentionTracker.hasSamples() -> attentionTracker.computeScore()
            else -> fallbackAttentionScore
        }
        val attentionScore = rawAttentionScore?.coerceIn(0f, 1f)

        val finalCorrectCount   = if (isCorrect) 1 else 0
        val finalIncorrectCount = (attempts - finalCorrectCount).coerceAtLeast(0)
        val calculatedScore     = if (attempts > 0) {
            finalCorrectCount.toFloat() / attempts.toFloat()
        } else 0f

        viewModelScope.launch {
            val activityId = current.activityWithContent.activity.id

            if (current.sessionSettings.isTest) {
                activityResultRepository.saveResult(ActivityResult(
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
                    touchQualityScore = touchQualityScore,
                    attentionScore   = attentionScore
                ))
            }

            if (!current.sessionSettings.isAssessment && current.sessionSettings.isTest) {
                val activity = activityRepository.getById(activityId) ?: return@launch
                val signal = ActivitySignal(
                    childId            = current.sessionSettings.childId,
                    activityId         = activityId,
                    skillArea          = activity.skillArea,
                    modality           = activity.modality,
                    activityType       = activity.activityType,
                    difficultyLevel    = activity.difficultyLevel,
                    correctCount       = finalCorrectCount,
                    incorrectCount     = finalIncorrectCount,
                    attempts           = attempts,
                    attentionScore     = attentionScore,
                    touchQualityScore   = touchQualityScore,
                    speechConfidence   = speechConfidence,
                    durationMs         = responseTimeMs,
                    expectedDurationMs = 60_000L
                )
                val profile = childProfileRepository.getByChildId(current.sessionSettings.childId)
                if (profile != null) {
                    val output = algorithmEngine.processActivityResult(signal, profile)
                    childProfileRepository.upsert(refreshContentProgress(output.updatedProfile))
                    lastAlgorithmOutput = output

                    if (algorithmEngine.computeItemScore(signal) >= AlgorithmWeights.LEVEL_UP_THRESHOLD) {
                        levelMasteryRepository.incrementMastered(
                            childId   = current.sessionSettings.childId,
                            skillArea = activity.skillArea,
                            level     = activity.difficultyLevel
                        )
                    }
                }
            }

            val newScore  = if (isCorrect) current.score + 1 else current.score
            val nextIndex = current.currentIndex + 1

            if (nextIndex >= current.activityWithContent.contentItems.size) {
                val decision = lastAlgorithmOutput?.let { resolveDecision(it) }
                    ?: resolveQueueDecisionWithoutAlgorithm()
                val (completedScore, completedTotal) =
                    if (decision is SessionDecision.SessionComplete) {
                        if (!current.sessionSettings.isAssessment) {
                            updateProfileForCompletedNormalTestSteps(current.sessionSettings.childId)
                        }
                        sessionRepository.endSession(
                            this@ActivityViewModel.sessionId,
                            System.currentTimeMillis()
                        )
                        normalSessionProgressStore.clear()
                        getSessionScore()
                    } else {
                        newScore to current.activityWithContent.contentItems.size
                    }
                _uiState.value = ActivityUiState.Completed(
                    completedScore,
                    completedTotal,
                    sessionId  = this@ActivityViewModel.sessionId,
                    activityId = current.activityWithContent.activity.id,
                    contentId  = current.activityWithContent.contentItems
                        .getOrNull(current.currentIndex)
                        ?.contentId,
                    stepIndex  = currentStepIndex,
                    decision   = decision
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
            latestAttentionSample = it
            latestAttentionSampleMs = System.currentTimeMillis()
            val current = _uiState.value as? ActivityUiState.Playing ?: return
            viewModelScope.launch {
                interactionEventRepository.saveEvent(
                    InteractionEvent(
                        sessionId  = sessionId,
                        childId    = current.sessionSettings.childId,
                        activityId = current.activityWithContent.activity.id,
                        eventType  = "GAZE",
                        eventData  = """{"eulerY":${it.eulerY},"eulerX":${it.eulerX},"eyeOpenProb":${it.eyeOpenProbability},"attentionScore":${it.attentionScore},"attentive":${it.isAttentive}}"""
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
        touchQualityScore: Float,
        averageMovementDistance: Float,
        correctionCount: Int
    ) {
        val cur = _uiState.value as? ActivityUiState.Playing ?: return
        viewModelScope.launch {
            interactionEventRepository.saveEvent(InteractionEvent(
                sessionId  = sessionId,
                childId    = cur.sessionSettings.childId,
                activityId = cur.activityWithContent.activity.id,
                eventType  = "TOUCH",
                eventData  = """{"contentId":"$contentId","touchQualityScore":$touchQualityScore,"averageMovementDistance":$averageMovementDistance,"correctionCount":$correctionCount}"""
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

    fun stopSoundSession() {
        appSoundSettings.stopSession()
    }

    fun pauseNormalSessionForExit() {
        timerJob?.cancel()
        val current = _uiState.value as? ActivityUiState.Playing
        if (current != null && !current.sessionSettings.isAssessment) {
            viewModelScope.launch {
                saveNormalSessionProgress(current.sessionRemainingMs)
                updateProfileForCompletedNormalTestSteps(current.sessionSettings.childId)
                sessionRepository.endSession(sessionId, System.currentTimeMillis())
            }
        }
        appSoundSettings.stopSession()
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
        pauseNormalSessionForExit()
    }

    override fun onCleared() {
        super.onCleared()
        loadJob?.cancel()
        timerJob?.cancel()
    }

    private fun resolveDecision(output: AlgorithmOutput): SessionDecision? {
        return resolveQueueDecisionWithoutAlgorithm()
    }

    private fun resolveQueueDecisionWithoutAlgorithm(): SessionDecision {
        val nextStep = sessionQueue.getOrNull(currentStepIndex + 1)
        return if (nextStep != null) {
            SessionDecision.Next(
                activityId = nextStep.activityId,
                contentId = nextStep.contentId
            )
        } else {
            SessionDecision.SessionComplete
        }
    }

    private suspend fun getSessionScore(): Pair<Int, Int> {
        val results = activityResultRepository.getForSession(sessionId)
        val correct = results.sumOf { it.correctCount }
        return correct to results.size
    }

    private suspend fun updateProfileForCompletedNormalTestSteps(childId: Long) {
        val profile = childProfileRepository.getByChildId(childId) ?: return
        val testStepKeys = sessionQueue
            .filter { it.isTest }
            .map { "${it.activityId}:${it.contentId.orEmpty()}" }
            .toSet()
        if (testStepKeys.isEmpty()) {
            childProfileRepository.upsert(
                refreshContentProgress(
                    algorithmEngine.updateModalityPreferencesFromSession(emptyList(), profile)
                )
            )
            return
        }

        val signals = activityResultRepository.getForSession(sessionId)
            .filter { result -> "${result.activityId}:${result.contentId}" in testStepKeys }
            .mapNotNull { result ->
                val activity = activityRepository.getById(result.activityId) ?: return@mapNotNull null
                ActivitySignal.from(result, activity)
            }

        val modalityProfile = algorithmEngine.updateModalityPreferencesFromSession(signals, profile)
        childProfileRepository.upsert(refreshContentProgress(modalityProfile))
    }

    private suspend fun refreshContentProgress(profile: com.babybloom.domain.model.ChildProfile): com.babybloom.domain.model.ChildProfile {
        val allContent = listOf("LETTER_NAME", "ANIMAL", "NUMBER", "COLOR", "SHAPE")
            .flatMap { learningContentRepository.getByCategory(it) }
        val latestByContent = activityResultRepository.getByChild(profile.childId)
            .filter { it.contentId.isNotBlank() }
            .groupBy { it.contentId.removeSuffix("_s") }
            .mapValues { (_, history) -> history.maxByOrNull { it.timestamp } }
        val learnedIds = latestByContent
            .filterValues { result -> result != null && result.score >= AlgorithmWeights.CONTENT_PASS_THRESHOLD }
            .keys
        return algorithmEngine.applyContentProgress(profile, allContent, learnedIds)
    }

    private suspend fun saveNormalSessionProgress(remainingMs: Long) {
        val current = _uiState.value as? ActivityUiState.Playing
        val childId = current?.sessionSettings?.childId ?: currentStep?.let { null } ?: return
        normalSessionProgressStore.save(
            NormalSessionProgress(
                childId = childId,
                sessionId = sessionId,
                encodedQueue = SessionQueueCodec.encode(sessionQueue),
                stepIndex = currentStepIndex,
                remainingMs = remainingMs
            )
        )
    }

}
