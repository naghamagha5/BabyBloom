package com.babybloom.presentation.viewmodels

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babybloom.R
import com.babybloom.data.local.dao.LearningContentDao
import com.babybloom.domain.model.ActivityContent
import com.babybloom.util.AssetPathResolver
import com.babybloom.util.ImageAsset
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
enum class DragType { COLOR_TO_SHAPE, LETTER_TO_WORD, ANIMALS_TO_CAGE }

// ── Color option ──────────────────────────────────────────────────────────────
data class ColorOption(
    val colorId      : String,
    val labelAr      : String,
    val audioPath    : String,
    val drawableImage: ImageAsset
)

// ── Letter option ─────────────────────────────────────────────────────────────
data class LetterOption(
    val letterId       : String,
    val labelAr        : String,
    val drawableImage  : ImageAsset,
    val audioPath      : String,
    val animalAudioPath: String,
    val isCorrect      : Boolean
)

// ── Animal option ─────────────────────────────────────────────────────────────
data class DragAnimalOption(
    val animalId : String,
    val assetPath: String,
    val audioPath: String,
    val isCorrect: Boolean
)

// ── Scatter position ──────────────────────────────────────────────────────────
data class ScatterPosition(val xFraction: Float, val yFraction: Float)

// ── Unified state ─────────────────────────────────────────────────────────────
data class DragGameState(
    val dragType  : DragType = DragType.COLOR_TO_SHAPE,
    val isLoading : Boolean  = true,

    // Shared
    val correctId   : String = "",
    val currentLabel: String = "",
    val resetTrigger: Int    = 0,

    // 3 attempts per question
    val attemptsLeft: Int = 3,
    val attemptsUsed: Int = 0,

    // ── COLOR_TO_SHAPE ────────────────────────────────────────────────────────
    val colorOptions : List<ColorOption> = emptyList(),
    val fillProgress : Float             = 0f,
    val activeColorId: String?           = null,

    // ── LETTER_TO_WORD ────────────────────────────────────────────────────────
    val animalQuestionImage: String?            = null,
    val wordPuzzleText     : String             = "",
    val wordFullText       : String             = "",
    val letterOptions      : List<LetterOption> = emptyList(),
    val droppedLetterId    : String?            = null,
    val wrongDropLetterId  : String?            = null,

    // ── ANIMALS_TO_CAGE ───────────────────────────────────────────────────────
    val instructionText : String                  = "",
    val targetNumeral   : String                  = "",
    val targetCount     : Int                     = 0,
    val targetAnimalId  : String                  = "",
    val cagePool        : List<DragAnimalOption>  = emptyList(),
    val scatterPositions: List<ScatterPosition>   = emptyList(),
    val inCageSet       : Set<Int>                = emptySet(),
    val rejectIdx       : Int                     = -1,

    // ── ANIMALS_TO_CAGE timer ─────────────────────────────────────────────────
    val cageTimerSeconds     : Int     = 10,
    val cageTimerTotalSeconds: Int     = 10,
    val cageTimerRunning     : Boolean = false,
    val showHint             : Boolean = false,

    // Shared result
    val isAnswered     : Boolean = false,
    val isCorrect      : Boolean = false,
    // ── Unified celebration popup ─────────────────────────────────────────────
    // Set true on a correct answer so the Screen can show GoodJobPopup.
    // Cleared before onComplete fires so the popup doesn't persist into the
    // next content item.
    val showCelebration: Boolean = false,

    // Session / round tracking
    val currentRound         : Int  = 0,
    val totalRounds          : Int  = 0,
    val questionsInRound     : Int  = 0,
    val questionInRound      : Int  = 0,
    val sessionCorrectCount  : Int  = 0,
    val sessionTotalAnswered : Int  = 0,
    val sessionWrongAttempts : Int  = 0,
    val sessionTotalAttempts : Int  = 0,
    val sessionElapsedMs     : Long = 0L,
    val sessionCompleteSignal: Int  = 0,

    val startTimeMs: Long = 0L
)

