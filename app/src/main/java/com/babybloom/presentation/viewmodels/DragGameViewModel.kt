package com.babybloom.presentation.viewmodels

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babybloom.R
import com.babybloom.data.local.dao.LearningContentDao
import com.babybloom.domain.model.ActivityContent
import com.babybloom.ui.theme.DragColorBlueHex
import com.babybloom.ui.theme.DragColorGreenHex
import com.babybloom.ui.theme.DragColorMidGrayHex
import com.babybloom.ui.theme.DragColorRedHex
import com.babybloom.ui.theme.DragColorYellowHex
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
    val hexColor     : Long,
    val labelAr      : String,
    val audioPath    : String,
    val drawableImage: ImageAsset
)

// ── Letter option ─────────────────────────────────────────────────────────────
data class LetterOption(
    val letterId      : String,
    val labelAr       : String,
    val drawableImage : ImageAsset,
    val audioPath     : String,
    val animalAudioPath: String,
    val isCorrect     : Boolean
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
    // Set briefly when a wrong letter is dropped so the Screen can show a
    // shake/red-flash animation, then cleared after 600 ms. Purely visual.
    val wrongDropLetterId  : String?            = null,

    // ── ANIMALS_TO_CAGE ───────────────────────────────────────────────────────
    val instructionText : String               = "",
    val targetNumeral   : String               = "",
    val targetCount     : Int                  = 0,
    val targetAnimalId  : String               = "",
    val cagePool        : List<DragAnimalOption>   = emptyList(),
    val scatterPositions: List<ScatterPosition> = emptyList(),
    val inCageSet       : Set<Int>             = emptySet(),
    val rejectIdx       : Int                  = -1,

    // ── ANIMALS_TO_CAGE timer ─────────────────────────────────────────────────
    val cageTimerSeconds     : Int     = 10,
    val cageTimerTotalSeconds: Int     = 10,
    val cageTimerRunning     : Boolean = false,
    val showHint             : Boolean = false,

    // Shared result
    val isAnswered: Boolean = false,
    val isCorrect : Boolean = false,

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

    // ── Constants ─────────────────────────────────────────────────────────────
    private companion object {
        // Audio asset paths
        const val SOUND_TAP     = "learning_content/audio/tap.ogg"
        const val SOUND_CORRECT = "learning_content/audio/correct.ogg"
        const val SOUND_WRONG   = "learning_content/audio/wrong.ogg"

        // Drag-game content id sets
        val DRAG_COLOR_IDS  = setOf("color_red", "color_blue", "color_yellow", "color_green")
        val DRAG_LETTER_IDS = setOf("letter_alef", "letter_ba", "letter_meem", "letter_ha")

        // Cage-game mechanics
        const val MAX_ATTEMPTS       = 3
        const val CAGE_TIMER_SECONDS = 15   // total seconds per attempt window
        const val HINT_THRESHOLD     = 5    // show hint wobble when remaining <= this

        // Attempt encoding: encoded = realElapsedMs + attemptsUsed * ATTEMPT_ENCODE_FACTOR
        const val ATTEMPT_ENCODE_FACTOR = 100_000L

        // Wrong-letter flash duration (ms)
        const val WRONG_LETTER_FLASH_MS = 600L

        // Delay before firing onComplete after correct answer (ms)
        const val CORRECT_ANSWER_DELAY_MS = 900L

        // Session advance delay after answer (ms)
        const val SESSION_ADVANCE_DELAY_MS = 1_400L

        // Arabic numeral content ids
        const val NUMBER_1_ID = "number_1"
        const val NUMBER_2_ID = "number_2"
        const val NUMBER_3_ID = "number_3"

        // Letter-to-word puzzle answer ids
        const val LETTER_ALEF = "letter_alef"
        const val LETTER_BA   = "letter_ba"
        const val LETTER_MEEM = "letter_meem"
        const val LETTER_HA   = "letter_ha"

        // Animal ids used in word puzzles
        const val ANIMAL_LION  = "animal_lion"
        const val ANIMAL_DUCK  = "animal_duck"
        const val ANIMAL_GOAT  = "animal_goat"
        const val ANIMAL_HORSE = "animal_horse"

        // Cage pool size (always 3 animals shown)
        const val CAGE_POOL_SIZE = 3

        // DB category strings
        const val CATEGORY_COLOR       = "COLOR"
        const val CATEGORY_LETTER_NAME = "LETTER_NAME"
        const val CATEGORY_ANIMAL      = "ANIMAL"
        const val CATEGORY_NUMBER      = "NUMBER"

        // Asset mood folder names
        const val MOOD_CALM   = "calm"
        const val MOOD_ACTIVE = "active"

        // Asset path templates
        const val VISUAL_ASSET_TEMPLATE = "learning_content/visual/%s/%s.png"
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
        val colorEntities = learningContentDao.getByCategory(CATEGORY_COLOR)
            .filter { it.id in DRAG_COLOR_IDS }

        val correctEntity = colorEntities.find { it.id == item.contentId }
        val distractors   = colorEntities.filter { it.id != item.contentId }.shuffled().take(3)
        val options       = (listOfNotNull(correctEntity) + distractors).shuffled()

        val colorOptions = options.map { entity ->
            ColorOption(
                colorId       = entity.id,
                hexColor      = colorHexFromContentId(entity.id),
                labelAr       = entity.labelAr,
                audioPath     = AssetPathResolver.audioPathFor(entity.id, CATEGORY_COLOR),
                drawableImage = AssetPathResolver.imageAssetFor(entity.id, CATEGORY_COLOR, isCalmMode)
            )
        }

        _state.value = _state.value.copy(
            dragType      = DragType.COLOR_TO_SHAPE,
            isLoading     = false,
            correctId     = item.contentId,
            currentLabel  = item.labelAr,
            colorOptions  = colorOptions,
            fillProgress  = 0f,
            activeColorId = null,
            attemptsLeft  = MAX_ATTEMPTS,
            attemptsUsed  = 0,
            isAnswered    = false,
            isCorrect     = false,
            startTimeMs   = System.currentTimeMillis(),
            resetTrigger  = _state.value.resetTrigger + 1
        )
    }

    private suspend fun loadLetterToWord(item: ActivityContent, isCalmMode: Boolean) {
        val puzzle = wordPuzzleFromContentId(item.contentId)

        val mood             = if (isCalmMode) MOOD_CALM else MOOD_ACTIVE
        val animalImageAsset = VISUAL_ASSET_TEMPLATE.format(mood, puzzle.correctAnimalId)

        val allLetterEntities = learningContentDao.getByCategory(CATEGORY_LETTER_NAME)
            .filter { it.id in DRAG_LETTER_IDS }

        val correctLetterEntity = allLetterEntities.find { it.id == item.contentId }
        val distractors         = allLetterEntities
            .filter { it.id != item.contentId }
            .shuffled()
            .take(2)

        val letterPool = (listOfNotNull(correctLetterEntity) + distractors).shuffled()

        val letterOptions = letterPool.map { entity ->
            val letterPuzzle = wordPuzzleFromContentId(entity.id)
            LetterOption(
                letterId        = entity.id,
                labelAr         = entity.labelAr,
                drawableImage   = AssetPathResolver.imageAssetFor(entity.id, CATEGORY_LETTER_NAME, isCalmMode),
                audioPath       = AssetPathResolver.audioPathFor(entity.id, CATEGORY_LETTER_NAME),
                animalAudioPath = AssetPathResolver.audioPathFor(letterPuzzle.correctAnimalId, CATEGORY_ANIMAL),
                isCorrect       = entity.id == item.contentId
            )
        }

        _state.value = _state.value.copy(
            dragType            = DragType.LETTER_TO_WORD,
            isLoading           = false,
            correctId           = item.contentId,
            currentLabel        = item.labelAr,
            animalQuestionImage = animalImageAsset,
            wordPuzzleText      = puzzle.puzzleText,
            wordFullText        = puzzle.fullWord,
            letterOptions       = letterOptions,
            droppedLetterId     = null,
            wrongDropLetterId   = null,
            attemptsLeft        = MAX_ATTEMPTS,
            attemptsUsed        = 0,
            isAnswered          = false,
            isCorrect           = false,
            startTimeMs         = System.currentTimeMillis(),
            resetTrigger        = _state.value.resetTrigger + 1
        )
    }

    private suspend fun loadAnimalsToCage(item: ActivityContent, isCalmMode: Boolean) {
        val targetCount = item.learningOrder.coerceIn(1, CAGE_POOL_SIZE)
        val mood        = if (isCalmMode) MOOD_CALM else MOOD_ACTIVE

        val allAnimals = learningContentDao.getByCategory(CATEGORY_ANIMAL).shuffled()
        if (allAnimals.isEmpty()) return

        val targetAnimalEntity = allAnimals.first()
        val targetAnimalId     = targetAnimalEntity.id

        val pool = List(CAGE_POOL_SIZE) {
            DragAnimalOption(
                animalId  = targetAnimalId,
                assetPath = VISUAL_ASSET_TEMPLATE.format(mood, targetAnimalId),
                audioPath = AssetPathResolver.audioPathFor(targetAnimalId, CATEGORY_ANIMAL),
                isCorrect = true
            )
        }

        // Scatter positions: 3 animals laid out horizontally.
        // xFraction is set to 0.5 for the first (top-center) animal so its center
        // aligns with the cage center; the other two flank it below (handled in UI).
        val scatterPositions = listOf(
            ScatterPosition(0.5f,  0.1f),   // top-center — X=0.5 aligns with cage center
            ScatterPosition(0.18f, 0.55f),  // bottom-left
            ScatterPosition(0.82f, 0.55f)   // bottom-right
        )

        val numeral     = arabicNumeralFromContentId(item.contentId)
        val animalName  = context.getString(R.string.drag_animal_article_prefix) + targetAnimalEntity.labelAr
        val instruction = context.getString(R.string.drag_cage_instruction, numeral, animalName)

        _state.value = _state.value.copy(
            dragType              = DragType.ANIMALS_TO_CAGE,
            isLoading             = false,
            correctId             = item.contentId,
            currentLabel          = item.labelAr,
            instructionText       = instruction,
            targetNumeral         = numeral,
            targetCount           = targetCount,
            targetAnimalId        = targetAnimalId,
            cagePool              = pool,
            scatterPositions      = scatterPositions,
            inCageSet             = emptySet(),
            rejectIdx             = -1,
            attemptsLeft          = MAX_ATTEMPTS,
            attemptsUsed          = 0,
            isAnswered            = false,
            isCorrect             = false,
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
        playSequence(listOf(option.audioPath, option.animalAudioPath))
    }

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
                _state.value = _state.value.copy(
                    isAnswered       = true,
                    isCorrect        = true,
                    attemptsUsed     = finalAttempts,
                    cageTimerRunning = false,
                    showHint         = false,
                    sessionCorrectCount = current.sessionCorrectCount + 1
                )
                viewModelScope.launch {
                    playSound(SOUND_CORRECT)
                    delay(CORRECT_ANSWER_DELAY_MS)
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
                sessionWrongAttempts = current.sessionWrongAttempts + 1,
                sessionTotalAttempts = current.sessionTotalAttempts + newUsed
            )
            viewModelScope.launch { onComplete?.invoke(false, encoded) }
            advanceSession(elapsedMs)
        } else {
            // Wrong attempt — reset cage and restart timer for next attempt window
            _state.value = current.copy(
                attemptsUsed          = newUsed,
                attemptsLeft          = newLeft,
                inCageSet             = emptySet(),
                cageTimerSeconds      = CAGE_TIMER_SECONDS,
                cageTimerTotalSeconds = CAGE_TIMER_SECONDS,
                cageTimerRunning      = false,
                showHint              = false,
                sessionWrongAttempts  = current.sessionWrongAttempts + 1,
                sessionTotalAttempts  = current.sessionTotalAttempts + 1
            )
            startCageTimer()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3-attempt logic
    //
    // onComplete is called ONLY when the question is truly over:
    //   • Correct answer           → onComplete(true,  encoded)
    //   • All 3 attempts exhausted → onComplete(false, encoded)
    //
    // Attempts are encoded into elapsedMs:
    //   encoded  = realElapsedMs + (attemptsUsed * ATTEMPT_ENCODE_FACTOR)
    //   attempts = (encoded / ATTEMPT_ENCODE_FACTOR).toInt()  ← Shell decodes
    // ─────────────────────────────────────────────────────────────────────────
    private fun handleAnswer(isCorrect: Boolean) {
        val current = _state.value
        if (current.isAnswered) return

        val elapsedMs = System.currentTimeMillis() - current.startTimeMs
        val newUsed   = current.attemptsUsed + 1
        val newLeft   = (current.attemptsLeft - 1).coerceAtLeast(0)

        if (isCorrect) {
            // ✅ FINAL — correct answer
            playSound(SOUND_CORRECT)
            _state.value = current.copy(
                isAnswered           = true,
                isCorrect            = true,
                attemptsUsed         = newUsed,
                attemptsLeft         = newLeft,
                sessionCorrectCount  = current.sessionCorrectCount + 1,
                sessionTotalAttempts = current.sessionTotalAttempts + newUsed
            )
            val encoded = elapsedMs + (newUsed.toLong() * ATTEMPT_ENCODE_FACTOR)
            viewModelScope.launch {
                delay(CORRECT_ANSWER_DELAY_MS)
                onComplete?.invoke(true, encoded)
            }
            advanceSession(elapsedMs)

        } else if (newLeft == 0) {
            // ❌ FINAL — all attempts exhausted
            playSound(SOUND_WRONG)
            _state.value = current.copy(
                isAnswered           = true,
                isCorrect            = false,
                attemptsUsed         = newUsed,
                attemptsLeft         = 0,
                sessionWrongAttempts = current.sessionWrongAttempts + 1,
                sessionTotalAttempts = current.sessionTotalAttempts + 1,
                fillProgress         = 0f,
                activeColorId        = null,
                droppedLetterId      = null
            )
            val encoded = elapsedMs + (newUsed.toLong() * ATTEMPT_ENCODE_FACTOR)
            viewModelScope.launch {
                onComplete?.invoke(false, encoded)
            }
            advanceSession(elapsedMs)

        } else {
            // 🔄 NOT FINAL — wrong but retries remain
            playSound(SOUND_WRONG)
            _state.value = current.copy(
                isAnswered           = false,
                attemptsUsed         = newUsed,
                attemptsLeft         = newLeft,
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
    // Lookup tables
    // ─────────────────────────────────────────────────────────────────────────

    private fun parseDragType(item: ActivityContent): DragType = when {
        item.category.contains(CATEGORY_COLOR,  ignoreCase = true) -> DragType.COLOR_TO_SHAPE
        item.category.contains("LETTER",        ignoreCase = true) -> DragType.LETTER_TO_WORD
        item.category.contains(CATEGORY_NUMBER, ignoreCase = true) -> DragType.ANIMALS_TO_CAGE
        else -> DragType.COLOR_TO_SHAPE
    }

    private fun colorHexFromContentId(id: String): Long = when (id) {
        "color_red"    -> DragColorRedHex
        "color_blue"   -> DragColorBlueHex
        "color_yellow" -> DragColorYellowHex
        "color_green"  -> DragColorGreenHex
        else           -> DragColorMidGrayHex
    }

    private data class WordPuzzleData(
        val correctAnimalId: String,
        val puzzleText     : String,
        val fullWord       : String
    )

    private fun wordPuzzleFromContentId(letterId: String): WordPuzzleData = when (letterId) {
        LETTER_ALEF -> WordPuzzleData(ANIMAL_LION,  context.getString(R.string.drag_puzzle_alef_part), context.getString(R.string.drag_puzzle_alef_full))
        LETTER_BA   -> WordPuzzleData(ANIMAL_DUCK,  context.getString(R.string.drag_puzzle_ba_part),   context.getString(R.string.drag_puzzle_ba_full))
        LETTER_MEEM -> WordPuzzleData(ANIMAL_GOAT,  context.getString(R.string.drag_puzzle_meem_part), context.getString(R.string.drag_puzzle_meem_full))
        LETTER_HA   -> WordPuzzleData(ANIMAL_HORSE, context.getString(R.string.drag_puzzle_ha_part),   context.getString(R.string.drag_puzzle_ha_full))
        else        -> WordPuzzleData(ANIMAL_LION,  "",     letterId)
    }

    private fun arabicNumeralFromContentId(id: String): String = when (id) {
        NUMBER_1_ID -> context.getString(R.string.drag_numeral_1)
        NUMBER_2_ID -> context.getString(R.string.drag_numeral_2)
        NUMBER_3_ID -> context.getString(R.string.drag_numeral_3)
        else        -> id.removePrefix("number_")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Audio
    // ─────────────────────────────────────────────────────────────────────────

    fun playSound(path: String) {
        try {
            releasePlayer()
            mediaPlayer = MediaPlayer()
            val afd = context.assets.openFd(path)
            mediaPlayer?.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            mediaPlayer?.setOnErrorListener { _, _, _ ->
                Log.w("DragGameVM", "Error playing: $path"); true
            }
            mediaPlayer?.prepare()
            mediaPlayer?.start()
        } catch (e: Exception) {
            Log.w("DragGameVM", "Audio not found: $path — ${e.message}")
        }
    }

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