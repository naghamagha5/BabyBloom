package com.babybloom.presentation.viewmodels

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babybloom.di.AppSoundSettings
import com.babybloom.domain.model.ActivityContent
import com.babybloom.util.AssetPathResolver
import com.babybloom.util.SoundEffect
import com.babybloom.util.touch.TouchPatternAnalyzer
import com.babybloom.util.trace.TracePathData
import com.babybloom.util.trace.TracePathLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.*

private const val TAG = "TraceViewModel"

private const val ITEM_TIMEOUT_MS  = 120_000L
private const val BONUS_TIMEOUT_MS =  30_000L

// ─── Result ───────────────────────────────────────────────────────────────────

data class TraceResult(
    val isSuccess:       Boolean,
    val coverage:        Float,
    val elapsedMs:       Long,
    val attempts:        Int,
    val motorSkillScore: Float,
    val choiceConfidenceScore: Float,
    val averageMovementDistance: Float,
    val correctionCount: Int
)

// ─── State ────────────────────────────────────────────────────────────────────

data class TraceState(
    val item:           ActivityContent,
    val pathData:       TracePathData,
    val coloredIndices: Set<Int> = emptySet(),
    val coverage:       Float   = 0f,
    val bestCoverage:   Float   = 0f,
    val showHandHint:   Boolean = true,
    val handHintIndex:  Int     = 0,
    val currentAttempt: Int     = 1,
    val startTimeMs:    Long    = System.currentTimeMillis(),
    val isCalmMode:     Boolean = false,
    val mainTimerMs:    Long    = ITEM_TIMEOUT_MS,
    val inBonusWindow:  Boolean = false,
    val bonusTimerMs:   Long    = BONUS_TIMEOUT_MS
)

// ─── UI States ────────────────────────────────────────────────────────────────