@HiltViewModel
class DragGameViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val learningContentDao: LearningContentDao
) : ViewModel() {

    private companion object {
        const val SOUND_TAP     = "learning_content/audio/tap.ogg"
        const val SOUND_CORRECT = "learning_content/audio/correct.ogg"
        const val SOUND_WRONG   = "learning_content/audio/wrong.ogg"

        const val MAX_ATTEMPTS            = 3
        const val CAGE_TIMER_SECONDS      = 15
        const val HINT_THRESHOLD          = 5
        const val ATTEMPT_ENCODE_FACTOR   = 100_000L
        const val WRONG_LETTER_FLASH_MS   = 600L
        // How long the GoodJobPopup is visible before onComplete fires
        const val CELEBRATION_DURATION_MS = 2_200L
        const val SESSION_ADVANCE_DELAY_MS= 1_400L
        const val CAGE_POOL_SIZE          = 3

        const val CATEGORY_COLOR       = "COLOR"
        const val CATEGORY_LETTER_NAME = "LETTER_NAME"
        const val CATEGORY_ANIMAL      = "ANIMAL"
        const val CATEGORY_NUMBER      = "NUMBER"

        const val MOOD_CALM   = "calm"
        const val MOOD_ACTIVE = "active"
    }

    private val _state = MutableStateFlow(DragGameState())
    val state: StateFlow<DragGameState> = _state.asStateFlow()

    private var onComplete: ((isCorrect: Boolean, elapsedMs: Long) -> Unit)? = null
    private var mediaPlayer: MediaPlayer? = null
    private var cageTimerJob: Job? = null

    // ─────────────────────────────────────────────────────────────────────────
    // Entry point
    // ─────────────────────────────────────────────────────────────────────────
    fun loadContent(
        currentItem: ActivityContent,
        isCalmMode : Boolean,
        onComplete : (isCorrect: Boolean, elapsedMs: Long) -> Unit
    ) {
        this.onComplete = onComplete
        cageTimerJob?.cancel()
        releasePlayer()
        playSound(SOUND_TAP)
        viewModelScope.launch {
            when (parseDragType(currentItem)) {
                DragType.COLOR_TO_SHAPE  -> loadColorToShape(currentItem, isCalmMode)
                DragType.LETTER_TO_WORD  -> loadLetterToWord(currentItem, isCalmMode)
                DragType.ANIMALS_TO_CAGE -> loadAnimalsToCage(currentItem, isCalmMode)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Loaders
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun loadColorToShape(item: ActivityContent, isCalmMode: Boolean) {
        val allColors     = learningContentDao.getByCategory(CATEGORY_COLOR)
        val correctEntity = allColors.find { it.id == item.contentId }
            ?: run { Log.e("DragGameVM", "color entity not found for ${item.contentId}"); return }
        val distractors  = allColors.filter { it.id != item.contentId }.shuffled().take(3)
        val colorOptions = (listOf(correctEntity) + distractors).shuffled().map { entity ->
            ColorOption(
                colorId       = entity.id,
                labelAr       = entity.labelAr,
                audioPath     = AssetPathResolver.audioPathFor(entity.id, CATEGORY_COLOR),
                drawableImage = AssetPathResolver.imageAssetFor(entity.id, CATEGORY_COLOR, isCalmMode)
            )
        }
        _state.value = _state.value.copy(
            dragType        = DragType.COLOR_TO_SHAPE,
            isLoading       = false,
            correctId       = item.contentId,
            currentLabel    = item.labelAr,
            colorOptions    = colorOptions,
            fillProgress    = 0f,
            activeColorId   = null,
            attemptsLeft    = MAX_ATTEMPTS,
            attemptsUsed    = 0,
            isAnswered      = false,
            isCorrect       = false,
            showCelebration = false,
            startTimeMs     = System.currentTimeMillis(),
            resetTrigger    = _state.value.resetTrigger + 1
        )
    }

    private suspend fun loadLetterToWord(item: ActivityContent, isCalmMode: Boolean) {
        val animalEntity     = learningContentDao.getByLearningOrderAndCategory(
            item.learningOrder, CATEGORY_ANIMAL
        )
        val mood             = if (isCalmMode) MOOD_CALM else MOOD_ACTIVE
        val animalImageAsset = animalEntity?.let {
            "learning_content/visual/$mood/${it.id}.png"
        }
        val fullWord   = animalEntity?.labelAr ?: item.labelAr
        val puzzleText = if (fullWord.isNotEmpty()) fullWord.substring(1) else ""

        val allLetters    = learningContentDao.getByCategory(CATEGORY_LETTER_NAME)
        val correctEntity = allLetters.find { it.id == item.contentId }
            ?: run { Log.e("DragGameVM", "letter entity not found for ${item.contentId}"); return }
        val distractors   = allLetters.filter { it.id != item.contentId }.shuffled().take(2)

        val letterOptions = (listOf(correctEntity) + distractors).shuffled().map { entity ->
            val pairedAnimalId = learningContentDao.getByLearningOrderAndCategory(
                entity.learningOrder, CATEGORY_ANIMAL
            )?.id ?: ""
            LetterOption(
                letterId        = entity.id,
                labelAr         = entity.labelAr,
                drawableImage   = AssetPathResolver.imageAssetFor(entity.id, CATEGORY_LETTER_NAME, isCalmMode),
                audioPath       = AssetPathResolver.audioPathFor(entity.id, CATEGORY_LETTER_NAME),
                animalAudioPath = if (pairedAnimalId.isNotEmpty())
                    AssetPathResolver.audioPathFor(pairedAnimalId, CATEGORY_ANIMAL)
                else "",
                isCorrect       = entity.id == item.contentId
            )
        }

        _state.value = _state.value.copy(
            dragType            = DragType.LETTER_TO_WORD,
            isLoading           = false,
            correctId           = item.contentId,
            currentLabel        = item.labelAr,
            animalQuestionImage = animalImageAsset,
            wordPuzzleText      = puzzleText,
            wordFullText        = fullWord,
            letterOptions       = letterOptions,
            droppedLetterId     = null,
            wrongDropLetterId   = null,
            attemptsLeft        = MAX_ATTEMPTS,
            attemptsUsed        = 0,
            isAnswered          = false,
            isCorrect           = false,
            showCelebration     = false,
            startTimeMs         = System.currentTimeMillis(),
            resetTrigger        = _state.value.resetTrigger + 1
        )
    }

    private suspend fun loadAnimalsToCage(item: ActivityContent, isCalmMode: Boolean) {
        val targetCount = item.learningOrder.coerceIn(1, CAGE_POOL_SIZE)
        val mood        = if (isCalmMode) MOOD_CALM else MOOD_ACTIVE

        val oneAnimal = learningContentDao.getByCategory(CATEGORY_ANIMAL).shuffled().firstOrNull()
            ?: run { Log.e("DragGameVM", "no animals in DB"); return }

        val pool = List(CAGE_POOL_SIZE) {
            DragAnimalOption(
                animalId  = oneAnimal.id,
                assetPath = AssetPathResolver.imageAssetFor(oneAnimal.id, CATEGORY_ANIMAL, isCalmMode)
                    .let { asset ->
                        when (asset) {
                            is ImageAsset.PngAsset -> asset.path
                            else -> "learning_content/visual/$mood/${oneAnimal.id}.png"
                        }
                    },
                audioPath = AssetPathResolver.audioPathFor(oneAnimal.id, CATEGORY_ANIMAL),
                isCorrect = true
            )
        }

        val numberEntity = learningContentDao.getByLearningOrderAndCategory(
            item.learningOrder, CATEGORY_NUMBER
        )
        val numeral     = numberEntity?.labelAr ?: item.learningOrder.toString()
        val animalName  = context.getString(R.string.drag_animal_article_prefix) + oneAnimal.labelAr
        val instruction = context.getString(R.string.drag_cage_instruction, numeral, animalName)

        _state.value = _state.value.copy(
            dragType              = DragType.ANIMALS_TO_CAGE,
            isLoading             = false,
            correctId             = item.contentId,
            currentLabel          = item.labelAr,
            instructionText       = instruction,
            targetNumeral         = numeral,
            targetCount           = targetCount,
            targetAnimalId        = oneAnimal.id,
            cagePool              = pool,
            scatterPositions      = listOf(
                ScatterPosition(0.5f, 0.1f),
                ScatterPosition(0.18f, 0.55f),
                ScatterPosition(0.82f, 0.55f)
            ),
            inCageSet             = emptySet(),
            rejectIdx             = -1,
            attemptsLeft          = MAX_ATTEMPTS,
            attemptsUsed          = 0,
            isAnswered            = false,
            isCorrect             = false,
            showCelebration       = false,
            cageTimerSeconds      = CAGE_TIMER_SECONDS,
            cageTimerTotalSeconds = CAGE_TIMER_SECONDS,
            cageTimerRunning      = false,
            showHint              = false,
            startTimeMs           = System.currentTimeMillis(),
            resetTrigger          = _state.value.resetTrigger + 1
        )
        startCageTimer()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // User events — COLOR_TO_SHAPE
    // ─────────────────────────────────────────────────────────────────────────

    fun onColorPickedUp(colorId: String) {
        if (_state.value.isAnswered) return
        _state.value = _state.value.copy(activeColorId = colorId)
        _state.value.colorOptions.find { it.colorId == colorId }
            ?.audioPath?.let { playSound(it) }
    }

    fun onPenMovedOverShape(colorId: String, delta: Float) {
        val current = _state.value
        if (current.isAnswered || current.fillProgress >= 1f) return
        val newProgress = (current.fillProgress + delta / 3000f).coerceAtMost(1f)
        _state.value = current.copy(activeColorId = colorId, fillProgress = newProgress)
        if (newProgress >= 1f) submitColor(colorId)
    }

    private fun submitColor(colorId: String) {
        val current = _state.value
        if (current.isAnswered) return
        handleAnswer(isCorrect = colorId == current.correctId)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // User events — LETTER_TO_WORD
    // ─────────────────────────────────────────────────────────────────────────

    fun onLetterTileDragStarted(letterId: String) {
        val option = _state.value.letterOptions.find { it.letterId == letterId } ?: return
        val paths  = listOfNotNull(
            option.audioPath.takeIf { it.isNotEmpty() },
            option.animalAudioPath.takeIf { it.isNotEmpty() }
        )
        playSequence(paths)
    }

    fun onLetterDroppedToSlot(droppedLetterId: String) {
        val current   = _state.value
        if (current.isAnswered) return
        val isCorrect = droppedLetterId == current.correctId
        if (isCorrect) {
            _state.value = current.copy(droppedLetterId = droppedLetterId)
        } else {
            _state.value = current.copy(wrongDropLetterId = droppedLetterId)
            viewModelScope.launch {
                delay(WRONG_LETTER_FLASH_MS)
                _state.value = _state.value.copy(wrongDropLetterId = null)
            }
        }
        handleAnswer(isCorrect)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // User events — ANIMALS_TO_CAGE
    // ─────────────────────────────────────────────────────────────────────────

    fun onCageAnimalDragStarted(poolIdx: Int) {
        _state.value.cagePool.getOrNull(poolIdx)
            ?.audioPath?.let { playSound(it) }
    }

    fun onAnimalDroppedToCage(poolIdx: Int) {
        val current  = _state.value
        if (current.isAnswered || poolIdx in current.inCageSet) return
        val poolItem = current.cagePool.getOrNull(poolIdx) ?: return

        if (poolItem.isCorrect) {
            val newSet = current.inCageSet + poolIdx
            _state.value = current.copy(
                inCageSet            = newSet,
                sessionTotalAttempts = current.sessionTotalAttempts + 1
            )
            if (newSet.size == current.targetCount) {
                cageTimerJob?.cancel()
                val elapsedMs     = System.currentTimeMillis() - current.startTimeMs
                val finalAttempts = current.attemptsUsed + 1
                val encoded       = elapsedMs + finalAttempts.toLong() * ATTEMPT_ENCODE_FACTOR

                playSound(SOUND_CORRECT)
                _state.value = _state.value.copy(
                    isAnswered          = true,
                    isCorrect           = true,
                    attemptsUsed        = finalAttempts,
                    cageTimerRunning    = false,
                    showHint            = false,
                    showCelebration     = true,          // ← show GoodJobPopup
                    sessionCorrectCount = current.sessionCorrectCount + 1
                )
                viewModelScope.launch {
                    delay(CELEBRATION_DURATION_MS)
                    _state.value = _state.value.copy(showCelebration = false)
                    onComplete?.invoke(true, encoded)
                }
                advanceSession(elapsedMs)
            }
        }
    }

    fun onRejectAnimationDone(poolIdx: Int) {
        if (_state.value.rejectIdx == poolIdx)
            _state.value = _state.value.copy(rejectIdx = -1)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ANIMALS_TO_CAGE — timer logic
    // ─────────────────────────────────────────────────────────────────────────

    private fun startCageTimer() {
        cageTimerJob?.cancel()
        cageTimerJob = viewModelScope.launch {
            var remaining = CAGE_TIMER_SECONDS
            while (remaining > 0 && !_state.value.isAnswered) {
                _state.value = _state.value.copy(
                    cageTimerSeconds = remaining,
                    cageTimerRunning = true,
                    showHint         = remaining <= HINT_THRESHOLD
                )
                delay(1_000)
                remaining--
            }
            if (!_state.value.isAnswered) {
                _state.value = _state.value.copy(cageTimerSeconds = 0, cageTimerRunning = false)
                handleCageTimeout()
            }
        }
    }

    private fun handleCageTimeout() {
        val current = _state.value
        if (current.isAnswered) return

        val elapsedMs = System.currentTimeMillis() - current.startTimeMs
        val newUsed   = current.attemptsUsed + 1
        val newLeft   = (current.attemptsLeft - 1).coerceAtLeast(0)

        playSound(SOUND_WRONG)

        if (newLeft == 0) {
            val encoded = elapsedMs + newUsed.toLong() * ATTEMPT_ENCODE_FACTOR
            _state.value = current.copy(
                isAnswered           = true,
                isCorrect            = false,
                attemptsUsed         = newUsed,
                attemptsLeft         = 0,
                cageTimerRunning     = false,
                cageTimerSeconds     = 0,
                showHint             = false,
                showCelebration      = false,
                sessionWrongAttempts = current.sessionWrongAttempts + 1,
                sessionTotalAttempts = current.sessionTotalAttempts + newUsed
            )
            viewModelScope.launch { onComplete?.invoke(false, encoded) }
            advanceSession(elapsedMs)
        } else {
            _state.value = current.copy(
                attemptsUsed          = newUsed,
                attemptsLeft          = newLeft,
                inCageSet             = emptySet(),
                cageTimerSeconds      = CAGE_TIMER_SECONDS,
                cageTimerTotalSeconds = CAGE_TIMER_SECONDS,
                cageTimerRunning      = false,
                showHint              = false,
                showCelebration       = false,
                sessionWrongAttempts  = current.sessionWrongAttempts + 1,
                sessionTotalAttempts  = current.sessionTotalAttempts + 1
            )
            startCageTimer()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3-attempt logic (COLOR_TO_SHAPE and LETTER_TO_WORD)
    // ─────────────────────────────────────────────────────────────────────────
    private fun handleAnswer(isCorrect: Boolean) {
        val current = _state.value
        if (current.isAnswered) return

        val elapsedMs = System.currentTimeMillis() - current.startTimeMs
        val newUsed   = current.attemptsUsed + 1
        val newLeft   = (current.attemptsLeft - 1).coerceAtLeast(0)

        if (isCorrect) {
            playSound(SOUND_CORRECT)
            _state.value = current.copy(
                isAnswered           = true,
                isCorrect            = true,
                attemptsUsed         = newUsed,
                attemptsLeft         = newLeft,
                showCelebration      = true,             // ← show GoodJobPopup
                sessionCorrectCount  = current.sessionCorrectCount + 1,
                sessionTotalAttempts = current.sessionTotalAttempts + newUsed
            )
            val encoded = elapsedMs + (newUsed.toLong() * ATTEMPT_ENCODE_FACTOR)
            viewModelScope.launch {
                delay(CELEBRATION_DURATION_MS)
                _state.value = _state.value.copy(showCelebration = false)
                onComplete?.invoke(true, encoded)
            }
            advanceSession(elapsedMs)

        } else if (newLeft == 0) {
            playSound(SOUND_WRONG)
            _state.value = current.copy(
                isAnswered           = true,
                isCorrect            = false,
                attemptsUsed         = newUsed,
                attemptsLeft         = 0,
                showCelebration      = false,
                sessionWrongAttempts = current.sessionWrongAttempts + 1,
                sessionTotalAttempts = current.sessionTotalAttempts + 1,
                fillProgress         = 0f,
                activeColorId        = null,
                droppedLetterId      = null
            )
            val encoded = elapsedMs + (newUsed.toLong() * ATTEMPT_ENCODE_FACTOR)
            viewModelScope.launch { onComplete?.invoke(false, encoded) }
            advanceSession(elapsedMs)

        } else {
            playSound(SOUND_WRONG)
            _state.value = current.copy(
                isAnswered           = false,
                attemptsUsed         = newUsed,
                attemptsLeft         = newLeft,
                showCelebration      = false,
                sessionWrongAttempts = current.sessionWrongAttempts + 1,
                sessionTotalAttempts = current.sessionTotalAttempts + 1,
                fillProgress         = 0f,
                activeColorId        = null,
                droppedLetterId      = null
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Session
    // ─────────────────────────────────────────────────────────────────────────
    private fun advanceSession(elapsedMs: Long) {
        viewModelScope.launch {
            delay(SESSION_ADVANCE_DELAY_MS)
            val cur         = _state.value
            val newAnswered = cur.sessionTotalAnswered + 1
            val sessionDone = cur.totalRounds > 0 &&
                    cur.questionsInRound > 0 &&
                    newAnswered >= cur.totalRounds * cur.questionsInRound
            _state.value = cur.copy(
                sessionTotalAnswered  = newAnswered,
                sessionElapsedMs      = cur.sessionElapsedMs + elapsedMs,
                sessionCompleteSignal = if (sessionDone) cur.sessionCompleteSignal + 1
                else cur.sessionCompleteSignal
            )
        }
    }

    @Suppress("unused")
    fun configureSession(totalRounds: Int, questionsInRound: Int) {
        _state.value = _state.value.copy(
            totalRounds      = totalRounds,
            questionsInRound = questionsInRound,
            currentRound     = 1,
            questionInRound  = 1
        )
    }

    @Suppress("unused")
    fun advanceRoundIndicator() {
        val cur   = _state.value
        val nextQ = cur.questionInRound + 1
        _state.value = if (nextQ > cur.questionsInRound)
            cur.copy(currentRound = (cur.currentRound + 1).coerceAtMost(cur.totalRounds), questionInRound = 1)
        else
            cur.copy(questionInRound = nextQ)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun parseDragType(item: ActivityContent): DragType = when {
        item.category.contains(CATEGORY_COLOR,  ignoreCase = true) -> DragType.COLOR_TO_SHAPE
        item.category.contains("LETTER",        ignoreCase = true) -> DragType.LETTER_TO_WORD
        item.category.contains(CATEGORY_NUMBER, ignoreCase = true) -> DragType.ANIMALS_TO_CAGE
        else -> DragType.COLOR_TO_SHAPE
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Audio
    // ─────────────────────────────────────────────────────────────────────────

    private fun playSequence(paths: List<String>, index: Int = 0) {
        if (index >= paths.size) return
        val path = paths[index]
        try {
            releasePlayer()
            mediaPlayer = MediaPlayer()
            val afd = context.assets.openFd(path)
            mediaPlayer?.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            mediaPlayer?.setOnCompletionListener { playSequence(paths, index + 1) }
            mediaPlayer?.setOnErrorListener { _, _, _ ->
                Log.w("DragGameVM", "Error playing: $path")
                playSequence(paths, index + 1)
                true
            }
            mediaPlayer?.prepare()
            mediaPlayer?.start()
        } catch (e: Exception) {
            Log.w("DragGameVM", "Audio not found: $path — ${e.message}")
            playSequence(paths, index + 1)
        }
    }

    fun playSound(path: String) = playSequence(listOf(path))

    private fun releasePlayer() {
        try { mediaPlayer?.stop() } catch (_: Exception) {}
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun onCleared() {
        super.onCleared()
        releasePlayer()
        cageTimerJob?.cancel()
    }
}