package com.babybloom.presentation.viewmodels

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babybloom.di.AppSoundSettings
import com.babybloom.domain.model.ActivityContent
import com.babybloom.util.AssetPathResolver
import com.babybloom.util.SoundEffect
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CountGameType { ANIMAL, SHAPE }

data class ShapeInfo(
    val id           : String,
    val labelAr      : String,
    val labelArPlural: String,
    val drawableName : String
)

sealed class CountingGameUiState {
    object Loading : CountingGameUiState()

    data class Playing(
        val contentId            : String,
        val gameType             : CountGameType,
        val targetCount          : Int,
        val subjectId            : String,
        val subjectLabelAr       : String,
        val subjectQuestionAr    : String,
        val choices              : List<Int>,
        val roundIndex           : Int,

        val countingStep         : Int      = -1,
        val isAnimating          : Boolean  = false,

        val selectedAnswer       : Int?     = null,
        val isCorrect            : Boolean? = null,
        val wrongAnswerIndex     : Int?     = null,
        val showCorrectHint      : Boolean  = false,
        val showCelebration      : Boolean  = false,
        val autoComplete         : Boolean  = false,

        val attempts             : Int      = 0,
        val maxAttempts          : Int      = 3,
        val startTimeMs          : Long     = System.currentTimeMillis(),

        val isTest               : Boolean  = false,
        // -1 = not started / not relevant, 1..TIMER_SECONDS = counting down, 0 = just expired
        val timeRemainingSeconds : Int      = -1
    ) : CountingGameUiState()
}

private val VALID_NUMBERS  = (1..10).toList()
private const val TIMER_SECONDS = 15