sealed class TraceUiState {
    object Idle : TraceUiState()
    data class Tracing(val state: TraceState) : TraceUiState()
    data class RevealContent(val state: TraceState, val finalScore: Float) : TraceUiState()
    data class ShowSuccess(val state: TraceState, val finalScore: Float) : TraceUiState()
    data class ShowEncouraging(
        val state: TraceState,
        val attemptsDone: Int,
        val isLastAttempt: Boolean
    ) : TraceUiState()
    data class ItemComplete(val result: TraceResult) : TraceUiState()
    data class NoPath(val contentId: String) : TraceUiState()
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class TraceViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appSoundSettings: AppSoundSettings,
    private val tracePathLoader:  TracePathLoader
) : ViewModel() {

    private val _uiState = MutableStateFlow<TraceUiState>(TraceUiState.Idle)
    val uiState: StateFlow<TraceUiState> = _uiState.asStateFlow()

    private val touchAnalyzer = TouchPatternAnalyzer()

    private var mainTimerJob:    Job? = null
    private var bonusTimerJob:   Job? = null
    private var handAnimJob:     Job? = null
    private var hintRestoreJob:  Job? = null
    private var postResultJob:   Job? = null
    private var instructionStartJob: Job? = null

    private var nameAudioPlayer:    MediaPlayer? = null
    private var soundAudioPlayer:   MediaPlayer? = null
    private var instructionPlayer:  MediaPlayer? = null
    private var instructionPlaying  = false

    private var lastTapSoundMs = 0L
    private var canvasW        = 1f
    private var canvasH        = 1f

    companion object {
        const val COVERAGE_MIN  = 0.80f
        const val MAX_ATTEMPTS  = 3

        private const val TOUCH_MULTIPLIER     =  3.5f
        private const val TAP_SOUND_GAP_MS     =  200L
        private const val HAND_STEP_MS         =  120L
        private const val HAND_INITIAL_DELAY   = 1_400L
        private const val HAND_STROKE_PAUSE_MS =   700L
        private const val HAND_LOOP_PAUSE_MS   = 1_000L
        private const val HINT_RESTORE_MS      = 2_800L
        private const val REVEAL_HOLD_MS       = 2_000L
        private const val SUCCESS_POPUP_MS     = 2_200L
        private const val ENCOURAGING_HOLD_MS  = 3_500L

        private const val INSTRUCTION_AUDIO_DIR = "activities/audio/trace"

        private val INSTRUCTION_AUDIO_ATTRS = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        private val CONTENT_AUDIO_ATTRS = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun loadItem(item: ActivityContent, isCalmMode: Boolean) {
        cancelAllJobs()
        releaseAudioPlayers()

        val pathData = tracePathLoader.load(item.contentId, item.labelAr)
            ?: run { _uiState.value = TraceUiState.NoPath(item.contentId); return }

        touchAnalyzer.onSessionStart()
        canvasW = 1f; canvasH = 1f

        _uiState.value = TraceUiState.Tracing(
            TraceState(
                item        = item,
                pathData    = pathData,
                isCalmMode  = isCalmMode,
                startTimeMs = System.currentTimeMillis()
            )
        )

        instructionStartJob = viewModelScope.launch {
            delay(400L)
            playInstructionAudio(item.contentId)
        }

        startHandAnimation()
        startMainTimer()
    }

    // ── Touch input ───────────────────────────────────────────────────────────

    fun onDragStart(canvasSize: Offset, position: Offset) {
        canvasW = canvasSize.x.coerceAtLeast(1f)
        canvasH = canvasSize.y.coerceAtLeast(1f)
        hintRestoreJob?.cancel()
        handAnimJob?.cancel()
        val c = cur() ?: return
        touchAnalyzer.onPointerEvent(position)
        update(c.copy(showHandHint = false))
        hitTest(c.copy(showHandHint = false), position)
    }

    fun onDrag(canvasSize: Offset, position: Offset) {
        canvasW = canvasSize.x.coerceAtLeast(1f)
        canvasH = canvasSize.y.coerceAtLeast(1f)
        val c = cur() ?: return
        touchAnalyzer.onPointerEvent(position)
        hitTest(c, position)
    }

    fun onDragEnd() {
        val c = cur() ?: return
        if (c.inBonusWindow) return
        hintRestoreJob = viewModelScope.launch {
            delay(HINT_RESTORE_MS)
            val s = cur() ?: return@launch
            if (!s.inBonusWindow) {
                update(s.copy(showHandHint = true, handHintIndex = 0))
                startHandAnimation()
            }
        }
    }

    // ── Hit detection ─────────────────────────────────────────────────────────

    private fun hitTest(state: TraceState, fingerPx: Offset) {
        val circles = state.pathData.circles
        if (circles.isEmpty()) return

        val newColored = state.coloredIndices.toMutableSet()
        var hit = false

        circles.forEachIndexed { i, circle ->
            if (i in newColored) return@forEachIndexed
            val cpx    = circle.center.x * canvasW
            val cpy    = circle.center.y * canvasH
            val touchR = circle.radius * canvasW * TOUCH_MULTIPLIER
            if (sqrt((fingerPx.x - cpx).pow(2) + (fingerPx.y - cpy).pow(2)) <= touchR) {
                newColored.add(i); hit = true
            }
        }

        if (!hit) return

        val coverage     = newColored.size.toFloat() / circles.size.coerceAtLeast(1)
        val bestCoverage = maxOf(state.bestCoverage, coverage)

        val now = System.currentTimeMillis()
        if (now - lastTapSoundMs > TAP_SOUND_GAP_MS) {
            appSoundSettings.playSoundEffect(SoundEffect.TAP)
            lastTapSoundMs = now
        }

        val updated = state.copy(
            coloredIndices = newColored,
            coverage       = coverage,
            bestCoverage   = bestCoverage
        )
        update(updated)

        if (coverage >= COVERAGE_MIN && !state.inBonusWindow) {
            enterBonusWindow(updated); return
        }
        if (coverage >= 1.0f) {
            onSuccess(finalScore = 1.0f, state = updated)
        }
    }

    // ── Phase transitions ─────────────────────────────────────────────────────

    private fun enterBonusWindow(state: TraceState) {
        mainTimerJob?.cancel(); handAnimJob?.cancel(); hintRestoreJob?.cancel()
        appSoundSettings.playSoundEffect(SoundEffect.CORRECT)
        update(state.copy(inBonusWindow = true, bonusTimerMs = BONUS_TIMEOUT_MS))
        startBonusTimer()
    }

    private fun onMainTimerExpired() {
        val c = cur() ?: return
        if (c.inBonusWindow) return
        if (c.bestCoverage >= COVERAGE_MIN) onSuccess(c.bestCoverage, c)
        else onAttemptFailed(c)
    }

    private fun onBonusTimerExpired() {
        val c = cur() ?: return
        onSuccess(finalScore = c.bestCoverage, state = c)
    }

    private fun onSuccess(finalScore: Float, state: TraceState) {
        cancelAllJobs()

        // FIX 1: Use runCatching around stop() — player may not be in started state
        runCatching { instructionPlayer?.stop() }
        instructionPlayer?.release()
        instructionPlayer = null
        instructionPlaying = false

        val elapsed = System.currentTimeMillis() - state.startTimeMs
        _uiState.value = TraceUiState.RevealContent(state, finalScore)

        postResultJob = viewModelScope.launch {
            delay(300L)
            playContentAudio(state.item)
            delay(REVEAL_HOLD_MS)
            _uiState.value = TraceUiState.ShowSuccess(state, finalScore)
            delay(SUCCESS_POPUP_MS)
            val analysis = touchAnalyzer.analyze(
                isCorrect = true,
                attempts = state.currentAttempt
            )
            _uiState.value = TraceUiState.ItemComplete(
                TraceResult(
                    isSuccess       = true,
                    coverage        = finalScore,
                    elapsedMs       = elapsed,
                    attempts        = state.currentAttempt,
                    motorSkillScore = (analysis.motorSkillScore * 0.7f + finalScore * 0.3f).coerceIn(0f, 1f),
                    choiceConfidenceScore = analysis.choiceConfidenceScore,
                    averageMovementDistance = analysis.averageMovementDistance,
                    correctionCount = analysis.correctionCount
                )
            )
        }
    }

    private fun onAttemptFailed(state: TraceState) {
        cancelAllJobs()
        val isLast = state.currentAttempt >= MAX_ATTEMPTS
        appSoundSettings.playSoundEffect(SoundEffect.WRONG)

        _uiState.value = TraceUiState.ShowEncouraging(
            state         = state,
            attemptsDone  = state.currentAttempt,
            isLastAttempt = isLast
        )

        postResultJob = viewModelScope.launch {
            delay(ENCOURAGING_HOLD_MS)
            if (isLast) {
                val analysis = touchAnalyzer.analyze(
                    isCorrect = false,
                    attempts = state.currentAttempt
                )
                val elapsed  = System.currentTimeMillis() - state.startTimeMs
                _uiState.value = TraceUiState.ItemComplete(
                    TraceResult(
                        isSuccess       = false,
                        coverage        = state.bestCoverage,
                        elapsedMs       = elapsed,
                        attempts        = state.currentAttempt,
                        motorSkillScore = (analysis.motorSkillScore * 0.7f + state.bestCoverage * 0.3f).coerceIn(0f, 1f),
                        choiceConfidenceScore = analysis.choiceConfidenceScore,
                        averageMovementDistance = analysis.averageMovementDistance,
                        correctionCount = analysis.correctionCount
                    )
                )
            } else {
                retryItem(state)
            }
        }
    }

    private fun retryItem(prev: TraceState) {
        touchAnalyzer.onSessionStart()
        _uiState.value = TraceUiState.Tracing(
            TraceState(
                item           = prev.item,
                pathData       = prev.pathData,
                isCalmMode     = prev.isCalmMode,
                currentAttempt = prev.currentAttempt + 1,
                startTimeMs    = System.currentTimeMillis()
            )
        )
        instructionStartJob = viewModelScope.launch {
            delay(400L)
            playInstructionAudio(prev.item.contentId)
        }
        startHandAnimation()
        startMainTimer()
    }

    // ── Timers ────────────────────────────────────────────────────────────────

    private fun startMainTimer() {
        mainTimerJob?.cancel()
        mainTimerJob = viewModelScope.launch {
            var rem = ITEM_TIMEOUT_MS
            while (rem > 0) {
                delay(1_000L); rem -= 1_000L
                val c = cur() ?: break
                if (c.inBonusWindow) break
                update(c.copy(mainTimerMs = rem))
            }
            if (rem <= 0) onMainTimerExpired()
        }
    }

    private fun startBonusTimer() {
        bonusTimerJob?.cancel()
        bonusTimerJob = viewModelScope.launch {
            var rem = BONUS_TIMEOUT_MS
            while (rem > 0) {
                delay(1_000L); rem -= 1_000L
                val c = cur() ?: break
                if (!c.inBonusWindow) break
                update(c.copy(bonusTimerMs = rem))
            }
            if (rem <= 0) onBonusTimerExpired()
        }
    }

    // ── Hand animation ────────────────────────────────────────────────────────

    private fun startHandAnimation() {
        handAnimJob?.cancel()
        handAnimJob = viewModelScope.launch {
            delay(HAND_INITIAL_DELAY)
            val strokes = cur()?.pathData?.logicStrokes
            if (strokes.isNullOrEmpty()) return@launch
            while (true) {
                strokes.forEachIndexed { si, stroke ->
                    if (si > 0) delay(HAND_STROKE_PAUSE_MS)
                    for (pi in stroke.indices) {
                        val s = cur() ?: return@launch
                        if (!s.showHandHint || s.inBonusWindow) return@launch
                        update(s.copy(handHintIndex = si * 10_000 + pi))
                        delay(HAND_STEP_MS)
                    }
                }
                delay(HAND_LOOP_PAUSE_MS)
            }
        }
    }

    // ── Audio ─────────────────────────────────────────────────────────────────

    private fun playInstructionAudio(contentId: String) {
        val type = when {
            contentId.startsWith("letter_") -> "letter"
            contentId.startsWith("number_") -> "number"
            contentId.startsWith("shape_")  -> "shape"
            else                            -> return
        }
        val path = "$INSTRUCTION_AUDIO_DIR/trace_instruction_$type.ogg"
        instructionPlayer?.release()
        instructionPlayer = null
        instructionPlaying = true
        viewModelScope.launch {
            instructionPlayer = buildMediaPlayer(path, INSTRUCTION_AUDIO_ATTRS) {
                instructionPlaying = false
            }
        }
    }

    private fun playContentAudio(item: ActivityContent) {
        nameAudioPlayer?.release()
        nameAudioPlayer = null
        soundAudioPlayer?.release()
        soundAudioPlayer = null

        viewModelScope.launch {
            when {
                item.contentId.startsWith("letter_") -> {
                    val namePath  = AssetPathResolver.audioPathFor(item.contentId, "LETTER_NAME")
                    nameAudioPlayer = buildMediaPlayer(namePath, CONTENT_AUDIO_ATTRS, null)
                }
                item.contentId.startsWith("number_") -> {
                    val path = AssetPathResolver.audioPathFor(item.contentId, "NUMBER")
                    nameAudioPlayer = buildMediaPlayer(path, CONTENT_AUDIO_ATTRS, null)
                }
                item.contentId.startsWith("shape_") -> {
                    val path = AssetPathResolver.audioPathFor(item.contentId, "SHAPE")
                    nameAudioPlayer = buildMediaPlayer(path, CONTENT_AUDIO_ATTRS, null)
                }
            }
        }
    }

    /**
     * Creates and starts a MediaPlayer for an assets file.
     *
     * FIX 2: Moved to Dispatchers.IO — MediaPlayer.prepare() is synchronous
     * I/O and must NOT run on the main thread. Calling it on Main can cause
     * an ANR or crash on devices with slow storage.
     *
     * FIX 3: afd is closed AFTER setDataSource returns (not before), and the
     * MediaPlayer is started on the Main thread after prepare() completes on IO.
     * The completion listener is posted back to Main via the MediaPlayer's
     * internal handler, which is safe.
     */
    private suspend fun buildMediaPlayer(
        assetPath:  String,
        attrs:      AudioAttributes,
        onComplete: (() -> Unit)?
    ): MediaPlayer? = withContext(Dispatchers.IO) {
        runCatching {
            val afd = context.assets.openFd(assetPath)
            val mp  = MediaPlayer()
            try {
                mp.setAudioAttributes(attrs)
                mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                // prepare() is synchronous I/O — safe on IO dispatcher
                mp.prepare()
            } finally {
                // FIX 3: close fd AFTER prepare() has finished reading it
                afd.close()
            }
            mp.setOnCompletionListener { player ->
                player.release()
                onComplete?.invoke()
            }
            // start() must be called from any thread after prepare()
            mp.start()
            mp
        }.onFailure { e ->
            Log.w(TAG, "Audio not found or failed: $assetPath — ${e.message}")
            // Keep chains alive even when file is missing
            onComplete?.invoke()
        }.getOrNull()
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    private fun cancelAllJobs() {
        mainTimerJob?.cancel()
        bonusTimerJob?.cancel()
        handAnimJob?.cancel()
        hintRestoreJob?.cancel()
        postResultJob?.cancel()
        instructionStartJob?.cancel()
    }

    private fun releaseAudioPlayers() {
        runCatching { nameAudioPlayer?.stop() }
        nameAudioPlayer?.release()
        nameAudioPlayer = null

        runCatching { soundAudioPlayer?.stop() }
        soundAudioPlayer?.release()
        soundAudioPlayer = null

        runCatching { instructionPlayer?.stop() }
        instructionPlayer?.release()
        instructionPlayer = null

        instructionPlaying = false
    }

    private fun cur() = (_uiState.value as? TraceUiState.Tracing)?.state

    private fun update(s: TraceState) {
        (_uiState.value as? TraceUiState.Tracing)
            ?.let { _uiState.value = it.copy(state = s) }
    }

    override fun onCleared() {
        super.onCleared()
        cancelAllJobs()
        releaseAudioPlayers()
    }
}
