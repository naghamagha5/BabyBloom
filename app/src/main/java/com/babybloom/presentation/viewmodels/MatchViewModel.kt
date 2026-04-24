package com.babybloom.presentation.viewmodels

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val labelAr: String,
    val activeImage: String,
    val calmImage: String
)

val ALL_HABITATS = listOf(
    Habitat("savanna",  "السَّافَانا",  "Savanna.jpeg",   "Savanna_calm.jpeg"),
    Habitat("forest",   "الغَابَة",    "Jungle(1).jpeg", "Jungle_calm.jpeg"),
    Habitat("desert",   "الصَّحْرَاء", "Desert(1).jpg",  "Desert_calm.jpeg"),
    Habitat("farm",     "المَزْرَعَة",  "Farm(1).jpeg",   "Farm_calm.jpeg"),
    Habitat("wetlands", "الأَهْوَار",  "Wetlands.jpeg",  "Wetlands_calm.jpeg"),
    Habitat("sea",      "البَحْر",     "Sea(1).jpeg",    "Sea(1).jpeg"),
    Habitat("birds",    "السَّمَاء",   "Birds.jpeg",     "Birds_calm.jpeg")
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

// ── Letter → Animal mapping ───────────────────────────────────────────────────
val LETTER_ANIMAL_MAP = mapOf(
    "letter_alef"  to "animal_elephant",
    "letter_ba"    to "animal_duck",
    "letter_ta"    to "animal_tiger",
    "letter_tha"   to "animal_snake",
    "letter_jeem"  to "animal_camel",
    "letter_ha"    to "animal_horse",
    "letter_kha"   to "animal_sheep",
    "letter_dal"   to "animal_bear",
    "letter_thal"  to "animal_wolf",
    "letter_ra"    to "animal_rhinoceros",
    "letter_zay"   to "animal_giraffe",
    "letter_seen"  to "animal_fish",
    "letter_sheen" to "animal_sheep",
    "letter_sad"   to "animal_falcon",
    "letter_dad"   to "animal_frog",
    "letter_tah"   to "animal_peacock",
    "letter_zah"   to "animal_gazelle",
    "letter_ain"   to "animal_spider",
    "letter_ghayn" to "animal_gazelle",
    "letter_fa"    to "animal_horse",
    "letter_qaf"   to "animal_monkey",
    "letter_kaf"   to "animal_dog",
    "letter_lam"   to "animal_lion",
    "letter_meem"  to "animal_goat",
    "letter_noon"  to "animal_dove",
    "letter_ha2"   to "animal_hoopoe",
    "letter_waw"   to "animal_hoopoe",
    "letter_ya"    to "animal_deer"
)

// ── Number Arabic labels ──────────────────────────────────────────────────────
val NUMBER_LABEL_AR = mapOf(
    1 to "١", 2 to "٢", 3 to "٣", 4 to "٤", 5 to "٥",
    6 to "٦", 7 to "٧", 8 to "٨", 9 to "٩", 10 to "١٠"
)

// ── Audio path helpers ────────────────────────────────────────────────────────
private fun letterAudioPath(contentId: String) =
    "learning_content/audio/name of letters/$contentId.ogg"

private fun animalAudioPath(contentId: String) =
    "learning_content/audio/animals/$contentId.ogg"

private fun numberAudioPath(n: Int) =
    "learning_content/audio/numbers/number_$n.ogg"

// ── SFX paths ─────────────────────────────────────────────────────────────────
private const val SFX_TAP      = "learning_content/audio/tap.ogg"
private const val SFX_CORRECT  = "learning_content/audio/correct.ogg"
private const val SFX_WRONG    = "learning_content/audio/wrong.ogg"
private const val SFX_COMPLETE = "learning_content/audio/complete.ogg"

// ── Hint timings ──────────────────────────────────────────────────────────────
private const val HINT_WIGGLE_MS = 5_000L
private const val HINT_AUDIO_MS  = 10_000L

// ── Number option ─────────────────────────────────────────────────────────────
data class NumberOption(
    val value: Int,          // 1–10
    val labelAr: String      // ١ ٢ ٣ …
)

// ── UI State ──────────────────────────────────────────────────────────────────
sealed class MatchCardState {
    object Loading : MatchCardState()

    data class AnimalHabitatCard(
        val animal: ActivityContent,
        val options: List<Habitat>,
        val correctHabitatId: String,
        val answerState: AnswerState = AnswerState.Idle,
        val questionIndex: Int = 0,
        val totalQuestions: Int = 1,
        val showCorrectWiggle: Boolean = false,
        val showAudioHint: Boolean = false
    ) : MatchCardState()

    data class LetterAnimalCard(
        val letter: ActivityContent,
        val options: List<AnimalOption>,
        val correctAnimalId: String,
        val answerState: AnswerState = AnswerState.Idle,
        val questionIndex: Int = 0,
        val totalQuestions: Int = 1,
        val showCorrectWiggle: Boolean = false,
        val showAudioHint: Boolean = false
    ) : MatchCardState()

    data class CountNumberCard(
        val animalId: String,          // which animal to display
        val count: Int,                // how many to show (1–10)
        val options: List<NumberOption>,
        val correctNumber: Int,
        val answerState: AnswerState = AnswerState.Idle,
        val questionIndex: Int = 0,
        val totalQuestions: Int = 1,
        val showCorrectWiggle: Boolean = false,
        val showAudioHint: Boolean = false,
        // counting hint: which animal index is currently glowing (0-based, -1 = none)
        val countingGlowIndex: Int = -1
    ) : MatchCardState()
}

data class AnimalOption(
    val entity: LearningContentEntity,
    val habitatId: String
)

enum class AnswerState { Idle, Correct, Wrong }

// ── ViewModel ─────────────────────────────────────────────────────────────────
@HiltViewModel
class MatchViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val learningContentDao: LearningContentDao
) : ViewModel() {

    private val _cardState = MutableStateFlow<MatchCardState>(MatchCardState.Loading)
    val cardState: StateFlow<MatchCardState> = _cardState.asStateFlow()

    private val _wiggleTick = MutableStateFlow(0)
    val wiggleTick: StateFlow<Int> = _wiggleTick.asStateFlow()

    private var voicePlayer: MediaPlayer? = null
    private var sfxPlayer:   MediaPlayer? = null

    private var items: List<ActivityContent> = emptyList()
    private var matchType    = "ANIMAL_TO_HABITAT"
    private var currentIndex = 0
    private var isCalmMode   = false
    private var onComplete: ((Long) -> Unit)? = null
    private var startTime    = 0L

    private var hintJob: Job? = null

    private var currentLetterAudioPath: String? = null
    private var currentAnimalAudioPath: String?  = null
    private var currentCountAnimalId:   String?  = null
    private var currentCorrectNumber:   Int      = 0

    // ── Public API ────────────────────────────────────────────────────────────

    fun loadActivity(
        contentItems: List<ActivityContent>,
        isCalmMode: Boolean,
        configJson: String,
        onComplete: (elapsedMs: Long) -> Unit
    ) {
        this.items        = contentItems.shuffled()
        this.isCalmMode   = isCalmMode
        this.onComplete   = onComplete
        this.currentIndex = 0
        this.startTime    = System.currentTimeMillis()
        this.matchType = when {
            configJson.contains("LETTER_TO_ANIMAL") -> "LETTER_TO_ANIMAL"
            configJson.contains("COUNT_TO_NUMBER")  -> "COUNT_TO_NUMBER"
            else                                    -> "ANIMAL_TO_HABITAT"
        }
        showQuestion(0)
    }

    fun onAnswerSelected(selectedId: String) {
        val isCorrect = when (val s = _cardState.value) {
            is MatchCardState.AnimalHabitatCard -> selectedId == s.correctHabitatId
            is MatchCardState.LetterAnimalCard  -> selectedId == s.correctAnimalId
            is MatchCardState.CountNumberCard   -> selectedId == s.correctNumber.toString()
            else -> return
        }
        if (currentAnswerState() != AnswerState.Idle) return

        cancelHints()
        playSfx(SFX_TAP)
        updateAnswerState(if (isCorrect) AnswerState.Correct else AnswerState.Wrong)

        viewModelScope.launch {
            delay(300)
            playSfx(if (isCorrect) SFX_CORRECT else SFX_WRONG)
            delay(800)

            when (matchType) {
                "LETTER_TO_ANIMAL" -> {
                    currentLetterAudioPath?.let { playVoiceAndWait(it) }
                    delay(300)
                    currentAnimalAudioPath?.let { playVoiceAndWait(it) }
                    delay(500)
                }
                "COUNT_TO_NUMBER" -> {
                    // play the correct number name after answer
                    playVoiceAndWait(numberAudioPath(currentCorrectNumber))
                    delay(500)
                }
                else -> {
                    currentLetterAudioPath?.let { playVoiceAndWait(it) }
                    delay(500)
                }
            }

            currentIndex++
            showQuestion(currentIndex)
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun showQuestion(index: Int) {
        val item = items.getOrNull(index) ?: run {
            viewModelScope.launch {
                playSfx(SFX_COMPLETE)
                delay(1_200)
                onComplete?.invoke(System.currentTimeMillis() - startTime)
            }
            return
        }
        cancelHints()
        _cardState.value = MatchCardState.Loading

        viewModelScope.launch {
            when (matchType) {
                "LETTER_TO_ANIMAL" -> buildLetterAnimalCard(item, index)
                "COUNT_TO_NUMBER"  -> buildCountNumberCard(item, index)
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

        currentLetterAudioPath = animalAudioPath(item.contentId)
        currentAnimalAudioPath = null

        _cardState.value = MatchCardState.AnimalHabitatCard(
            animal           = item,
            options          = options,
            correctHabitatId = correctId,
            questionIndex    = index,
            totalQuestions   = items.size
        )
        playVoice(currentLetterAudioPath!!)
    }

    private suspend fun buildLetterAnimalCard(item: ActivityContent, index: Int) {
        val correctAnimalId = LETTER_ANIMAL_MAP[item.contentId] ?: run {
            currentIndex++
            showQuestion(currentIndex)
            return
        }

        val correctEntity = learningContentDao.getById(correctAnimalId)
            ?: learningContentDao.getByCategory("ANIMAL").firstOrNull()
            ?: return

        val wrongEntities = learningContentDao
            .getByCategory("ANIMAL")
            .filter { it.id != correctAnimalId }
            .shuffled()
            .take(3)

        val allEntities = (wrongEntities + correctEntity).shuffled()
        val options = allEntities.map { entity ->
            AnimalOption(
                entity    = entity,
                habitatId = ANIMAL_HABITAT_MAP[entity.id] ?: ALL_HABITATS.first().id
            )
        }

        currentLetterAudioPath = letterAudioPath(item.contentId)
        currentAnimalAudioPath  = animalAudioPath(correctAnimalId)

        _cardState.value = MatchCardState.LetterAnimalCard(
            letter          = item,
            options         = options,
            correctAnimalId = correctAnimalId,
            questionIndex   = index,
            totalQuestions  = items.size
        )
        playVoice(currentLetterAudioPath!!)
    }

    private suspend fun buildCountNumberCard(item: ActivityContent, index: Int) {
        // item.contentId = "number_3" → correctNumber = 3
        val correctNumber = item.contentId.removePrefix("number_").toIntOrNull() ?: 1

        // pick a random animal to display
        val allAnimals = ANIMAL_HABITAT_MAP.keys.toList()
        val animalId   = allAnimals.random()

        // build 4 number options: correct + 3 random different ones from 1–10
        val wrongNumbers = (1..10)
            .filter { it != correctNumber }
            .shuffled()
            .take(3)
        val options = (wrongNumbers + correctNumber)
            .shuffled()
            .map { n -> NumberOption(value = n, labelAr = NUMBER_LABEL_AR[n] ?: n.toString()) }

        currentCountAnimalId  = animalId
        currentCorrectNumber  = correctNumber
        currentLetterAudioPath = null
        currentAnimalAudioPath = null

        _cardState.value = MatchCardState.CountNumberCard(
            animalId      = animalId,
            count         = correctNumber,
            options       = options,
            correctNumber = correctNumber,
            questionIndex = index,
            totalQuestions = items.size
        )

        // play number audio on question show
        playVoice(numberAudioPath(correctNumber))
    }

    // ── Hint timer ────────────────────────────────────────────────────────────

    private fun startHintTimer() {
        hintJob = viewModelScope.launch {
            if (matchType == "COUNT_TO_NUMBER") {
                // 10 s → start counting hint: glow each animal one by one with audio
                delay(HINT_AUDIO_MS)
                if (currentAnswerState() != AnswerState.Idle) return@launch

                // repeat counting hint + wiggle every 2 s until tapped
                while (currentAnswerState() == AnswerState.Idle) {
                    val count = currentCorrectNumber

                    // glow + say each number 1..count
                    for (i in 0 until count) {
                        if (currentAnswerState() != AnswerState.Idle) break
                        setCountGlowIndex(i)
                        playVoiceAndWait(numberAudioPath(i + 1))
                        delay(200)
                    }
                    setCountGlowIndex(-1)

                    // wiggle the correct answer
                    if (currentAnswerState() == AnswerState.Idle) {
                        setWiggleHint(true)
                        _wiggleTick.value++
                    }

                    delay(2_000)
                }

            } else {
                // Games 1 & 2 — original 5 s wiggle, 10 s audio, repeat every 2 s
                delay(HINT_WIGGLE_MS)
                if (currentAnswerState() != AnswerState.Idle) return@launch
                setWiggleHint(true)

                delay(HINT_AUDIO_MS - HINT_WIGGLE_MS)
                if (currentAnswerState() != AnswerState.Idle) return@launch
                setAudioHint(true)

                while (currentAnswerState() == AnswerState.Idle) {
                    _wiggleTick.value++

                    currentLetterAudioPath?.let { playVoiceAndWait(it) }
                    if (matchType == "LETTER_TO_ANIMAL") {
                        delay(300)
                        currentAnimalAudioPath?.let { playVoiceAndWait(it) }
                    }

                    delay(2_000)
                }
            }
        }
    }

    private fun setCountGlowIndex(index: Int) {
        val s = _cardState.value
        if (s is MatchCardState.CountNumberCard) {
            _cardState.value = s.copy(countingGlowIndex = index)
        }
    }

    private fun cancelHints() {
        hintJob?.cancel()
        hintJob = null
        _wiggleTick.value = 0
        when (val s = _cardState.value) {
            is MatchCardState.AnimalHabitatCard ->
                _cardState.value = s.copy(showCorrectWiggle = false, showAudioHint = false)
            is MatchCardState.LetterAnimalCard  ->
                _cardState.value = s.copy(showCorrectWiggle = false, showAudioHint = false)
            is MatchCardState.CountNumberCard   ->
                _cardState.value = s.copy(
                    showCorrectWiggle = false,
                    showAudioHint     = false,
                    countingGlowIndex = -1
                )
            else -> Unit
        }
    }

    private fun setWiggleHint(on: Boolean) {
        _cardState.value = when (val s = _cardState.value) {
            is MatchCardState.AnimalHabitatCard -> s.copy(showCorrectWiggle = on)
            is MatchCardState.LetterAnimalCard  -> s.copy(showCorrectWiggle = on)
            is MatchCardState.CountNumberCard   -> s.copy(showCorrectWiggle = on)
            else -> _cardState.value
        }
    }

    private fun setAudioHint(on: Boolean) {
        _cardState.value = when (val s = _cardState.value) {
            is MatchCardState.AnimalHabitatCard -> s.copy(showAudioHint = on)
            is MatchCardState.LetterAnimalCard  -> s.copy(showAudioHint = on)
            is MatchCardState.CountNumberCard   -> s.copy(showAudioHint = on)
            else -> _cardState.value
        }
    }

    private fun currentAnswerState(): AnswerState = when (val s = _cardState.value) {
        is MatchCardState.AnimalHabitatCard -> s.answerState
        is MatchCardState.LetterAnimalCard  -> s.answerState
        is MatchCardState.CountNumberCard   -> s.answerState
        else -> AnswerState.Idle
    }

    private fun updateAnswerState(state: AnswerState) {
        _cardState.value = when (val s = _cardState.value) {
            is MatchCardState.AnimalHabitatCard -> s.copy(answerState = state)
            is MatchCardState.LetterAnimalCard  -> s.copy(answerState = state)
            is MatchCardState.CountNumberCard   -> s.copy(answerState = state)
            else -> _cardState.value
        }
    }

    // ── Audio ─────────────────────────────────────────────────────────────────

    private fun playVoice(path: String) {
        try {
            voicePlayer?.stop()
            voicePlayer?.release()
            voicePlayer = MediaPlayer()
            val afd = context.assets.openFd(path)
            voicePlayer!!.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            voicePlayer!!.setOnErrorListener { _, _, _ ->
                Log.w("MatchVM", "Voice error: $path"); true
            }
            voicePlayer!!.prepare()
            voicePlayer!!.start()
        } catch (e: Exception) {
            Log.w("MatchVM", "Voice not found: $path — ${e.message}")
        }
    }

    private suspend fun playVoiceAndWait(path: String) {
        try {
            voicePlayer?.stop()
            voicePlayer?.release()
            voicePlayer = MediaPlayer()
            val afd = context.assets.openFd(path)
            voicePlayer!!.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            voicePlayer!!.prepare()
            voicePlayer!!.start()
            val durationMs = voicePlayer!!.duration.toLong().coerceAtLeast(600L)
            delay(durationMs + 150)
        } catch (e: Exception) {
            Log.w("MatchVM", "Voice not found: $path — ${e.message}")
            delay(600)
        }
    }

    private fun playSfx(path: String) {
        try {
            sfxPlayer?.stop()
            sfxPlayer?.release()
            sfxPlayer = MediaPlayer()
            val afd = context.assets.openFd(path)
            sfxPlayer!!.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            sfxPlayer!!.setOnErrorListener { _, _, _ ->
                Log.w("MatchVM", "SFX error: $path"); true
            }
            sfxPlayer!!.prepare()
            sfxPlayer!!.start()
        } catch (e: Exception) {
            Log.w("MatchVM", "SFX not found: $path — ${e.message}")
        }
    }

    override fun onCleared() {
        super.onCleared()
        cancelHints()
        voicePlayer?.stop(); voicePlayer?.release(); voicePlayer = null
        sfxPlayer?.stop();   sfxPlayer?.release();   sfxPlayer   = null
    }
}