@HiltViewModel
class CountingGameViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appSoundSettings: AppSoundSettings
) : ViewModel() {

    private val _uiState = MutableStateFlow<CountingGameUiState>(CountingGameUiState.Loading)
    val uiState: StateFlow<CountingGameUiState> = _uiState.asStateFlow()

    private var mediaPlayer         : MediaPlayer? = null
    private var loadJob             : Job?         = null
    private var animationJob        : Job?         = null
    private var timerJob            : Job?         = null

    // Stored so the timer expiry path can call it without requiring it as a parameter
    private var onCompleteCallback  : ((Boolean, Long, Int) -> Unit)? = null

    // ── Catalogues ────────────────────────────────────────────────────────────

    private val animalInfo = mapOf(
        "animal_bear"       to Pair("دُبّ",        "الدِّبَبَة"),
        "animal_camel"      to Pair("جَمَل",       "الجِمَال"),
        "animal_chimpanzee" to Pair("شِمبانزي",    "الشِّمبانزي"),
        "animal_crocodile"  to Pair("تِمسَاح",     "التَّمَاسِيح"),
        "animal_deer"       to Pair("غَزَال",      "الغِزْلَان"),
        "animal_dog"        to Pair("كَلب",        "الكِلَاب"),
        "animal_dove"       to Pair("حَمَامَة",    "الحَمَام"),
        "animal_duck"       to Pair("بَطَّة",      "البَطّ"),
        "animal_elephant"   to Pair("فِيل",        "الفِيَلَة"),
        "animal_falcon"     to Pair("صَقر",        "الصُّقُور"),
        "animal_fish"       to Pair("سَمَكَة",     "الأَسْمَاك"),
        "animal_frog"       to Pair("ضُفْدَع",     "الضَّفَادِع"),
        "animal_giraffe"    to Pair("زَرَافَة",    "الزَّرَافَات"),
        "animal_goat"       to Pair("مَاعِز",      "المَاعِز"),
        "animal_horse"      to Pair("حِصَان",      "الخُيُول"),
        "animal_lion"       to Pair("أَسَد",       "الأُسُود"),
        "animal_monkey"     to Pair("قِرْد",       "القِرَدَة"),
        "animal_peacock"    to Pair("طَاوُوس",     "الطَّوَاوِيس"),
        "animal_sheep"      to Pair("خَرُوف",      "الخِرَاف"),
        "animal_tiger"      to Pair("نَمِر",       "النُّمُور"),
        "animal_wolf"       to Pair("ذِئْب",       "الذِّئَاب")
    )
    private val availableAnimals = animalInfo.keys.toList()

    val availableShapes = listOf(
        ShapeInfo("shape_circle",    "دَائِرَة",   "الدَّوَائِر",    "shape_circle"),
        ShapeInfo("shape_rectangle", "مُسْتَطِيل", "المُسْتَطِيلَات","shape_rectangle"),
        ShapeInfo("shape_square",    "مُرَبَّع",   "المُرَبَّعَات",  "shape_square"),
        ShapeInfo("shape_triangle",  "مُثَلَّث",   "المُثَلَّثَات",  "shape_triangle")
    )

    // ── Load ──────────────────────────────────────────────────────────────────

    fun loadGame(
        item           : ActivityContent,
        difficultyLevel: Int,
        activityId     : String,
        roundIndex     : Int,
        isTest         : Boolean,
        onComplete     : (isCorrect: Boolean, elapsedMs: Long, attempts: Int) -> Unit
    ) {
        // Store callback so timer expiry can reach it without a parameter
        onCompleteCallback = onComplete
        loadJob?.cancel()
        animationJob?.cancel()
        timerJob?.cancel()
        releasePlayer()
        _uiState.value = CountingGameUiState.Loading

        loadJob = viewModelScope.launch {
            val targetCount = item.contentId
                .removePrefix("number_")
                .toIntOrNull()
                ?.coerceIn(1, 10)
                ?: 1

            val gameType = when {
                activityId.contains("shape",  ignoreCase = true) -> CountGameType.SHAPE
                activityId.contains("animal", ignoreCase = true) -> CountGameType.ANIMAL
                else -> listOf(CountGameType.ANIMAL, CountGameType.SHAPE).random()
            }

            val (subjectId, labelAr, pluralAr) = when (gameType) {
                CountGameType.ANIMAL -> {
                    val id   = availableAnimals.random()
                    val info = animalInfo[id] ?: Pair("حَيَوَان", "الحَيَوَانَات")
                    Triple(id, info.first, info.second)
                }
                CountGameType.SHAPE -> {
                    val s = availableShapes.random()
                    Triple(s.id, s.labelAr, s.labelArPlural)
                }
            }

            _uiState.value = CountingGameUiState.Playing(
                contentId         = item.contentId,
                gameType          = gameType,
                targetCount       = targetCount,
                subjectId         = subjectId,
                subjectLabelAr    = labelAr,
                subjectQuestionAr = "كم عدد $pluralAr؟",
                choices           = generateChoices(targetCount),
                roundIndex        = roundIndex,
                isAnimating       = true,
                isTest            = isTest,
                startTimeMs       = System.currentTimeMillis()
            )

            delay(800)
            playQuestionAudio(subjectId)
            delay(2200)
            startBounceAnimation(targetCount)
        }
    }

    // ── Bounce animation — chains into timer when done ────────────────────────

    private fun startBounceAnimation(count: Int) {
        animationJob?.cancel()
        animationJob = viewModelScope.launch {
            updatePlaying { it.copy(isAnimating = true, countingStep = -1) }
            for (i in 0 until count) {
                updatePlaying { it.copy(countingStep = i) }
                appSoundSettings.playSoundEffect(SoundEffect.TAP)
                delay(1500)
            }
            updatePlaying { it.copy(countingStep = -1, isAnimating = false) }
            // Timer starts as soon as the counting hint finishes
            startTimer()
        }
    }

    // ── 15-second attempt timer ───────────────────────────────────────────────

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            for (sec in TIMER_SECONDS downTo 1) {
                updatePlaying { it.copy(timeRemainingSeconds = sec) }
                delay(1000)
            }
            onTimerExpired()
        }
    }

    private fun onTimerExpired() {
        val state = _uiState.value as? CountingGameUiState.Playing ?: return
        // If the child already answered, ignore the expiry
        if (state.selectedAnswer != null) return

        val newAttempts = state.attempts + 1
        val elapsedMs   = System.currentTimeMillis() - state.startTimeMs

        viewModelScope.launch {
            // Flash the timer at 0 briefly so the child sees it expired
            updatePlaying { it.copy(attempts = newAttempts, timeRemainingSeconds = 0) }
            appSoundSettings.playSoundEffect(SoundEffect.WRONG)
            delay(600)

            if (newAttempts >= state.maxAttempts) {
                // Same hint flow as three wrong taps
                updatePlaying { it.copy(showCorrectHint = true, timeRemainingSeconds = -1) }
                delay(800)
                playNumberAudio(state.targetCount)
                delay(3000)
                updatePlaying { it.copy(showCorrectHint = false) }
                delay(300)
                onCompleteCallback?.invoke(false, elapsedMs, newAttempts)
            } else {
                // Reset and give the child another attempt with a fresh timer
                updatePlaying { it.copy(timeRemainingSeconds = -1) }
                delay(400)
                startTimer()
            }
        }
    }

    // ── Answer handling ───────────────────────────────────────────────────────

    fun onAnswerSelected(
        selected  : Int,
        onComplete: (isCorrect: Boolean, elapsedMs: Long, attempts: Int) -> Unit
    ) {
        val state = _uiState.value as? CountingGameUiState.Playing ?: return
        // Only block if an answer is already being processed — animation no longer blocks
        if (state.selectedAnswer != null) return

        // Stop counting animation and timer immediately
        animationJob?.cancel()
        timerJob?.cancel()
        onCompleteCallback = onComplete

        val isCorrect   = selected == state.targetCount
        val newAttempts = state.attempts + 1
        val elapsedMs   = System.currentTimeMillis() - state.startTimeMs

        viewModelScope.launch {
            // Clean up any in-progress animation state
            updatePlaying { it.copy(countingStep = -1, isAnimating = false, timeRemainingSeconds = -1) }

            if (isCorrect) {
                updatePlaying { it.copy(selectedAnswer = selected, isCorrect = true, attempts = newAttempts) }
                appSoundSettings.playSoundEffect(SoundEffect.CORRECT)
                delay(300)

                playNumberAudio(state.targetCount)
                delay(1400)

                updatePlaying { it.copy(showCelebration = true) }
                delay(2200)

                updatePlaying { it.copy(showCelebration = false) }
                delay(300)
                onComplete(true, elapsedMs, newAttempts)

            } else {
                updatePlaying { it.copy(
                    selectedAnswer   = selected,
                    isCorrect        = false,
                    wrongAnswerIndex = selected,
                    attempts         = newAttempts
                )}
                appSoundSettings.playSoundEffect(SoundEffect.WRONG)
                delay(900)
                updatePlaying { it.copy(wrongAnswerIndex = null) }

                if (newAttempts >= state.maxAttempts) {
                    delay(400)
                    updatePlaying { it.copy(selectedAnswer = null, isCorrect = null, showCorrectHint = true) }
                    delay(800)
                    playNumberAudio(state.targetCount)
                    delay(3000)
                    updatePlaying { it.copy(showCorrectHint = false) }
                    delay(300)
                    onComplete(false, elapsedMs, newAttempts)
                } else {
                    delay(400)
                    updatePlaying { it.copy(selectedAnswer = null, isCorrect = null) }
                    // Give the child a fresh timer for the next attempt
                    startTimer()
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun generateChoices(correct: Int): List<Int> {
        val distractors = VALID_NUMBERS
            .filter { it != correct }
            .sortedBy { n -> if (n > correct) n - correct else correct - n }
            .take(3)
            .shuffled()
        return (listOf(correct) + distractors).shuffled()
    }

    private fun updatePlaying(block: (CountingGameUiState.Playing) -> CountingGameUiState.Playing) {
        val s = _uiState.value as? CountingGameUiState.Playing ?: return
        _uiState.value = block(s)
    }

    // ── Audio ─────────────────────────────────────────────────────────────────

    private fun playQuestionAudio(subjectId: String) =
        playAsset(AssetPathResolver.countQuestionAudioPath(subjectId))

    fun playNumberAudio(number: Int) =
        playAsset(AssetPathResolver.audioPathFor("number_$number", "NUMBER"))

    private fun playAsset(path: String) {
        try {
            releasePlayer()
            mediaPlayer = MediaPlayer()
            val afd = context.assets.openFd(path)
            mediaPlayer?.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            mediaPlayer?.prepare()
            mediaPlayer?.start()
        } catch (e: Exception) {
            Log.w("CountingGameVM", "Audio not found: $path", e)
        }
    }

    private fun releasePlayer() {
        try { mediaPlayer?.stop(); mediaPlayer?.release() } catch (_: Exception) {}
        mediaPlayer = null
    }

    fun getShapeInfo(shapeId: String): ShapeInfo? =
        availableShapes.firstOrNull { it.id == shapeId }

    override fun onCleared() {
        super.onCleared()
        loadJob?.cancel()
        animationJob?.cancel()
        timerJob?.cancel()
        releasePlayer()
        onCompleteCallback = null
    }
}
