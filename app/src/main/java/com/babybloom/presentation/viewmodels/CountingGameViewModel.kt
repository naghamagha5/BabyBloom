package com.babybloom.presentation.viewmodels

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babybloom.data.local.dao.LearningContentDao
import com.babybloom.di.AppSoundSettings
import com.babybloom.domain.model.ActivityContent
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

// ── Game type ─────────────────────────────────────────────────────────────────

enum class CountGameType { ANIMAL, SHAPE }

data class ShapeInfo(
    val id           : String,
    val labelAr      : String,
    val labelArPlural: String,
    val drawableName : String
)

// ── UI State ──────────────────────────────────────────────────────────────────

sealed class CountingGameUiState {
    object Loading : CountingGameUiState()

    data class Playing(
        val gameType          : CountGameType,
        val targetCount       : Int,
        val subjectId         : String,
        val subjectLabelAr    : String,
        val subjectQuestionAr : String,
        val choices           : List<Int>,
        val roundIndex        : Int,

        // Bounce animation
        val countingStep      : Int      = -1,
        val isAnimating       : Boolean  = false,

        // Answer state
        val selectedAnswer    : Int?     = null,
        val isCorrect         : Boolean? = null,
        val wrongAnswerIndex  : Int?     = null,
        val showCorrectHint   : Boolean  = false,
        // ── unified celebration popup ──────────────────────────────────────
        // Set to true after a correct answer; the Screen renders GoodJobPopup.
        val showCelebration   : Boolean  = false,
        val autoComplete      : Boolean  = false,

        val attempts          : Int      = 0,
        val maxAttempts       : Int      = 3,
        val startTimeMs       : Long     = System.currentTimeMillis()
    ) : CountingGameUiState()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class CountingGameViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val learningContentDao: LearningContentDao,
    // ── Sound settings — same as TraceViewModel ───────────────────────────────
    // SFX (correct/wrong/tap) are routed through AppSoundSettings so the
    // parent's sound-effects toggle is respected.
    // Voice audio (question/number) still uses its own MediaPlayer because
    // it plays content audio, not SFX.
    private val appSoundSettings: AppSoundSettings
) : ViewModel() {

    private val _uiState = MutableStateFlow<CountingGameUiState>(CountingGameUiState.Loading)
    val uiState: StateFlow<CountingGameUiState> = _uiState.asStateFlow()

    private var mediaPlayer  : MediaPlayer? = null
    private var animationJob : Job?         = null

    private var roundCounts: List<Int> = emptyList()

    // ── Animal catalogue ──────────────────────────────────────────────────────
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

    private fun generateRoundCounts(): List<Int> = listOf(1, 2, 3)

    // ── Load ──────────────────────────────────────────────────────────────────

    fun loadGame(
        item           : ActivityContent,
        difficultyLevel: Int,
        activityId     : String,
        roundIndex     : Int
    ) {
        animationJob?.cancel()

        viewModelScope.launch {
            _uiState.value = CountingGameUiState.Loading

            if (roundIndex == 0 || roundCounts.isEmpty()) {
                roundCounts = generateRoundCounts()
            }

            val gameType = if (activityId.contains("shape", ignoreCase = true))
                CountGameType.SHAPE else CountGameType.ANIMAL

            val targetCount = roundCounts.getOrElse(roundIndex) { (1..3).random() }

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
                gameType          = gameType,
                targetCount       = targetCount,
                subjectId         = subjectId,
                subjectLabelAr    = labelAr,
                subjectQuestionAr = "كم عدد $pluralAr؟",
                choices           = generateChoices(targetCount),
                roundIndex        = roundIndex,
                isAnimating       = true,
                startTimeMs       = System.currentTimeMillis()
            )

            delay(800)
            playQuestionAudio(subjectId)
            delay(2200)
            startBounceAnimation(targetCount)
        }
    }

    // ── Bounce animation ──────────────────────────────────────────────────────

    private fun startBounceAnimation(count: Int) {
        animationJob?.cancel()
        animationJob = viewModelScope.launch {
            updatePlaying { it.copy(isAnimating = true, countingStep = -1) }
            for (i in 0 until count) {
                updatePlaying { it.copy(countingStep = i) }
                // TAP sound on each counted item — gated by AppSoundSettings
                appSoundSettings.playSoundEffect(SoundEffect.TAP)
                delay(1500)
            }
            updatePlaying { it.copy(countingStep = -1, isAnimating = false) }
        }
    }

    // ── Answer handling ───────────────────────────────────────────────────────

    fun onAnswerSelected(
        selected  : Int,
        onComplete: (isCorrect: Boolean, elapsedMs: Long, attempts: Int, touchComplexity: Float) -> Unit
    ) {
        val state = _uiState.value as? CountingGameUiState.Playing ?: return
        if (state.selectedAnswer != null || state.isAnimating) return

        val isCorrect   = selected == state.targetCount
        val newAttempts = state.attempts + 1
        val elapsedMs   = System.currentTimeMillis() - state.startTimeMs
        val touch       = computeTouch(newAttempts)

        viewModelScope.launch {
            if (isCorrect) {
                // ── Correct path ──────────────────────────────────────────
                // 1. Mark answer, play CORRECT SFX via AppSoundSettings
                updatePlaying { it.copy(selectedAnswer = selected, isCorrect = true, attempts = newAttempts) }
                appSoundSettings.playSoundEffect(SoundEffect.CORRECT)
                delay(300)

                // 2. Play the number audio (voice content — own MediaPlayer)
                playNumberAudio(state.targetCount)
                delay(1400)

                // 3. Show GoodJobPopup + COMPLETE SFX (same as Trace pattern)
                appSoundSettings.playSoundEffect(SoundEffect.COMPLETE)
                updatePlaying { it.copy(showCelebration = true) }
                delay(2200)

                // 4. Dismiss popup and notify shell
                updatePlaying { it.copy(showCelebration = false) }
                delay(300)
                onComplete(true, elapsedMs, newAttempts, touch)

            } else {
                // ── Wrong path ────────────────────────────────────────────
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
                    onComplete(false, elapsedMs, newAttempts, touch)
                } else {
                    delay(400)
                    updatePlaying { it.copy(selectedAnswer = null, isCorrect = null) }
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun generateChoices(correct: Int): List<Int> {
        val pool = (1..6).toMutableList()
        pool.remove(correct)
        pool.shuffle()
        return (listOf(correct) + pool.take(3)).shuffled()
    }

    private fun computeTouch(attempts: Int): Float =
        (1f - (attempts - 1) * 0.2f).coerceIn(0f, 1f)

    private fun updatePlaying(block: (CountingGameUiState.Playing) -> CountingGameUiState.Playing) {
        val s = _uiState.value as? CountingGameUiState.Playing ?: return
        _uiState.value = block(s)
    }

    // ── Voice audio — own MediaPlayer (content, not SFX) ─────────────────────

    private fun playQuestionAudio(subjectId: String) =
        playAsset("activities/audio/count/count_${subjectId
            .removePrefix("animal_")
            .removePrefix("shape_")}.ogg")

    fun playNumberAudio(number: Int) =
        playAsset("learning_content/audio/numbers/number_$number.ogg")

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
        animationJob?.cancel()
        releasePlayer()
        roundCounts = emptyList()
    }
}