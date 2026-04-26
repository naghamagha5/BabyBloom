package com.babybloom.presentation.viewmodels

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babybloom.R
import com.babybloom.data.local.dao.LearningContentDao
import com.babybloom.data.local.entity.LearningContentEntity
import com.babybloom.domain.model.ActivityContent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── Habitat definition ────────────────────────────────────────────────────────
data class Habitat(
    val id: String,
    val labelResId: Int,      // R.string.habitat_* — mirrors how animal labels work
    val activeImage: String,
    val calmImage: String
)

val ALL_HABITATS = listOf(
    Habitat("savanna",  R.string.habitat_savanna,  "Savanna.jpeg",   "Savanna_calm.jpeg"),
    Habitat("forest",   R.string.habitat_forest,   "Jungle(1).jpeg", "Jungle_calm.jpeg"),
    Habitat("desert",   R.string.habitat_desert,   "Desert(1).jpg",  "Desert_calm.jpeg"),
    Habitat("farm",     R.string.habitat_farm,     "Farm(1).jpeg",   "Farm_calm.jpeg"),
    Habitat("wetlands", R.string.habitat_wetlands, "Wetlands.jpeg",  "Wetlands_calm.jpeg"),
    Habitat("sea",      R.string.habitat_sea,      "Sea(1).jpeg",    "Sea(1).jpeg"),
    Habitat("birds",    R.string.habitat_birds,    "Birds.jpeg",     "Birds_calm.jpeg")
)

// ── Animal → Habitat mapping ──────────────────────────────────────────────────
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

// ── Audio path helpers ────────────────────────────────────────────────────────
fun letterNameAudioPath(contentId: String) =
    "learning_content/audio/name of letters/$contentId.ogg"

fun letterSoundAudioPath(contentId: String) =
    "learning_content/audio/sound of letters/${contentId}_s.ogg"

fun animalAudioPath(contentId: String) =
    "learning_content/audio/animals/$contentId.ogg"

// ── SFX & instruction audio ───────────────────────────────────────────────────
private const val SFX_TAP             = "learning_content/audio/tap.ogg"
private const val SFX_CORRECT         = "learning_content/audio/correct.ogg"
private const val SFX_WRONG           = "learning_content/audio/wrong.ogg"
private const val SFX_COMPLETE        = "learning_content/audio/complete.ogg"
private const val INSTRUCTION_ANIMALS = "learning_content/audio/match_instruction_animals.ogg"
private const val INSTRUCTION_LETTERS = "learning_content/audio/match_instruction_letters.ogg"

// ── Timings & limits ──────────────────────────────────────────────────────────
private const val HINT_WIGGLE_MS      = 5_000L
private const val MAX_ATTEMPTS        = 3
private const val QUESTIONS_PER_ROUND = 6

// ── Data classes ──────────────────────────────────────────────────────────────
data class AnimalOption(
    val entity: LearningContentEntity,
    val habitatId: String
)

// ── UI State ──────────────────────────────────────────────────────────────────
sealed class MatchCardState {
    object Loading : MatchCardState()
    data class Done(val elapsedMs: Long, val correctCount: Int) : MatchCardState()

    data class AnimalHabitatCard(
        val animal: ActivityContent,
        val options: List<Habitat>,
        val correctHabitatId: String,
        val answerState: AnswerState = AnswerState.Idle,
        val attemptsLeft: Int = MAX_ATTEMPTS,
        val showCorrectWiggle: Boolean = false,
        val questionIndex: Int = 0,
        val totalQuestions: Int = QUESTIONS_PER_ROUND,
        val lastWrongId: String? = null
    ) : MatchCardState()

    data class LetterAnimalCard(
        val letter: ActivityContent,
        val options: List<AnimalOption>,
        val correctAnimalId: String,
        val answerState: AnswerState = AnswerState.Idle,
        val attemptsLeft: Int = MAX_ATTEMPTS,
        val showCorrectWiggle: Boolean = false,
        val questionIndex: Int = 0,
        val totalQuestions: Int = QUESTIONS_PER_ROUND,
        val lastWrongId: String? = null
    ) : MatchCardState()
}

enum class AnswerState { Idle, Correct, Wrong, Revealed }

