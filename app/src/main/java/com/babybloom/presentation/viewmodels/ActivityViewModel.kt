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
import com.babybloom.domain.algorithm.SessionPlannerService
import com.babybloom.domain.model.ActivityLaunchStep
import com.babybloom.domain.model.ActivityResult
import com.babybloom.domain.model.ActivitySignal
import com.babybloom.domain.model.AlgorithmOutput
import com.babybloom.domain.model.ActivityWithContent
import com.babybloom.domain.model.ChildProfile
import com.babybloom.domain.model.InteractionEvent
import com.babybloom.domain.model.Session
import com.babybloom.domain.model.SessionDecision
import com.babybloom.data.local.entity.LevelMasteryEntity
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
import kotlin.math.roundToInt
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

internal object NormalSessionExitProgressPlanner {
    fun progressForCompletedState(
        childId: Long,
        sessionId: Long,
        decision: SessionDecision?,
        sessionQueue: List<ActivityLaunchStep>,
        currentStepIndex: Int
    ): NormalSessionProgress? {
        val queue = when (decision) {
            is SessionDecision.Next -> decision.encodedQueue
                ?.let(SessionQueueCodec::decode)
                ?.takeIf { it.isNotEmpty() }
                ?: sessionQueue
            is SessionDecision.Repeat -> decision.encodedQueue
                ?.let(SessionQueueCodec::decode)
                ?.takeIf { it.isNotEmpty() }
                ?: sessionQueue
            else -> return null
        }
        val stepIndex = when (decision) {
            is SessionDecision.Next -> decision.stepIndex ?: (currentStepIndex + 1)
            is SessionDecision.Repeat -> decision.stepIndex ?: currentStepIndex
            else -> return null
        }
        if (queue.isEmpty() || stepIndex !in queue.indices) return null

        return NormalSessionProgress(
            childId = childId,
            sessionId = sessionId,
            encodedQueue = SessionQueueCodec.encode(queue),
            stepIndex = stepIndex,
            remainingMs = null
        )
    }
}

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
    private val sessionPlannerService: SessionPlannerService,
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
    private var answerSubmissionJob: Job? = null
    private var loadRequestId: Long = 0L
    private var sessionQueue: List<ActivityLaunchStep> = emptyList()
    private var currentStepIndex: Int = 0
    private var currentStep: ActivityLaunchStep? = null
    private var lastAlgorithmOutput: AlgorithmOutput? = null
    private var pendingCompletion: ActivityUiState.Completed? = null

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
                    publishCompletion(current, ActivityUiState.Completed(
                        sessionScore,
                        sessionTotal,
                        sessionId  = this@ActivityViewModel.sessionId,
                        activityId = current.activityWithContent.activity.id,
                        contentId  = current.activityWithContent.contentItems
                            .getOrNull(current.currentIndex)
                            ?.contentId,
                        stepIndex  = currentStepIndex,
                        decision   = null
                    ))
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

        answerSubmissionJob = viewModelScope.launch {
            val activityId = current.activityWithContent.activity.id
            val activity = if (!current.sessionSettings.isAssessment && current.sessionSettings.isTest) {
                activityRepository.getById(activityId)
            } else {
                null
            }
            val signal = activity?.let {
                ActivitySignal(
                    childId = current.sessionSettings.childId,
                    activityId = activityId,
                    skillArea = it.skillArea,
                    modality = it.modality,
                    activityType = it.activityType,
                    difficultyLevel = it.difficultyLevel,
                    correctCount = finalCorrectCount,
                    incorrectCount = finalIncorrectCount,
                    attempts = attempts,
                    attentionScore = attentionScore,
                    touchQualityScore = touchQualityScore,
                    speechConfidence = speechConfidence,
                    durationMs = responseTimeMs,
                    expectedDurationMs = 60_000L
                )
            }

            if (current.sessionSettings.isTest) {
                activityResultRepository.saveResult(ActivityResult(
                    sessionId        = sessionId,
                    childId          = current.sessionSettings.childId,
                    activityId       = activityId,
                    contentId        = contentId,
                    score            = when {
                        signal != null -> algorithmEngine.computeItemScore(signal)
                        else -> scoreOverride ?: calculatedScore
                    },
                    duration         = responseTimeMs,
                    correctCount     = finalCorrectCount,
                    incorrectCount   = finalIncorrectCount,
                    attempts         = attempts,
                    speechConfidence = speechConfidence,
                    touchQualityScore = touchQualityScore,
                    attentionScore   = attentionScore
                ))
            }

            val newScore  = if (isCorrect) current.score + 1 else current.score
            val nextIndex = current.currentIndex + 1

            if (nextIndex >= current.activityWithContent.contentItems.size) {
                val isAssessment = current.sessionSettings.isAssessment
                val decision = if (isAssessment) {
                    null
                } else {
                    if (current.sessionSettings.isTest) {
                        updateProfileForCompletedNormalTestSteps(current.sessionSettings.childId)
                    }
                    resolveQueueDecisionWithoutAlgorithm(
                        childId = current.sessionSettings.childId,
                        sessionRemainingMs = current.sessionRemainingMs
                    )
                }
                val (completedScore, completedTotal) = when {
                    isAssessment -> newScore to current.activityWithContent.contentItems.size
                    decision is SessionDecision.SessionComplete -> {
                        updateProfileForCompletedNormalTestSteps(current.sessionSettings.childId)
                        sessionRepository.endSession(
                            this@ActivityViewModel.sessionId,
                            System.currentTimeMillis()
                        )
                        normalSessionProgressStore.clear()
                        getSessionScore()
                    }
                    else -> newScore to current.activityWithContent.contentItems.size
                }
                publishCompletion(current, ActivityUiState.Completed(
                    completedScore,
                    completedTotal,
                    sessionId  = this@ActivityViewModel.sessionId,
                    activityId = current.activityWithContent.activity.id,
                    contentId  = current.activityWithContent.contentItems
                        .getOrNull(current.currentIndex)
                        ?.contentId,
                    stepIndex  = currentStepIndex,
                    decision   = decision
                ))
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

    private fun publishCompletion(
        current: ActivityUiState.Playing,
        completed: ActivityUiState.Completed
    ) {
        if (current.showParentLock) {
            pendingCompletion = completed
        } else {
            _uiState.value = completed
        }
    }

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
        _uiState.value = pendingCompletion ?: current.copy(showParentLock = false)
        pendingCompletion = null
    }

    fun stopSoundSession() {
        appSoundSettings.stopSession()
    }

    fun pauseNormalSessionForExit() {
        timerJob?.cancel()
        viewModelScope.launch {
            answerSubmissionJob?.join()

            when (val current = _uiState.value) {
                is ActivityUiState.Playing -> {
                    if (!current.sessionSettings.isAssessment) {
                        saveNormalSessionProgress(current.sessionRemainingMs)
                        updateProfileForCompletedNormalTestSteps(current.sessionSettings.childId)
                        sessionRepository.endSession(sessionId, System.currentTimeMillis())
                    }
                }
                is ActivityUiState.Completed -> {
                    val activeSession = sessionRepository.getSessionById(sessionId)
                    if (activeSession?.isAssessment == false) {
                        runCatching {
                            NormalSessionExitProgressPlanner.progressForCompletedState(
                                childId = activeSession.childId,
                                sessionId = sessionId,
                                decision = current.decision,
                                sessionQueue = sessionQueue,
                                currentStepIndex = currentStepIndex
                            )?.let { normalSessionProgressStore.save(it) }
                                ?: normalSessionProgressStore.clear()
                        }
                        updateProfileForCompletedNormalTestSteps(activeSession.childId)
                        sessionRepository.endSession(sessionId, System.currentTimeMillis())
                    }
                }
                else -> Unit
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

    private suspend fun resolveQueueDecisionWithoutAlgorithm(
        childId: Long,
        sessionRemainingMs: Long
    ): SessionDecision {
        val nextStep = sessionQueue.getOrNull(currentStepIndex + 1)
        if (nextStep != null) {
            return SessionDecision.Next(
                activityId = nextStep.activityId,
                contentId = nextStep.contentId
            )
        }

        val activeStep = currentStep
        if (
            activeStep != null &&
            activeStep.phase == com.babybloom.domain.model.SessionPhase.REVISION &&
            sessionRemainingMs > 0L
        ) {
            val profile = childProfileRepository.getByChildId(childId)
            if (profile != null) {
                val usedRevisionIds = sessionQueue
                    .filter { it.phase == com.babybloom.domain.model.SessionPhase.REVISION }
                    .mapNotNull { (it.targetContentId ?: it.contentId)?.removeSuffix("_s") }
                    .toSet()
                val additionalSteps = sessionPlannerService.buildRevisionSteps(
                    profile = profile,
                    excludedContentIds = usedRevisionIds,
                    limit = AlgorithmWeights.REVISION_CONTENT_COUNT,
                    fallbackToAllWhenEmpty = true
                )
                if (additionalSteps.isNotEmpty()) {
                    sessionQueue = sessionQueue + additionalSteps
                    val appendedNext = sessionQueue.getOrNull(currentStepIndex + 1)
                    if (appendedNext != null) {
                        return SessionDecision.Next(
                            activityId = appendedNext.activityId,
                            contentId = appendedNext.contentId,
                            encodedQueue = SessionQueueCodec.encode(sessionQueue),
                            stepIndex = currentStepIndex + 1
                        )
                    }
                }
            }
        }

        return SessionDecision.SessionComplete
    }

    private suspend fun getSessionScore(): Pair<Int, Int> {
        val results = activityResultRepository.getForSession(sessionId)
        val correct = results.sumOf { it.correctCount }
        return correct to results.size
    }

    private suspend fun updateProfileForCompletedNormalTestSteps(childId: Long) {
        val profile = childProfileRepository.getByChildId(childId) ?: return
        val testOrRevisionStepKeys = sessionQueue
            .filter { it.isTest }
            .map { "${it.activityId}:${it.contentId.orEmpty()}" }
            .toSet()
        val testStepKeys = sessionQueue
            .filter { it.phase == com.babybloom.domain.model.SessionPhase.TEST }
            .mapNotNull { step ->
                val contentId = step.contentId ?: return@mapNotNull null
                "${step.activityId}:$contentId"
            }
            .toSet()
        val sessionResults = activityResultRepository.getForSession(sessionId)
        val signals = sessionResults
            .filter { result -> "${result.activityId}:${result.contentId}" in testOrRevisionStepKeys }
            .mapNotNull { result ->
                val activity = activityRepository.getById(result.activityId) ?: return@mapNotNull null
                ActivitySignal.from(result, activity)
            }
        val modalityProfile = algorithmEngine.updateModalityPreferencesFromSession(signals, profile)
        val completedTestResults = sessionResults.filter { result ->
            "${result.activityId}:${result.contentId}" in testStepKeys
        }
        val isTestPartComplete = testStepKeys.isNotEmpty() &&
            completedTestResults
                .map { "${it.activityId}:${it.contentId}" }
                .toSet()
                .containsAll(testStepKeys)

        val testContentScores = if (isTestPartComplete) {
            aggregateContentScores(completedTestResults)
        } else {
            emptyList()
        }
        if (isTestPartComplete) {
            persistTestContentScores(childId, testContentScores)
        }

        val completedRevisionAggregates = completedRevisionContentScores(
            childId = childId,
            profile = profile,
            sessionResults = sessionResults
        )
        if (completedRevisionAggregates.isNotEmpty()) {
            updateRevisionContentScores(childId, completedRevisionAggregates)
        }

        val contentProgressProfile = refreshContentProgress(modalityProfile)
        val finalProfile = if (isTestPartComplete) {
            applyCompletedTestLevels(
                baseProfile = profile,
                contentProgressProfile = contentProgressProfile,
                contentScores = testContentScores
            )
        } else {
            contentProgressProfile
        }

        childProfileRepository.upsert(finalProfile)
    }

    private suspend fun refreshContentProgress(profile: com.babybloom.domain.model.ChildProfile): com.babybloom.domain.model.ChildProfile {
        val allContent = listOf("LETTER_NAME", "ANIMAL", "NUMBER", "COLOR", "SHAPE")
            .flatMap { learningContentRepository.getByCategory(it) }
        val learnedIds = levelMasteryRepository.getContentScoresForChild(profile.childId)
            .filter { it.contentId.isNotBlank() && (it.contentScore ?: 0f) > AlgorithmWeights.CONTENT_PASS_THRESHOLD }
            .mapTo(mutableSetOf()) { it.contentId }
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

    private suspend fun aggregateContentScores(
        results: List<ActivityResult>
    ): List<TestContentAggregate> =
        results
            .groupBy { it.contentId.removeSuffix("_s") }
            .mapNotNull { (normalizedContentId, contentResults) ->
                val content = learningContentRepository.getById(normalizedContentId) ?: return@mapNotNull null
                val activityScores = contentResults.map { it.score }
                if (activityScores.isEmpty()) return@mapNotNull null

                TestContentAggregate(
                    contentId = normalizedContentId,
                    category = content.category,
                    level = content.difficultyLevel,
                    averageScore = activityScores.average().toFloat(),
                    completedActivityCount = contentResults.size,
                    latestResultTimestamp = contentResults.maxOfOrNull { it.timestamp } ?: 0L
                )
            }

    private suspend fun persistTestContentScores(
        childId: Long,
        contentScores: List<TestContentAggregate>
    ) {
        contentScores.forEach { aggregate ->
            levelMasteryRepository.upsert(
                LevelMasteryEntity(
                    id = levelMasteryRepository.getByContentId(childId, aggregate.contentId)?.id ?: 0L,
                    childId = childId,
                    skillArea = aggregate.category,
                    level = aggregate.level,
                    contentId = aggregate.contentId,
                    contentScore = aggregate.averageScore,
                    masteredCount = 0,
                    lastUpdated = System.currentTimeMillis()
                )
            )
        }
    }

    private suspend fun completedRevisionContentScores(
        childId: Long,
        profile: ChildProfile,
        sessionResults: List<ActivityResult>
    ): List<TestContentAggregate> {
        val revisionContentIds = sessionQueue
            .filter { it.phase == com.babybloom.domain.model.SessionPhase.REVISION }
            .mapNotNull { (it.targetContentId ?: it.contentId)?.removeSuffix("_s") }
            .toSet()
        val allChildResults = activityResultRepository.getByChild(childId)
            .filter { it.contentId.isNotBlank() }

        val completed = mutableListOf<TestContentAggregate>()
        revisionContentIds.forEach { contentId ->
            val existingMastery = levelMasteryRepository.getByContentId(childId, contentId)
            val cycleStartTimestamp = existingMastery?.lastUpdated ?: 0L
            val expectedKeys = sessionPlannerService
                .buildRevisionStepsForContent(profile, contentId)
                .map { "${it.activityId}:${it.contentId.orEmpty()}" }
                .toSet()
            if (expectedKeys.isEmpty()) return@forEach

            val latestByActivityKey = allChildResults
                .filter { it.timestamp > cycleStartTimestamp }
                .groupBy { "${it.activityId}:${it.contentId}" }
                .mapValues { (_, rows) -> rows.maxByOrNull { it.timestamp } }
            val completedKeys = latestByActivityKey.keys
            if (!completedKeys.containsAll(expectedKeys)) return@forEach

            val content = learningContentRepository.getById(contentId) ?: return@forEach
            val completedResults = latestByActivityKey
                .filterKeys { it in expectedKeys }
                .values
                .filterNotNull()
            val activityScores = completedResults.map { it.score }
            if (activityScores.isEmpty()) return@forEach

            completed += TestContentAggregate(
                contentId = contentId,
                category = content.category,
                level = content.difficultyLevel,
                averageScore = activityScores.average().toFloat(),
                completedActivityCount = completedResults.size,
                latestResultTimestamp = completedResults.maxOfOrNull { it.timestamp } ?: 0L
            )
        }
        return completed
    }

    private suspend fun updateRevisionContentScores(
        childId: Long,
        contentScores: List<TestContentAggregate>
    ) {
        contentScores.forEach { aggregate ->
            val existing = levelMasteryRepository.getByContentId(childId, aggregate.contentId)
            if (existing != null && aggregate.latestResultTimestamp <= existing.lastUpdated) {
                return@forEach
            }
            val mergedScore = existing?.contentScore
                ?.let { priorScore ->
                    if (priorScore == 0f) aggregate.averageScore
                    else (priorScore + aggregate.averageScore) / 2f
                }
                ?: aggregate.averageScore
            levelMasteryRepository.upsert(
                LevelMasteryEntity(
                    id = existing?.id ?: 0L,
                    childId = childId,
                    skillArea = aggregate.category,
                    level = aggregate.level,
                    contentId = aggregate.contentId,
                    contentScore = mergedScore,
                    masteredCount = existing?.masteredCount ?: 0,
                    lastUpdated = System.currentTimeMillis()
                )
            )
        }
    }

    private fun applyCompletedTestLevels(
        baseProfile: ChildProfile,
        contentProgressProfile: ChildProfile,
        contentScores: List<TestContentAggregate>
    ): ChildProfile {
        val resolvedLevels = mutableMapOf(
            CATEGORY_LETTER to baseProfile.languageLevel.coerceIn(1, 5),
            CATEGORY_ANIMAL to baseProfile.languageLevel.coerceIn(1, 5),
            CATEGORY_NUMBER to baseProfile.numeracyLevel.coerceIn(1, 4),
            CATEGORY_COLOR to baseProfile.motorLevel.coerceIn(1, 4),
            CATEGORY_SHAPE to baseProfile.motorLevel.coerceIn(1, 4)
        )

        contentScores
            .groupBy { it.category to it.level }
            .toList()
            .sortedBy { it.first.second }
            .forEach { entry ->
                val (category, level) = entry.first
                val scoresAtLevel = entry.second
                val passedCount = scoresAtLevel.count { it.averageScore > AlgorithmWeights.CONTENT_PASS_THRESHOLD }
                val resolvedLevel = when {
                    passedCount == scoresAtLevel.size -> (level + 1).coerceAtMost(maxLevelForCategory(category))
                    passedCount == 0 -> (level - 1).coerceAtLeast(1)
                    else -> level
                }
                resolvedLevels[category] = resolvedLevel
            }

        val languageLevel = ((resolvedLevels.getValue(CATEGORY_LETTER) + resolvedLevels.getValue(CATEGORY_ANIMAL)) / 2f)
            .roundToInt()
            .coerceIn(0, 5)
        val numeracyLevel = resolvedLevels.getValue(CATEGORY_NUMBER).coerceIn(0, 4)
        val motorLevel = ((resolvedLevels.getValue(CATEGORY_COLOR) + resolvedLevels.getValue(CATEGORY_SHAPE)) / 2f)
            .roundToInt()
            .coerceIn(0, 4)

        return contentProgressProfile.copy(
            languageLevel = languageLevel,
            numeracyLevel = numeracyLevel,
            motorLevel = motorLevel,
            overallProgressPercent = computeOverallProgressPercent(
                languageLevel = languageLevel,
                numeracyLevel = numeracyLevel,
                motorLevel = motorLevel,
                languageProgress = contentProgressProfile.languageProgress,
                numeracyProgress = contentProgressProfile.numeracyProgress,
                motorProgress = contentProgressProfile.motorProgress
            ),
            lastUpdated = System.currentTimeMillis()
        )
    }

    private fun maxLevelForCategory(category: String): Int =
        when (category) {
            CATEGORY_LETTER,
            CATEGORY_ANIMAL -> 5
            CATEGORY_NUMBER,
            CATEGORY_COLOR,
            CATEGORY_SHAPE -> 4
            else -> 5
        }

    private fun computeOverallProgressPercent(
        languageLevel: Int,
        numeracyLevel: Int,
        motorLevel: Int,
        languageProgress: Float,
        numeracyProgress: Float,
        motorProgress: Float
    ): Float {
        fun normalized(level: Int, progress: Float, maxLevel: Int): Float {
            val safeLevel = level.coerceIn(1, maxLevel)
            return ((safeLevel - 1) + progress.coerceIn(0f, 1f)) /
                (maxLevel - 1).coerceAtLeast(1).toFloat()
        }

        return (
            normalized(languageLevel, languageProgress, 5) +
                normalized(numeracyLevel, numeracyProgress, 4) +
                normalized(motorLevel, motorProgress, 4)
            ) / 3f * 100f
    }

    private data class TestContentAggregate(
        val contentId: String,
        val category: String,
        val level: Int,
        val averageScore: Float,
        val completedActivityCount: Int,
        val latestResultTimestamp: Long
    )

    private companion object {
        const val CATEGORY_LETTER = "LETTER_NAME"
        const val CATEGORY_ANIMAL = "ANIMAL"
        const val CATEGORY_NUMBER = "NUMBER"
        const val CATEGORY_COLOR = "COLOR"
        const val CATEGORY_SHAPE = "SHAPE"
    }

}
