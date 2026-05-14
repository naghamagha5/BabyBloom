package com.babybloom.presentation.viewmodels

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babybloom.R
import com.babybloom.data.local.dao.LearningContentDao
import com.babybloom.data.local.entity.LearningContentEntity
import com.babybloom.di.AppSoundSettings
import com.babybloom.domain.model.ActivityContent
import com.babybloom.util.AssetPathResolver
import com.babybloom.util.ImageAsset
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

// ── Habitat ───────────────────────────────────────────────────────────────────
data class Habitat(
    val id          : String,
    val labelResId  : Int,
    val activeImage : String,
    val calmImage   : String
)

val ALL_HABITATS = listOf(
    Habitat("savanna",  R.string.habitat_savanna,  "Savanna_active.jpeg", "Savanna_calm.jpeg"),
    Habitat("forest",   R.string.habitat_forest,   "Jungle_active.jpeg",  "Jungle_calm.jpeg"),
    Habitat("desert",   R.string.habitat_desert,   "Desert_active.jpg",   "Desert_calm.jpeg"),
    Habitat("farm",     R.string.habitat_farm,     "Farm_active.jpeg",    "Farm_calm.jpeg"),
    Habitat("wetlands", R.string.habitat_wetlands, "Wetlands_active.jpeg","Wetlands_calm.jpeg"),
    Habitat("sea",      R.string.habitat_sea,      "Sea_active.jpeg",     "Sea_active.jpeg"),
    Habitat("birds",    R.string.habitat_birds,    "Birds_active.jpeg",   "Birds_calm.jpeg")
)

val ANIMAL_HABITAT_MAP = mapOf(
    "animal_lion"       to "savanna",
    "animal_giraffe"    to "savanna",
    "animal_elephant"   to "savanna",
    "animal_gazelle"    to "savanna",
    "animal_rhinoceros" to "savanna",
    "animal_tiger"      to "savanna",
    "animal_monkey"     to "forest",
    "animal_chimpanzee" to "forest",
    "animal_deer"       to "forest",
    "animal_wolf"       to "forest",
    "animal_bear"       to "forest",
    "animal_spider"     to "forest",
    "animal_snake"      to "forest",
    "animal_camel"      to "desert",
    "animal_llama"      to "desert",
    "animal_horse"      to "farm",
    "animal_sheep"      to "farm",
    "animal_goat"       to "farm",
    "animal_dog"        to "farm",
    "animal_crocodile"  to "wetlands",
    "animal_frog"       to "wetlands",
    "animal_duck"       to "wetlands",
    "animal_fish"       to "sea",
    "animal_shrimp"     to "sea",
    "animal_falcon"     to "birds",
    "animal_dove"       to "birds",
    "animal_hoopoe"     to "birds",
    "animal_peacock"    to "birds"
)

data class AnimalOption(
    val entity    : LearningContentEntity,
    val habitatId : String
)

enum class AnswerState { Idle, Correct, Wrong, Revealed }

// ── Timings ───────────────────────────────────────────────────────────────────
private const val WIGGLE_HINT_DELAY_MS = 4_000L
private const val ATTEMPT_TIMEOUT_MS   = 15_000L
private const val WRONG_SOUND_DELAY_MS = 200L
private const val WRONG_HIGHLIGHT_MS   = 650L
private const val REVEAL_PAUSE_MS      = 1_500L
private const val CELEBRATION_MS       = 1_800L
private const val LETTER_ANIMAL_GAP_MS = 150L   // gap between letter-name and animal-name audio
private const val MAX_ATTEMPTS         = 3
private const val QUESTIONS_PER_ROUND  = 6

private const val INSTRUCTION_ANIMALS = "activities/audio/match/match_instruction_animals.ogg"
private const val INSTRUCTION_LETTERS = "activities/audio/match/match_instruction_letters.ogg"

// ── UI state ──────────────────────────────────────────────────────────────────
sealed class MatchCardState {
    object Loading : MatchCardState()
    data class Done(val elapsedMs: Long, val correctCount: Int) : MatchCardState()

    data class AnimalHabitatCard(
        val animal           : ActivityContent,
        val options          : List<Habitat>,
        val correctHabitatId : String,
        val answerState      : AnswerState = AnswerState.Idle,
        val attemptsLeft     : Int         = MAX_ATTEMPTS,
        val showCorrectWiggle: Boolean     = false,
        val questionIndex    : Int         = 0,
        val totalQuestions   : Int         = QUESTIONS_PER_ROUND,
        val lastWrongId      : String?     = null,
        val showCelebration  : Boolean     = false,
        val isTest           : Boolean     = false,
        val showHandHint     : Boolean     = false
    ) : MatchCardState()