// ── ViewModel ─────────────────────────────────────────────────────────────────
@HiltViewModel
class MatchViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val learningContentDao: LearningContentDao
) : ViewModel() {

    private val _cardState  = MutableStateFlow<MatchCardState>(MatchCardState.Loading)
    val cardState: StateFlow<MatchCardState> = _cardState.asStateFlow()

    private val _wiggleTick = MutableStateFlow(0)
    val wiggleTick: StateFlow<Int> = _wiggleTick.asStateFlow()

    private var voicePlayer: MediaPlayer? = null
    private var sfxPlayer:   MediaPlayer? = null
    private var hintJob:     Job?         = null

    private var items:          List<ActivityContent> = emptyList()
    private var matchType       = "ANIMAL_TO_HABITAT"
    private var currentIndex    = 0
    private var correctCount    = 0
    private var cardCorrect     = 0
    private var cardIncorrect   = 0
    private var cardAttempts    = 0
    private var isCalmMode      = false
    private var onComplete:     ((Long, Int) -> Unit)? = null
    private var onCardResult:   ((String, Boolean, Int, Int, Int) -> Unit)? = null
    private var startTime       = 0L
    private var isLoaded        = false

    private var currentContentId:       String  = ""
    private var currentLetterPath:      String? = null
    private var currentLetterSoundPath: String? = null
    private var currentAnimalPath:      String? = null

    // ── Public API ────────────────────────────────────────────────────────────

    fun loadActivity(
        contentItems: List<ActivityContent>,
        isCalmMode: Boolean,
        configJson: String,
        onCardResult: (contentId: String, isCorrect: Boolean, correct: Int, incorrect: Int, attempts: Int) -> Unit,
        onComplete: (elapsedMs: Long, correctCount: Int) -> Unit
    ) {
        if (isLoaded) return
        isLoaded = true
        this.items          = contentItems.shuffled().take(QUESTIONS_PER_ROUND)
        this.isCalmMode     = isCalmMode
        this.onComplete     = onComplete
        this.onCardResult   = onCardResult
        this.currentIndex   = 0
        this.correctCount   = 0
        this.cardCorrect    = 0
        this.cardIncorrect  = 0
        this.cardAttempts   = 0
        this.startTime      = System.currentTimeMillis()
        this.matchType      = when {
            configJson.contains("LETTER_TO_ANIMAL") -> "LETTER_TO_ANIMAL"
            else                                    -> "ANIMAL_TO_HABITAT"
        }
        showQuestion(0)
    }

    fun onAnswerSelected(selectedId: String) {
        val state = _cardState.value
        if (currentAnswerState() !in listOf(AnswerState.Idle, AnswerState.Wrong)) return

        val isCorrect = when (state) {
            is MatchCardState.AnimalHabitatCard -> selectedId == state.correctHabitatId
            is MatchCardState.LetterAnimalCard  -> selectedId == state.correctAnimalId
            else -> return
        }

        cancelHints()
        playSfx(SFX_TAP)

        if (isCorrect) {
            correctCount++
            cardCorrect++
            cardAttempts++
            updateAnswerState(AnswerState.Correct)
            onCardResult?.invoke(currentContentId, true, cardCorrect, cardIncorrect, cardAttempts)

            viewModelScope.launch {
                delay(250)
                playSfx(SFX_CORRECT)
                delay(700)
                when (matchType) {
                    "LETTER_TO_ANIMAL"  -> currentLetterPath?.let { playVoiceAndWait(it) }
                    "ANIMAL_TO_HABITAT" -> currentAnimalPath?.let { playVoiceAndWait(it) }
                }
                delay(600)
                advanceQuestion()
            }
        } else {
            cardIncorrect++
            cardAttempts++
            val attemptsLeft = when (state) {
                is MatchCardState.AnimalHabitatCard -> state.attemptsLeft - 1
                is MatchCardState.LetterAnimalCard  -> state.attemptsLeft - 1
                else -> 0
            }

            viewModelScope.launch {
                delay(250)
                playSfx(SFX_WRONG)

                if (attemptsLeft <= 0) {
                    onCardResult?.invoke(currentContentId, false, cardCorrect, cardIncorrect, cardAttempts)
                    revealCorrect()
                    delay(300)
                    _wiggleTick.value++
                    delay(400)
                    when (matchType) {
                        "LETTER_TO_ANIMAL" -> {
                            currentLetterSoundPath?.let { playVoiceAndWait(it) }
                            delay(150)
                            currentAnimalPath?.let { playVoiceAndWait(it) }
                        }
                        "ANIMAL_TO_HABITAT" -> currentAnimalPath?.let { playVoiceAndWait(it) }
                    }
                    delay(1_200)
                    advanceQuestion()
                } else {
                    decrementAttempts(attemptsLeft, selectedId)
                    when (matchType) {
                        "LETTER_TO_ANIMAL" -> {
                            currentLetterSoundPath?.let { playVoiceAndWait(it) }
                            delay(150)
                            currentAnimalPath?.let { playVoiceAndWait(it) }
                        }
                        "ANIMAL_TO_HABITAT" -> currentAnimalPath?.let { playVoiceAndWait(it) }
                    }
                    startHintTimer()
                }
            }
        }
    }

    // ── Question builder ──────────────────────────────────────────────────────

    private fun showQuestion(index: Int) {
        cancelHints()
        cardCorrect   = 0
        cardIncorrect = 0
        cardAttempts  = 0

        val item = items.getOrNull(index) ?: run {
            viewModelScope.launch {
                playSfx(SFX_COMPLETE)
                delay(1_500)
                val elapsed = System.currentTimeMillis() - startTime
                _cardState.value = MatchCardState.Done(elapsed, correctCount)
                onComplete?.invoke(elapsed, correctCount)
            }
            return
        }
        _cardState.value = MatchCardState.Loading
        viewModelScope.launch {
            when (matchType) {
                "LETTER_TO_ANIMAL" -> buildLetterAnimalCard(item, index)
                else               -> buildAnimalHabitatCard(item, index)
            }
            startHintTimer()
        }
    }

    private suspend fun buildAnimalHabitatCard(item: ActivityContent, index: Int) {
        val correctId = ANIMAL_HABITAT_MAP[item.contentId] ?: ALL_HABITATS.first().id
        val correct   = ALL_HABITATS.first { it.id == correctId }
        val wrong     = ALL_HABITATS.filter { it.id != correctId }.shuffled().take(3)
        val options   = (wrong + correct).shuffled()

        currentContentId       = item.contentId
        currentAnimalPath      = animalAudioPath(item.contentId)
        currentLetterPath      = null
        currentLetterSoundPath = null

        _cardState.value = MatchCardState.AnimalHabitatCard(
            animal           = item,
            options          = options,
            correctHabitatId = correctId,
            questionIndex    = index,
            totalQuestions   = items.size
        )

        playVoiceAndWait(INSTRUCTION_ANIMALS)
        playVoice(currentAnimalPath!!)
    }

    private suspend fun buildLetterAnimalCard(item: ActivityContent, index: Int) {
        val animal = learningContentDao.getByLearningOrderAndCategory(
            item.learningOrder, "ANIMAL"
        )

        if (animal == null) {
            Log.w("MatchVM", "No animal for learningOrder=${item.learningOrder}, skipping")
            advanceQuestion()
            return
        }

        val wrongEntities = learningContentDao
            .getByCategory("ANIMAL")
            .filter { it.id != animal.id }
            .shuffled()
            .take(3)

        val options = (wrongEntities + animal).shuffled().map { entity ->
            AnimalOption(
                entity    = entity,
                habitatId = ANIMAL_HABITAT_MAP[entity.id] ?: ALL_HABITATS.first().id
            )
        }

        currentContentId       = item.contentId
        currentLetterPath      = letterNameAudioPath(item.contentId)
        currentLetterSoundPath = letterSoundAudioPath(item.contentId)
        currentAnimalPath      = animalAudioPath(animal.id)

        _cardState.value = MatchCardState.LetterAnimalCard(
            letter          = item,
            options         = options,
            correctAnimalId = animal.id,
            questionIndex   = index,
            totalQuestions  = items.size
        )

        playVoiceAndWait(INSTRUCTION_LETTERS)
        playVoice(currentLetterPath!!)
    }

    private fun advanceQuestion() {
        currentIndex++
        showQuestion(currentIndex)
    }

    // ── Hint timer ────────────────────────────────────────────────────────────

    private fun startHintTimer() {
        hintJob?.cancel()
        var hintCount = 0
        hintJob = viewModelScope.launch {
            delay(HINT_WIGGLE_MS)
            if (currentAnswerState() !in listOf(AnswerState.Idle, AnswerState.Wrong)) return@launch

            while (currentAnswerState() in listOf(AnswerState.Idle, AnswerState.Wrong)) {
                hintCount++
                when {
                    hintCount == 1 -> {
                        setWiggle(true); _wiggleTick.value++
                        delay(1_500); setWiggle(false)
                    }
                    hintCount == 2 -> {
                        setWiggle(true); _wiggleTick.value++
                        currentAnimalPath?.let { playVoiceAndWait(it) }
                        delay(500); setWiggle(false)
                    }
                    else -> {
                        setWiggle(true); _wiggleTick.value++
                        when (matchType) {
                            "LETTER_TO_ANIMAL" -> {
                                currentLetterSoundPath?.let { playVoiceAndWait(it) }
                                delay(150)
                                currentAnimalPath?.let { playVoiceAndWait(it) }
                            }
                            "ANIMAL_TO_HABITAT" -> currentAnimalPath?.let { playVoiceAndWait(it) }
                        }
                        delay(500); setWiggle(false)
                    }
                }
                delay(2_000)
            }
        }
    }

    private fun cancelHints() {
        hintJob?.cancel(); hintJob = null
        _wiggleTick.value = 0
        setWiggle(false)
    }

    // ── State helpers ─────────────────────────────────────────────────────────

    private fun setWiggle(on: Boolean) {
        _cardState.value = when (val s = _cardState.value) {
            is MatchCardState.AnimalHabitatCard -> s.copy(showCorrectWiggle = on)
            is MatchCardState.LetterAnimalCard  -> s.copy(showCorrectWiggle = on)
            else -> _cardState.value
        }
    }

    private fun revealCorrect() {
        _cardState.value = when (val s = _cardState.value) {
            is MatchCardState.AnimalHabitatCard -> s.copy(answerState = AnswerState.Revealed, attemptsLeft = 0)
            is MatchCardState.LetterAnimalCard  -> s.copy(answerState = AnswerState.Revealed, attemptsLeft = 0)
            else -> _cardState.value
        }
    }

    private fun decrementAttempts(newCount: Int, wrongId: String) {
        _cardState.value = when (val s = _cardState.value) {
            is MatchCardState.AnimalHabitatCard -> s.copy(attemptsLeft = newCount, answerState = AnswerState.Wrong, lastWrongId = wrongId)
            is MatchCardState.LetterAnimalCard  -> s.copy(attemptsLeft = newCount, answerState = AnswerState.Wrong, lastWrongId = wrongId)
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

    // ── Audio ─────────────────────────────────────────────────────────────────

    private fun playVoice(path: String) {
        try {
            voicePlayer?.stop(); voicePlayer?.release()
            voicePlayer = MediaPlayer()
            val afd = context.assets.openFd(path)
            voicePlayer!!.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            voicePlayer!!.setOnErrorListener { _, _, _ -> Log.w("MatchVM", "Voice err: $path"); true }
            voicePlayer!!.prepare(); voicePlayer!!.start()
        } catch (e: Exception) { Log.w("MatchVM", "Voice not found: $path") }
    }

    private suspend fun playVoiceAndWait(path: String) {
        try {
            voicePlayer?.stop(); voicePlayer?.release()
            voicePlayer = MediaPlayer()
            val afd = context.assets.openFd(path)
            voicePlayer!!.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            voicePlayer!!.prepare(); voicePlayer!!.start()
            delay(voicePlayer!!.duration.toLong().coerceAtLeast(600L) + 150)
        } catch (e: Exception) { Log.w("MatchVM", "Voice not found: $path"); delay(600) }
    }

    private fun playSfx(path: String) {
        try {
            sfxPlayer?.stop(); sfxPlayer?.release()
            sfxPlayer = MediaPlayer()
            val afd = context.assets.openFd(path)
            sfxPlayer!!.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            sfxPlayer!!.setOnErrorListener { _, _, _ -> Log.w("MatchVM", "SFX err: $path"); true }
            sfxPlayer!!.prepare(); sfxPlayer!!.start()
        } catch (e: Exception) { Log.w("MatchVM", "SFX not found: $path") }
    }

    override fun onCleared() {
        super.onCleared()
        cancelHints()
        voicePlayer?.stop(); voicePlayer?.release(); voicePlayer = null
        sfxPlayer?.stop();   sfxPlayer?.release();   sfxPlayer   = null
    }
}