    data class LetterAnimalCard(
        val letter           : ActivityContent,
        val letterImageAsset : ImageAsset,
        val options          : List<AnimalOption>,
        val correctAnimalId  : String,
        val answerState      : AnswerState = AnswerState.Idle,
        val attemptsLeft     : Int         = MAX_ATTEMPTS,
        val showCorrectWiggle: Boolean     = false,
        val questionIndex    : Int         = 0,
        val totalQuestions   : Int         = QUESTIONS_PER_ROUND,
        val lastWrongId      : String?     = null,
        val showCelebration  : Boolean     = false,
        val isTest           : Boolean     = false,
        val showHandHint     : Boolean     = false
    ) : MatchCardState()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────
@HiltViewModel
class MatchViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val learningContentDao: LearningContentDao,
    private val appSoundSettings: AppSoundSettings
) : ViewModel() {

    private val _cardState  = MutableStateFlow<MatchCardState>(MatchCardState.Loading)
    val cardState: StateFlow<MatchCardState> = _cardState.asStateFlow()

    private val _wiggleTick = MutableStateFlow(0)
    val wiggleTick: StateFlow<Int> = _wiggleTick.asStateFlow()

    private var voicePlayer: MediaPlayer? = null

    private var wiggleHintJob  : Job? = null
    private var attemptTimerJob: Job? = null
    private var questionJob    : Job? = null
    private var answerJob      : Job? = null

    private var items           : List<ActivityContent>                       = emptyList()
    private var matchType       = "ANIMAL_TO_HABITAT"
    private var isTest          = false
    private var currentIndex    = 0
    private var correctCount    = 0
    private var cardCorrect     = 0
    private var cardIncorrect   = 0
    private var cardAttempts    = 0
    private var isCalmMode      = false
    private var onComplete      : ((Long, Int) -> Unit)?                      = null
    private var onCardResult    : ((String, Boolean, Int, Int, Int) -> Unit)? = null
    private var startTime       = 0L
    private var isLoaded        = false
    private var loadedSignature = ""

    private var currentContentId       = ""
    private var currentLetterPath      : String? = null
    private var currentLetterSoundPath : String? = null
    private var currentAnimalPath      : String? = null

    // ═════════════════════════════════════════════════════════════════════════
    // Public API
    // ═════════════════════════════════════════════════════════════════════════

    fun loadActivity(
        contentItems : List<ActivityContent>,
        isCalmMode   : Boolean,
        isTest       : Boolean,
        isAssessment : Boolean,   // reserved for future use
        configJson   : String,
        onCardResult : (contentId: String, isCorrect: Boolean, correct: Int, incorrect: Int, attempts: Int) -> Unit,
        onComplete   : (elapsedMs: Long, correctCount: Int) -> Unit
    ) {
        val signature = listOf(
            configJson, isCalmMode, isTest, isAssessment,
            contentItems.joinToString("|") { it.contentId }
        ).joinToString("#")
        if (isLoaded && loadedSignature == signature) return
        isLoaded        = true
        loadedSignature = signature

        cancelAll()
        this.items        = contentItems.shuffled().take(QUESTIONS_PER_ROUND)
        this.isCalmMode   = isCalmMode
        this.isTest       = isTest
        this.onComplete   = onComplete
        this.onCardResult = onCardResult
        this.currentIndex = 0
        this.correctCount = 0
        this.startTime    = System.currentTimeMillis()
        this.matchType    = when {
            configJson.contains("LETTER_TO_ANIMAL") -> "LETTER_TO_ANIMAL"
            else                                    -> "ANIMAL_TO_HABITAT"
        }
        showQuestion(0)
    }

    fun onFirstInteraction() {
        _cardState.value = when (val s = _cardState.value) {
            is MatchCardState.AnimalHabitatCard -> s.copy(showHandHint = false)
            is MatchCardState.LetterAnimalCard  -> s.copy(showHandHint = false)
            else -> _cardState.value
        }
    }

    fun onAnswerSelected(selectedId: String) {
        val state = _cardState.value
        if (currentAnswerState() != AnswerState.Idle) return

        val isCorrect = when (state) {
            is MatchCardState.AnimalHabitatCard -> selectedId == state.correctHabitatId
            is MatchCardState.LetterAnimalCard  -> selectedId == state.correctAnimalId
            else -> return
        }

        cancelAttemptTimers()
        // Synchronous TAP gives instant auditory feedback and "warms up" the
        // audio engine before the WRONG / CORRECT sound fires in the coroutine.
        appSoundSettings.playSoundEffect(SoundEffect.TAP)
        cardAttempts++

        if (isCorrect) {
            cardCorrect++
            updateAnswerState(AnswerState.Correct)

            answerJob?.cancel()
            answerJob = viewModelScope.launch {
                // FIX: LETTER_TO_ANIMAL now plays letter name THEN animal name
                // so the child hears both upon a correct connection.
                when (matchType) {
                    "LETTER_TO_ANIMAL" -> {
                        currentLetterPath?.let { playVoiceAndWait(it) }
                        delay(LETTER_ANIMAL_GAP_MS)
                        currentAnimalPath?.let { playVoiceAndWait(it) }
                    }
                    "ANIMAL_TO_HABITAT" -> currentAnimalPath?.let { playVoiceAndWait(it) }
                }
                appSoundSettings.playSoundEffect(SoundEffect.CORRECT)
                delay(400)
                appSoundSettings.playSoundEffect(SoundEffect.COMPLETE)
                setCelebration(true)
                delay(CELEBRATION_MS)
                setCelebration(false)
                delay(300)
                onCardResult?.invoke(
                    currentContentId, true, cardCorrect, cardIncorrect, cardAttempts
                )
                advanceQuestion()
            }

        } else {
            cardIncorrect++
            val attemptsLeft = getAttemptsLeft() - 1

            answerJob?.cancel()
            answerJob = viewModelScope.launch {
                // FIX: delay prevents the WRONG sfx from colliding with the
                // MediaPlayer voice audio that may still be finishing.
                delay(WRONG_SOUND_DELAY_MS)
                appSoundSettings.playSoundEffect(SoundEffect.WRONG)

                decrementAttempts(attemptsLeft, selectedId)
                delay(WRONG_HIGHLIGHT_MS)

                if (attemptsLeft <= 0) {
                    revealAndAdvance()
                } else {
                    resetToIdle(attemptsLeft)
                    startAttemptTimer()
                }
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Timer
    // ═════════════════════════════════════════════════════════════════════════

    private fun startAttemptTimer() {
        cancelAttemptTimers()

        wiggleHintJob = viewModelScope.launch {
            delay(WIGGLE_HINT_DELAY_MS)
            if (currentAnswerState() == AnswerState.Idle) {
                setWiggle(true)
                _wiggleTick.value++
            }
        }

        attemptTimerJob = viewModelScope.launch {
            delay(ATTEMPT_TIMEOUT_MS)
            if (currentAnswerState() == AnswerState.Idle) {
                onAttemptTimeout()
            }
        }
    }

    private fun onAttemptTimeout() {
        cancelAttemptTimers()
        cardIncorrect++
        cardAttempts++
        appSoundSettings.playSoundEffect(SoundEffect.WRONG)
        val attemptsLeft = (getAttemptsLeft() - 1).coerceAtLeast(0)
        answerJob?.cancel()
        answerJob = viewModelScope.launch {
            if (attemptsLeft <= 0) revealAndAdvance()
            else { resetToIdle(attemptsLeft); startAttemptTimer() }
        }
    }

    private suspend fun revealAndAdvance() {
        revealCorrect()
        delay(400)
        _wiggleTick.value++
        when (matchType) {
            "LETTER_TO_ANIMAL" -> {
                currentLetterSoundPath?.let { playVoiceAndWait(it) }
                delay(LETTER_ANIMAL_GAP_MS)
                currentAnimalPath?.let { playVoiceAndWait(it) }
            }
            "ANIMAL_TO_HABITAT" -> currentAnimalPath?.let { playVoiceAndWait(it) }
        }
        delay(REVEAL_PAUSE_MS)
        onCardResult?.invoke(
            currentContentId, false, cardCorrect, cardIncorrect, cardAttempts
        )
        advanceQuestion()
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Question builder
    // ═════════════════════════════════════════════════════════════════════════

    private fun showQuestion(index: Int) {
        cancelAttemptTimers()
        cardCorrect   = 0
        cardIncorrect = 0
        cardAttempts  = 0

        val item = items.getOrNull(index) ?: run {
            questionJob?.cancel()
            questionJob = viewModelScope.launch {
                appSoundSettings.playSoundEffect(SoundEffect.COMPLETE)
                delay(1_500)
                val elapsed = System.currentTimeMillis() - startTime
                _cardState.value = MatchCardState.Done(elapsed, correctCount)
                onComplete?.invoke(elapsed, correctCount)
            }
            return
        }

        _cardState.value = MatchCardState.Loading
        questionJob?.cancel()
        questionJob = viewModelScope.launch {
            when (matchType) {
                "LETTER_TO_ANIMAL" -> buildLetterAnimalCard(item, index)
                else               -> buildAnimalHabitatCard(item, index)
            }
            startAttemptTimer()
        }
    }

    private suspend fun buildAnimalHabitatCard(item: ActivityContent, index: Int) {
        val correctId = ANIMAL_HABITAT_MAP[item.contentId] ?: ALL_HABITATS.first().id
        val correct   = ALL_HABITATS.first { it.id == correctId }
        val options: List<Habitat> = if (isTest) {
            val wrong = ALL_HABITATS.filter { it.id != correctId }.shuffled().take(3)
            (wrong + correct).shuffled()
        } else listOf(correct)

        currentContentId       = item.contentId
        currentAnimalPath      = AssetPathResolver.audioPathFor(item.contentId, "ANIMAL")
        currentLetterPath      = null
        currentLetterSoundPath = null

        _cardState.value = MatchCardState.AnimalHabitatCard(
            animal           = item,
            options          = options,
            correctHabitatId = correctId,
            questionIndex    = index,
            totalQuestions   = items.size,
            isTest           = isTest,
            showHandHint     = !isTest   // FIX: ALL learning-mode questions show hint
        )
        playVoiceAndWait(INSTRUCTION_ANIMALS)
        playVoice(currentAnimalPath!!)
    }

    private suspend fun buildLetterAnimalCard(item: ActivityContent, index: Int) {
        val animal = learningContentDao.getByLearningOrderAndCategory(
            item.learningOrder, "ANIMAL"
        )
        if (animal == null) {
            Log.w("MatchVM", "No animal for learningOrder=${item.learningOrder}")
            onCardResult?.invoke(item.contentId, false, 0, 1, 1)
            advanceQuestion(); return
        }

        val options: List<AnimalOption> = if (isTest) {
            val wrongEntities = learningContentDao
                .getByCategory("ANIMAL")
                .filter { it.id != animal.id }
                .shuffled().take(3)
            (wrongEntities + animal).shuffled().map { entity ->
                AnimalOption(entity = entity,
                    habitatId = ANIMAL_HABITAT_MAP[entity.id] ?: ALL_HABITATS.first().id)
            }
        } else {
            listOf(AnimalOption(entity = animal,
                habitatId = ANIMAL_HABITAT_MAP[animal.id] ?: ALL_HABITATS.first().id))
        }

        currentContentId       = item.contentId
        currentLetterPath      = AssetPathResolver.audioPathFor(item.contentId, "LETTER_NAME")
        currentLetterSoundPath = AssetPathResolver.audioPathFor("${item.contentId}_s", "LETTER_SOUND")
        currentAnimalPath      = AssetPathResolver.audioPathFor(animal.id, "ANIMAL")

        _cardState.value = MatchCardState.LetterAnimalCard(
            letter           = item,
            letterImageAsset = AssetPathResolver.imageAssetFor(
                item.contentId, item.category, isCalmMode),
            options          = options,
            correctAnimalId  = animal.id,
            questionIndex    = index,
            totalQuestions   = items.size,
            isTest           = isTest,
            showHandHint     = !isTest
        )
        playVoiceAndWait(INSTRUCTION_LETTERS)
        playVoice(currentLetterPath!!)
    }

    private fun advanceQuestion() { currentIndex++; showQuestion(currentIndex) }

    // ═════════════════════════════════════════════════════════════════════════
    // State helpers
    // ═════════════════════════════════════════════════════════════════════════

    private fun getAttemptsLeft(): Int = when (val s = _cardState.value) {
        is MatchCardState.AnimalHabitatCard -> s.attemptsLeft
        is MatchCardState.LetterAnimalCard  -> s.attemptsLeft
        else -> 0
    }
    private fun setCelebration(show: Boolean) {
        _cardState.value = when (val s = _cardState.value) {
            is MatchCardState.AnimalHabitatCard -> s.copy(showCelebration = show)
            is MatchCardState.LetterAnimalCard  -> s.copy(showCelebration = show)
            else -> _cardState.value
        }
    }
    private fun setWiggle(on: Boolean) {
        _cardState.value = when (val s = _cardState.value) {
            is MatchCardState.AnimalHabitatCard -> s.copy(showCorrectWiggle = on)
            is MatchCardState.LetterAnimalCard  -> s.copy(showCorrectWiggle = on)
            else -> _cardState.value
        }
    }
    private fun revealCorrect() {
        _cardState.value = when (val s = _cardState.value) {
            is MatchCardState.AnimalHabitatCard ->
                s.copy(answerState = AnswerState.Revealed, attemptsLeft = 0, showCorrectWiggle = false)
            is MatchCardState.LetterAnimalCard ->
                s.copy(answerState = AnswerState.Revealed, attemptsLeft = 0, showCorrectWiggle = false)
            else -> _cardState.value
        }
    }
    private fun decrementAttempts(newCount: Int, wrongId: String?) {
        _cardState.value = when (val s = _cardState.value) {
            is MatchCardState.AnimalHabitatCard ->
                s.copy(attemptsLeft = newCount, answerState = AnswerState.Wrong, lastWrongId = wrongId)
            is MatchCardState.LetterAnimalCard ->
                s.copy(attemptsLeft = newCount, answerState = AnswerState.Wrong, lastWrongId = wrongId)
            else -> _cardState.value
        }
    }
    private fun resetToIdle(newCount: Int) {
        _cardState.value = when (val s = _cardState.value) {
            is MatchCardState.AnimalHabitatCard ->
                s.copy(attemptsLeft = newCount, answerState = AnswerState.Idle,
                    lastWrongId = null, showCorrectWiggle = false)
            is MatchCardState.LetterAnimalCard ->
                s.copy(attemptsLeft = newCount, answerState = AnswerState.Idle,
                    lastWrongId = null, showCorrectWiggle = false)
            else -> _cardState.value
        }
    }
    private fun updateAnswerState(state: AnswerState) {
        _cardState.value = when (val s = _cardState.value) {
            is MatchCardState.AnimalHabitatCard -> s.copy(answerState = state)
            is MatchCardState.LetterAnimalCard  -> s.copy(answerState = state)
            else -> _cardState.value
        }
    }
    private fun currentAnswerState(): AnswerState = when (val s = _cardState.value) {
        is MatchCardState.AnimalHabitatCard -> s.answerState
        is MatchCardState.LetterAnimalCard  -> s.answerState
        else -> AnswerState.Idle
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Cancellation
    // ═════════════════════════════════════════════════════════════════════════

    private fun cancelAttemptTimers() {
        wiggleHintJob?.cancel();   wiggleHintJob   = null
        attemptTimerJob?.cancel(); attemptTimerJob = null
        setWiggle(false)
    }
    private fun cancelAll() {
        cancelAttemptTimers()
        questionJob?.cancel(); questionJob = null
        answerJob?.cancel();   answerJob   = null
        _wiggleTick.value = 0
        runCatching { voicePlayer?.stop() }
        voicePlayer?.release(); voicePlayer = null
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Voice audio  (AppSoundSettings handles SFX; MediaPlayer handles speech)
    // ═════════════════════════════════════════════════════════════════════════

    private fun playVoice(path: String) {
        try {
            voicePlayer?.stop(); voicePlayer?.release()
            voicePlayer = MediaPlayer()
            val afd = context.assets.openFd(path)
            voicePlayer!!.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            voicePlayer!!.setOnErrorListener { _, _, _ -> Log.w("MatchVM","Voice err: $path"); true }
            voicePlayer!!.prepare(); voicePlayer!!.start()
        } catch (e: Exception) { Log.w("MatchVM","Voice not found: $path") }
    }
    private suspend fun playVoiceAndWait(path: String) {
        try {
            voicePlayer?.stop(); voicePlayer?.release()
            voicePlayer = MediaPlayer()
            val afd = context.assets.openFd(path)
            voicePlayer!!.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            voicePlayer!!.prepare(); voicePlayer!!.start()
            delay(voicePlayer!!.duration.toLong().coerceAtLeast(600L) + 150)
        } catch (e: Exception) { Log.w("MatchVM","Voice not found: $path"); delay(600) }
    }

    override fun onCleared() { super.onCleared(); cancelAll() }
}