package com.babybloom.presentation.viewmodels

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babybloom.R
import com.babybloom.data.local.dao.LearningContentDao
import com.babybloom.domain.model.ActivityContent
import com.babybloom.util.AssetPathResolver
import com.babybloom.util.ImageAsset
import com.babybloom.util.SoundEffect
import com.babybloom.util.touch.TouchPatternAnalyzer
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
enum class DragType { COLOR_TO_SHAPE, LETTER_TO_WORD, ANIMALS_TO_CAGE, SHAPE_TO_OUTLINE }

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
    val letterSoundPath: String,
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

// ── Shape option ──────────────────────────────────────────────────────────────
data class ShapeOption(
    val shapeId      : String,
    val labelAr      : String,
    val audioPath    : String,
    val drawableImage: ImageAsset,
    /** True when this shape is the correct answer for the current question. */
    val isCorrect    : Boolean
)

// ── Scatter position ──────────────────────────────────────────────────────────
data class ScatterPosition(val xFraction: Float, val yFraction: Float)

// ── Unified state ─────────────────────────────────────────────────────────────
data class DragGameState(
    val contentId   : String? = null,
    val dragType    : DragType = DragType.COLOR_TO_SHAPE,
    val isLoading   : Boolean  = true,
    val isTest      : Boolean  = false,

    // Shared
    val correctId   : String = "",
    val currentLabel: String = "",
    val resetTrigger: Int    = 0,

    // 3 attempts per question — dots now fill as attempts are used (empty → full)
    val attemptsLeft: Int = 3,
    val attemptsUsed: Int = 0,

    // ── COLOR_TO_SHAPE ────────────────────────────────────────────────────────
    val colorOptions     : List<ColorOption> = emptyList(),
    val colorShapeId     : String            = "",
    val fillProgress     : Float             = 0f,
    val activeColorId    : String?           = null,
    val isPenDragging    : Boolean           = false,
    val hintColorId      : String?           = null,

    // ── LETTER_TO_WORD ────────────────────────────────────────────────────────
    val animalQuestionImage: String?            = null,
    val wordPuzzleText     : String             = "",
    val wordFullText       : String             = "",
    val letterOptions      : List<LetterOption> = emptyList(),
    val droppedLetterId    : String?            = null,
    val wrongDropLetterId  : String?            = null,
    val hintLetterId       : String?            = null,

    // ── ANIMALS_TO_CAGE ───────────────────────────────────────────────────────
    val instructionText : String                  = "",
    val targetNumeral   : String                  = "",
    val targetCount     : Int                     = 0,
    val targetAnimalId  : String                  = "",
    val cagePool        : List<DragAnimalOption>  = emptyList(),
    val scatterPositions: List<ScatterPosition>   = emptyList(),
    val inCageSet       : Set<Int>                = emptySet(),
    val rejectIdx       : Int                     = -1,

    // ── SHAPE_TO_OUTLINE ──────────────────────────────────────────────────────
    // shapeOptions  – the draggable shape tiles (1 in learning, all in testing)
    // outlineSlots  – ordered list of shapeIds that have outline drop targets
    // droppedShapes – maps slotShapeId → dropped shapeId (null = empty slot)
    val shapeOptions    : List<ShapeOption>         = emptyList(),
    val outlineSlots    : List<String>               = emptyList(),
    val droppedShapes   : Map<String, String?>       = emptyMap(),
    val wrongDropSlotId : String?                    = null,
    val hintShapeId     : String?                    = null,

    // Shared result
    val isAnswered     : Boolean = false,
    val isCorrect      : Boolean = false,
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
        const val MAX_ATTEMPTS             = 3
        // ── Timers ────────────────────────────────────────────────────────────
        // 30-second timeout per attempt for ALL game types (no UI arc shown).
        const val QUESTION_TIMEOUT_MS      = 30_000L
        // ── Hint ─────────────────────────────────────────────────────────────
        const val HINT_DELAY_MS            = 5_000L
        // ── Encoding ─────────────────────────────────────────────────────────
        const val ATTEMPT_ENCODE_FACTOR    = 100_000L
        // ── Delays ───────────────────────────────────────────────────────────
        const val WRONG_LETTER_FLASH_MS            = 600L
        const val LETTER_WORD_RECOGNITION_DELAY_MS = 1_200L
        const val WRONG_SHAPE_FLASH_MS             = 600L
        const val CELEBRATION_DURATION_MS          = 2_200L
        const val SESSION_ADVANCE_DELAY_MS         = 1_400L
        // ── Category keys ────────────────────────────────────────────────────
        const val CATEGORY_COLOR        = "COLOR"
        const val CATEGORY_LETTER_NAME  = "LETTER_NAME"
        const val CATEGORY_LETTER_SOUND = "LETTER_SOUND"
        const val CATEGORY_ANIMAL       = "ANIMAL"
        const val CATEGORY_NUMBER       = "NUMBER"
        const val CATEGORY_SHAPE        = "SHAPE"
    }

    private val _state = MutableStateFlow(DragGameState())
    val state: StateFlow<DragGameState> = _state.asStateFlow()

    // ── Callback ──────────────────────────────────────────────────────────────
    private var onComplete: ((isCorrect: Boolean, elapsedMs: Long, touchComplexity: Float) -> Unit)? = null

    // ── Media ─────────────────────────────────────────────────────────────────
    private var mediaPlayer : MediaPlayer? = null
    private var loopPlayer  : MediaPlayer? = null

    // ── Jobs ──────────────────────────────────────────────────────────────────
    private var cageTimerJob    : Job? = null
    private var questionTimerJob: Job? = null
    private var hintJob         : Job? = null
    private var loadJob         : Job? = null

    // ── Touch analysis ────────────────────────────────────────────────────────
    private val touchAnalyzer = TouchPatternAnalyzer()

    // ─────────────────────────────────────────────────────────────────────────
    // Public API — touch tracking
    // ─────────────────────────────────────────────────────────────────────────

    fun onTouchPoint(offset: Offset) {
        touchAnalyzer.onPointerEvent(offset)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Entry point
    // ─────────────────────────────────────────────────────────────────────────

    fun loadContent(
        currentItem: ActivityContent,
        isCalmMode : Boolean,
        isTest     : Boolean,
        onComplete : (isCorrect: Boolean, elapsedMs: Long, touchComplexity: Float) -> Unit
    ) {
        this.onComplete = onComplete
        loadJob?.cancel()
        cageTimerJob?.cancel()
        questionTimerJob?.cancel()
        hintJob?.cancel()
        releasePlayer()
        releaseLoopPlayer()
        val previousResetTrigger = _state.value.resetTrigger
        _state.value = DragGameState(
            contentId = currentItem.contentId,
            isLoading = true,
            isTest = isTest,
            resetTrigger = previousResetTrigger + 1
        )
        playSound(AssetPathResolver.soundEffectPath(SoundEffect.TAP))
        loadJob = viewModelScope.launch {
            when (parseDragType(currentItem)) {
                DragType.COLOR_TO_SHAPE  -> loadColorToShape(currentItem, isCalmMode, isTest)
                DragType.LETTER_TO_WORD  -> loadLetterToWord(currentItem, isCalmMode, isTest)
                DragType.ANIMALS_TO_CAGE -> loadAnimalsToCage(currentItem, isCalmMode, isTest)
                DragType.SHAPE_TO_OUTLINE -> loadShapeToOutline(currentItem, isCalmMode, isTest)
            }
        }
    }

    fun stopContent(contentId: String) {
        if (_state.value.contentId != contentId) return
        loadJob?.cancel()
        cageTimerJob?.cancel()
        questionTimerJob?.cancel()
        hintJob?.cancel()
        releasePlayer()
        releaseLoopPlayer()
        _state.value = DragGameState(
            contentId = contentId,
            isLoading = true,
            resetTrigger = _state.value.resetTrigger + 1
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Loaders
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun loadColorToShape(
        item      : ActivityContent,
        isCalmMode: Boolean,
        isTest    : Boolean
    ) {
        val allColors     = learningContentDao.getByCategory(CATEGORY_COLOR)
        val shapeEntity   = learningContentDao.getByCategory(CATEGORY_SHAPE).shuffled().firstOrNull()
        val correctEntity = allColors.find { it.id == item.contentId }
            ?: run { Log.e("DragGameVM", "color entity not found for ${item.contentId}"); return }

        val colorOptions: List<ColorOption> = if (isTest) {
            val distractors = allColors.filter { it.id != item.contentId }.shuffled().take(3)
            (listOf(correctEntity) + distractors).shuffled().map { entity ->
                ColorOption(
                    colorId       = entity.id,
                    labelAr       = entity.labelAr,
                    audioPath     = AssetPathResolver.audioPathFor(entity.id, CATEGORY_COLOR),
                    drawableImage = AssetPathResolver.imageAssetFor(entity.id, CATEGORY_COLOR, isCalmMode)
                )
            }
        } else {
            listOf(
                ColorOption(
                    colorId       = correctEntity.id,
                    labelAr       = correctEntity.labelAr,
                    audioPath     = AssetPathResolver.audioPathFor(correctEntity.id, CATEGORY_COLOR),
                    drawableImage = AssetPathResolver.imageAssetFor(correctEntity.id, CATEGORY_COLOR, isCalmMode)
                )
            )
        }

        _state.value = _state.value.copy(
            contentId       = item.contentId,
            dragType        = DragType.COLOR_TO_SHAPE,
            isLoading       = false,
            isTest          = isTest,
            correctId       = item.contentId,
            currentLabel    = item.labelAr,
            colorOptions    = colorOptions,
            colorShapeId    = shapeEntity?.id.orEmpty(),
            fillProgress    = 0f,
            activeColorId   = null,
            isPenDragging   = false,
            hintColorId     = null,
            attemptsLeft    = MAX_ATTEMPTS,
            attemptsUsed    = 0,
            isAnswered      = false,
            isCorrect       = false,
            showCelebration = false,
            startTimeMs     = System.currentTimeMillis(),
            resetTrigger    = _state.value.resetTrigger + 1
        )

        touchAnalyzer.onSessionStart()
        playSound(AssetPathResolver.dragInstructionColorPath())
        startQuestionTimer()
        scheduleColorHint(item.contentId)
    }

    private suspend fun loadLetterToWord(
        item      : ActivityContent,
        isCalmMode: Boolean,
        isTest    : Boolean
    ) {
        val animalEntity = learningContentDao.getByLearningOrderAndCategory(
            item.learningOrder, CATEGORY_ANIMAL
        )
        val animalImageAsset = animalEntity?.let {
            AssetPathResolver.animalImagePathFor(it.id, isCalmMode)
        }

        val fullWord   = animalEntity?.labelAr ?: item.labelAr
        val puzzleText = dropFirstLetterCluster(fullWord)

        val allLetters    = learningContentDao.getByCategory(CATEGORY_LETTER_NAME)
        val correctEntity = allLetters.find { it.id == item.contentId }
            ?: run { Log.e("DragGameVM", "letter entity not found for ${item.contentId}"); return }

        val letterOptions: List<LetterOption> = if (isTest) {
            val distractors = allLetters.filter { it.id != item.contentId }.shuffled().take(2)
            (listOf(correctEntity) + distractors).shuffled().map { entity ->
                buildLetterOption(entity, item.contentId, isCalmMode)
            }
        } else {
            listOf(buildLetterOption(correctEntity, item.contentId, isCalmMode))
        }

        _state.value = _state.value.copy(
            contentId           = item.contentId,
            dragType            = DragType.LETTER_TO_WORD,
            isLoading           = false,
            isTest              = isTest,
            correctId           = item.contentId,
            currentLabel        = item.labelAr,
            animalQuestionImage = animalImageAsset,
            wordPuzzleText      = puzzleText,
            wordFullText        = fullWord,
            letterOptions       = letterOptions,
            droppedLetterId     = null,
            wrongDropLetterId   = null,
            hintLetterId        = null,
            attemptsLeft        = MAX_ATTEMPTS,
            attemptsUsed        = 0,
            isAnswered          = false,
            isCorrect           = false,
            showCelebration     = false,
            startTimeMs         = System.currentTimeMillis(),
            resetTrigger        = _state.value.resetTrigger + 1
        )

        touchAnalyzer.onSessionStart()
        playSound(AssetPathResolver.dragInstructionLetterPath())
        startQuestionTimer()
        scheduleLetterHint(item.contentId)
    }

    private suspend fun buildLetterOption(
        entity    : com.babybloom.data.local.entity.LearningContentEntity,
        correctId : String,
        isCalmMode: Boolean
    ): LetterOption {
        val pairedAnimalId = learningContentDao.getByLearningOrderAndCategory(
            entity.learningOrder, CATEGORY_ANIMAL
        )?.id ?: ""
        val letterSoundId = "${entity.id}_s"
        return LetterOption(
            letterId        = entity.id,
            labelAr         = entity.labelAr,
            drawableImage   = AssetPathResolver.imageAssetFor(entity.id, CATEGORY_LETTER_NAME, isCalmMode),
            audioPath       = AssetPathResolver.audioPathFor(entity.id, CATEGORY_LETTER_NAME),
            letterSoundPath = AssetPathResolver.audioPathFor(letterSoundId, CATEGORY_LETTER_SOUND),
            animalAudioPath = if (pairedAnimalId.isNotEmpty())
                AssetPathResolver.audioPathFor(pairedAnimalId, CATEGORY_ANIMAL) else "",
            isCorrect       = entity.id == correctId
        )
    }

    private suspend fun loadAnimalsToCage(
        item      : ActivityContent,
        isCalmMode: Boolean,
        isTest    : Boolean
    ) {
        val allNumbers   = learningContentDao.getByCategory(CATEGORY_NUMBER)
        val numberEntity = allNumbers.find { it.id == item.contentId }
        val targetCount  = (
                numberEntity?.learningOrder
                    ?: item.learningOrder.takeIf { it > 0 }
                    ?: item.contentId.substringAfterLast('_').toIntOrNull()
                    ?: 1
                ).coerceAtLeast(1)
        val maxPoolSize = allNumbers.maxOfOrNull { it.learningOrder }
            ?.coerceAtLeast(targetCount) ?: targetCount

        val oneAnimal = learningContentDao.getByCategory(CATEGORY_ANIMAL).shuffled().firstOrNull()
            ?: run { Log.e("DragGameVM", "no animals in DB"); return }

        fun animalOption() = DragAnimalOption(
            animalId  = oneAnimal.id,
            assetPath = (AssetPathResolver.imageAssetFor(oneAnimal.id, CATEGORY_ANIMAL, isCalmMode)
                    as? ImageAsset.PngAsset)?.path
                ?: AssetPathResolver.animalImagePathFor(oneAnimal.id, isCalmMode),
            audioPath = AssetPathResolver.audioPathFor(oneAnimal.id, CATEGORY_ANIMAL),
            isCorrect = true
        )

        val poolSize = if (isTest) {
            val extra = (1..2).random()
            (targetCount + extra).coerceAtMost(maxPoolSize)
        } else targetCount

        val pool        = List(poolSize) { animalOption() }
        val numeral     = numberEntity?.labelAr ?: targetCount.toString()
        val instruction = context.getString(R.string.drag_cage_instruction, numeral)

        _state.value = _state.value.copy(
            contentId        = item.contentId,
            dragType         = DragType.ANIMALS_TO_CAGE,
            isLoading        = false,
            isTest           = isTest,
            correctId        = item.contentId,
            currentLabel     = item.labelAr,
            instructionText  = instruction,
            targetNumeral    = numeral,
            targetCount      = targetCount,
            targetAnimalId   = oneAnimal.id,
            cagePool         = pool,
            scatterPositions = buildScatterPositions(poolSize),
            inCageSet        = emptySet(),
            rejectIdx        = -1,
            attemptsLeft     = MAX_ATTEMPTS,
            attemptsUsed     = 0,
            isAnswered       = false,
            isCorrect        = false,
            showCelebration  = false,
            startTimeMs      = System.currentTimeMillis(),
            resetTrigger     = _state.value.resetTrigger + 1
        )

        touchAnalyzer.onSessionStart()
        playSound(AssetPathResolver.dragInstructionNumberPath(numberEntity?.id ?: "number_$targetCount"))
        // Start the 30-second cage timer immediately after load
        startCageTimer()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SHAPE_TO_OUTLINE loader
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Learning layout: shows 1 draggable shape tile + 1 outline drop target.
     * Testing layout : shows all shapes (from this difficulty level) as tiles
     *                  and all their outlines as drop slots simultaneously.
     *
     * The content pool is loaded purely from the DB by [difficultyLevel] so it
     * always stays in sync with new shapes added to LearningContentSeeder.
     */
    private suspend fun loadShapeToOutline(
        item: ActivityContent,
        isCalmMode: Boolean,
        isTest: Boolean
    ) {
        val allShapes = learningContentDao.getByCategory(CATEGORY_SHAPE)
        val correctEntity = allShapes.find { it.id == item.contentId }
            ?: run { Log.e("DragGameVM", "shape entity not found"); return }

        fun buildShapeOption(entity: com.babybloom.data.local.entity.LearningContentEntity) =
            ShapeOption(
                shapeId = entity.id,
                labelAr = entity.labelAr,
                audioPath = AssetPathResolver.audioPathFor(entity.id, CATEGORY_SHAPE),
                drawableImage = AssetPathResolver.imageAssetFor(entity.id, CATEGORY_SHAPE, isCalmMode),
                isCorrect = entity.id == item.contentId
            )

        val (shapeOptions, outlineSlots) = if (isTest) {
            // 1 colored draggable tile (the correct shape)
            val tile = buildShapeOption(correctEntity)

            // 3 outline slots: 1 correct + 2 random distractors from any difficulty
            val distractors = allShapes
                .filter { it.id != item.contentId }
                .shuffled()
                .take(2)

            val allForSlots = (listOf(correctEntity) + distractors).shuffled()
            val slots = allForSlots.map { it.id }

            (listOf(tile) + distractors.map(::buildShapeOption)) to slots
        } else {
            // Learning: only 1 tile and 1 slot
            val tile = buildShapeOption(correctEntity)
            listOf(tile) to listOf(correctEntity.id)
        }

        _state.value = _state.value.copy(
            contentId = item.contentId,
            dragType = DragType.SHAPE_TO_OUTLINE,
            isLoading = false,
            isTest = isTest,
            correctId = item.contentId,
            currentLabel = correctEntity.labelAr,
            shapeOptions = shapeOptions,
            outlineSlots = outlineSlots,
            droppedShapes = outlineSlots.associateWith { null },
            wrongDropSlotId = null,
            hintShapeId = null,
            attemptsLeft = MAX_ATTEMPTS,
            attemptsUsed = 0,
            isAnswered = false,
            isCorrect = false,
            showCelebration = false,
            startTimeMs = System.currentTimeMillis(),
            resetTrigger = _state.value.resetTrigger + 1
        )

        touchAnalyzer.onSessionStart()
        playSound(AssetPathResolver.dragInstructionShapePath())
        startQuestionTimer()
        scheduleShapeHint(item.contentId)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildScatterPositions(poolSize: Int): List<ScatterPosition> {
        if (poolSize <= 0) return emptyList()
        val columns = kotlin.math.ceil(kotlin.math.sqrt(poolSize.toFloat())).toInt().coerceAtLeast(1)
        val rows    = kotlin.math.ceil(poolSize / columns.toFloat()).toInt().coerceAtLeast(1)
        return (0 until poolSize).map { index ->
            val row = index / columns
            val col = index % columns
            val x   = if (columns == 1) 0.5f else col / (columns - 1f)
            val y   = if (rows    == 1) 0.5f else row / (rows    - 1f)
            ScatterPosition(x, y)
        }.shuffled()
    }

    private fun dropFirstLetterCluster(text: String): String {
        if (text.isEmpty()) return ""
        var index = text.offsetByCodePoints(0, 1)
        while (index < text.length) {
            val type = Character.getType(text.codePointAt(index))
            if (type != Character.NON_SPACING_MARK.toInt() &&
                type != Character.COMBINING_SPACING_MARK.toInt()
            ) break
            index = text.offsetByCodePoints(index, 1)
        }
        return text.substring(index)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Timer helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 30-second question timeout for COLOR_TO_SHAPE, LETTER_TO_WORD, and
     * SHAPE_TO_OUTLINE. No UI timer is shown – purely logic-level.
     */
    private fun startQuestionTimer() {
        questionTimerJob?.cancel()
        questionTimerJob = viewModelScope.launch {
            delay(QUESTION_TIMEOUT_MS)
            val s = _state.value
            if (!s.isAnswered) {
                when (s.dragType) {
                    DragType.COLOR_TO_SHAPE,
                    DragType.LETTER_TO_WORD,
                    DragType.SHAPE_TO_OUTLINE -> handleAnswer(false)
                    DragType.ANIMALS_TO_CAGE  -> { /* cage has its own cageTimerJob */ }
                }
            }
        }
    }

    /**
     * 30-second countdown for ANIMALS_TO_CAGE.
     * Restarts after each failed attempt so the child always gets 30 s per try.
     */
    private fun startCageTimer() {
        cageTimerJob?.cancel()
        cageTimerJob = viewModelScope.launch {
            delay(QUESTION_TIMEOUT_MS)
            if (!_state.value.isAnswered) {
                handleCageTimeout()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // User events — COLOR_TO_SHAPE
    // ─────────────────────────────────────────────────────────────────────────

    fun onColorPickedUp(colorId: String) {
        if (_state.value.isAnswered) return
        _state.value = _state.value.copy(
            activeColorId = if (_state.value.isTest) {
                colorId.takeIf { it.isNotEmpty() }
            } else colorId,
            isPenDragging = true,
            hintColorId   = null
        )
    }

    fun onPenTouchedColor(colorId: String) {
        val current = _state.value
        if (current.isAnswered || !current.isPenDragging) return
        _state.value = current.copy(activeColorId = colorId, hintColorId = null)
    }

    fun onPenDragReleased() {
        if (_state.value.isPenDragging) {
            _state.value = _state.value.copy(isPenDragging = false)
        }
    }

    fun onPenMovedOverShape(delta: Float, resolvedColorId: String) {
        val current = _state.value
        if (current.isAnswered || current.fillProgress >= 1f) return
        val newProgress = (current.fillProgress + delta / 3000f).coerceAtMost(1f)
        _state.value = current.copy(
            activeColorId = resolvedColorId,
            fillProgress  = newProgress
        )
        if (newProgress >= 1f) submitColor(resolvedColorId)
    }

    private fun submitColor(colorId: String) {
        val current = _state.value
        if (current.isAnswered) return
        val isCorrect        = colorId == current.correctId
        val successAudioPath = if (isCorrect)
            current.colorOptions.find { it.colorId == colorId }?.audioPath
        else null
        handleAnswer(isCorrect, successAudioPath)
        if (!isCorrect && current.isTest) scheduleColorHint(current.correctId)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // User events — LETTER_TO_WORD
    // ─────────────────────────────────────────────────────────────────────────

    fun onLetterTileDragStarted(letterId: String) {
        val current = _state.value
        val option  = current.letterOptions.find { it.letterId == letterId } ?: return
        if (current.isTest) {
            if (option.isCorrect) playSoundOnce(option.letterSoundPath)
        } else {
            startLoopSound(option.letterSoundPath)
        }
        _state.value = current.copy(hintLetterId = null)
    }

    fun onLetterTileDragReleased() {
        releaseLoopPlayer()
    }

    fun onLetterDroppedToSlot(droppedLetterId: String) {
        val current = _state.value
        if (current.isAnswered) return

        releaseLoopPlayer()

        val isCorrect = droppedLetterId == current.correctId
        if (isCorrect) {
            _state.value = current.copy(droppedLetterId = droppedLetterId)
            val correctOption = current.letterOptions.find { it.letterId == droppedLetterId }
            handleAnswer(true, correctOption?.animalAudioPath?.takeIf { it.isNotEmpty() })
            return
        } else {
            _state.value = current.copy(wrongDropLetterId = droppedLetterId)
            viewModelScope.launch {
                delay(WRONG_LETTER_FLASH_MS)
                _state.value = _state.value.copy(wrongDropLetterId = null)
            }
            if (current.isTest) scheduleLetterHint(current.correctId)
        }
        handleAnswer(false)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // User events — ANIMALS_TO_CAGE
    // ─────────────────────────────────────────────────────────────────────────

    fun onCageAnimalDragStarted(poolIdx: Int) {
        // Audio is played on successful cage drop only.
    }

    private fun countingSoundPath(count: Int): String =
        AssetPathResolver.audioPathFor("number_$count", CATEGORY_NUMBER)

    private fun playCountingSound(count: Int) = playSound(countingSoundPath(count))

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

                val elapsedMs       = System.currentTimeMillis() - current.startTimeMs
                val finalAttempts   = current.attemptsUsed + 1
                val encoded         = elapsedMs + finalAttempts.toLong() * ATTEMPT_ENCODE_FACTOR
                val touchComplexity = touchAnalyzer.analyze().touchComplexity

                playSequence(listOf(
                    countingSoundPath(newSet.size),
                    AssetPathResolver.soundEffectPath(SoundEffect.CORRECT)
                ))
                _state.value = _state.value.copy(
                    isAnswered          = true,
                    isCorrect           = true,
                    attemptsUsed        = finalAttempts,
                    showCelebration     = true,
                    sessionCorrectCount = current.sessionCorrectCount + 1
                )
                viewModelScope.launch {
                    delay(CELEBRATION_DURATION_MS)
                    _state.value = _state.value.copy(showCelebration = false)
                    onComplete?.invoke(true, encoded, touchComplexity)
                }
                advanceSession(elapsedMs)
            } else {
                playCountingSound(newSet.size)
            }
        }
    }

    fun onRejectAnimationDone(poolIdx: Int) {
        if (_state.value.rejectIdx == poolIdx)
            _state.value = _state.value.copy(rejectIdx = -1)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // User events — SHAPE_TO_OUTLINE
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Called when the user drops [droppedShapeId] over the outline slot whose
     * target shape is [slotShapeId].
     */
    fun onShapeDroppedToOutline(droppedShapeId: String, slotShapeId: String) {
        val current = _state.value
        if (current.isAnswered) return

        val isMatch = droppedShapeId == slotShapeId

        if (isMatch) {
            val newDropped = current.droppedShapes.toMutableMap().apply {
                put(slotShapeId, droppedShapeId)
            }
            _state.value = current.copy(
                droppedShapes = newDropped,
                hintShapeId   = null
            )

            val shapeAudio = current.shapeOptions
                .find { it.shapeId == droppedShapeId }?.audioPath
            handleAnswer(true, shapeAudio)
        } else {
            // Wrong drop — flash and count as an attempt
            _state.value = current.copy(wrongDropSlotId = slotShapeId)
            viewModelScope.launch {
                delay(WRONG_SHAPE_FLASH_MS)
                _state.value = _state.value.copy(wrongDropSlotId = null)
            }
            if (current.isTest) scheduleShapeHint(current.correctId)
            handleAnswer(false)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Hint scheduling
    // ─────────────────────────────────────────────────────────────────────────

    private fun scheduleColorHint(correctId: String) {
        hintJob?.cancel()
        hintJob = viewModelScope.launch {
            delay(HINT_DELAY_MS)
            if (!_state.value.isAnswered) {
                _state.value = _state.value.copy(hintColorId = correctId)
            }
        }
    }

    private fun scheduleLetterHint(correctId: String) {
        hintJob?.cancel()
        hintJob = viewModelScope.launch {
            delay(HINT_DELAY_MS)
            if (!_state.value.isAnswered) {
                _state.value = _state.value.copy(hintLetterId = correctId)
            }
        }
    }

    private fun scheduleShapeHint(correctId: String) {
        hintJob?.cancel()
        hintJob = viewModelScope.launch {
            delay(HINT_DELAY_MS)
            if (!_state.value.isAnswered) {
                _state.value = _state.value.copy(hintShapeId = correctId)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cage timeout handler
    // ─────────────────────────────────────────────────────────────────────────

    private fun handleCageTimeout() {
        val current = _state.value
        if (current.isAnswered) return

        val elapsedMs       = System.currentTimeMillis() - current.startTimeMs
        val newUsed         = current.attemptsUsed + 1
        val newLeft         = (current.attemptsLeft - 1).coerceAtLeast(0)
        val touchComplexity = touchAnalyzer.analyze().touchComplexity

        playSound(AssetPathResolver.soundEffectPath(SoundEffect.WRONG))

        if (newLeft == 0) {
            val encoded = elapsedMs + newUsed.toLong() * ATTEMPT_ENCODE_FACTOR
            _state.value = current.copy(
                isAnswered           = true,
                isCorrect            = false,
                attemptsUsed         = newUsed,
                attemptsLeft         = 0,
                showCelebration      = false,
                sessionWrongAttempts = current.sessionWrongAttempts + 1,
                sessionTotalAttempts = current.sessionTotalAttempts + newUsed
            )
            viewModelScope.launch { onComplete?.invoke(false, encoded, touchComplexity) }
            advanceSession(elapsedMs)
        } else {
            // Still has attempts — reset the cage and restart the 30-second timer
            _state.value = current.copy(
                attemptsUsed         = newUsed,
                attemptsLeft         = newLeft,
                inCageSet            = emptySet(),
                showCelebration      = false,
                sessionWrongAttempts = current.sessionWrongAttempts + 1,
                sessionTotalAttempts = current.sessionTotalAttempts + 1
            )
            startCageTimer()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Core answer handler (COLOR_TO_SHAPE, LETTER_TO_WORD, SHAPE_TO_OUTLINE)
    // ─────────────────────────────────────────────────────────────────────────

    private fun handleAnswer(isCorrect: Boolean, successAudioPath: String? = null) {
        val current = _state.value
        if (current.isAnswered) return

        val elapsedMs       = System.currentTimeMillis() - current.startTimeMs
        val newUsed         = current.attemptsUsed + 1
        val newLeft         = (current.attemptsLeft - 1).coerceAtLeast(0)
        val touchComplexity = touchAnalyzer.analyze().touchComplexity

        if (isCorrect) {
            // ── Correct ───────────────────────────────────────────────────────
            questionTimerJob?.cancel()
            hintJob?.cancel()

            val delayCelebration = current.dragType == DragType.LETTER_TO_WORD
            playSequence(listOfNotNull(
                successAudioPath,
                AssetPathResolver.soundEffectPath(SoundEffect.CORRECT)
            ))
            _state.value = current.copy(
                isAnswered           = true,
                isCorrect            = true,
                attemptsUsed         = newUsed,
                attemptsLeft         = newLeft,
                showCelebration      = !delayCelebration,
                hintColorId          = null,
                hintLetterId         = null,
                hintShapeId          = null,
                sessionCorrectCount  = current.sessionCorrectCount + 1,
                sessionTotalAttempts = current.sessionTotalAttempts + newUsed
            )
            val encoded = elapsedMs + (newUsed.toLong() * ATTEMPT_ENCODE_FACTOR)
            viewModelScope.launch {
                if (delayCelebration) {
                    delay(LETTER_WORD_RECOGNITION_DELAY_MS)
                    _state.value = _state.value.copy(showCelebration = true)
                }
                delay(CELEBRATION_DURATION_MS)
                _state.value = _state.value.copy(showCelebration = false)
                onComplete?.invoke(true, encoded, touchComplexity)
            }
            advanceSession(elapsedMs)

        } else if (newLeft == 0) {
            // ── Wrong + no attempts left ──────────────────────────────────────
            questionTimerJob?.cancel()
            hintJob?.cancel()

            playSound(AssetPathResolver.soundEffectPath(SoundEffect.WRONG))
            _state.value = current.copy(
                isAnswered           = true,
                isCorrect            = false,
                attemptsUsed         = newUsed,
                attemptsLeft         = 0,
                showCelebration      = false,
                hintColorId          = null,
                hintLetterId         = null,
                hintShapeId          = null,
                sessionWrongAttempts = current.sessionWrongAttempts + 1,
                sessionTotalAttempts = current.sessionTotalAttempts + 1,
                fillProgress         = 0f,
                activeColorId        = null,
                isPenDragging        = false,
                droppedLetterId      = null
            )
            val encoded = elapsedMs + (newUsed.toLong() * ATTEMPT_ENCODE_FACTOR)
            viewModelScope.launch { onComplete?.invoke(false, encoded, touchComplexity) }
            advanceSession(elapsedMs)

        } else {
            // ── Wrong but still has attempts — restart timer ──────────────────
            playSound(AssetPathResolver.soundEffectPath(SoundEffect.WRONG))
            _state.value = current.copy(
                isAnswered           = false,
                attemptsUsed         = newUsed,
                attemptsLeft         = newLeft,
                showCelebration      = false,
                sessionWrongAttempts = current.sessionWrongAttempts + 1,
                sessionTotalAttempts = current.sessionTotalAttempts + 1,
                fillProgress         = 0f,
                activeColorId        = null,
                isPenDragging        = false,
                droppedLetterId      = null
            )
            startQuestionTimer()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Session management
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
                sessionCompleteSignal = if (sessionDone)
                    cur.sessionCompleteSignal + 1 else cur.sessionCompleteSignal
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
            cur.copy(
                currentRound    = (cur.currentRound + 1).coerceAtMost(cur.totalRounds),
                questionInRound = 1
            )
        else
            cur.copy(questionInRound = nextQ)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DragType parser
    // ─────────────────────────────────────────────────────────────────────────

    private fun parseDragType(item: ActivityContent): DragType = when {
        item.category.contains(CATEGORY_COLOR,   ignoreCase = true) -> DragType.COLOR_TO_SHAPE
        item.category.contains("LETTER",         ignoreCase = true) -> DragType.LETTER_TO_WORD
        item.category.contains(CATEGORY_NUMBER,  ignoreCase = true) -> DragType.ANIMALS_TO_CAGE
        item.category.contains(CATEGORY_SHAPE,   ignoreCase = true) -> DragType.SHAPE_TO_OUTLINE
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

    private fun playSoundOnce(path: String) = playSound(path)

    private fun startLoopSound(path: String) {
        releaseLoopPlayer()
        try {
            loopPlayer = MediaPlayer()
            val afd = context.assets.openFd(path)
            loopPlayer?.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            loopPlayer?.isLooping = true
            loopPlayer?.prepare()
            loopPlayer?.start()
        } catch (e: Exception) {
            Log.w("DragGameVM", "Loop audio not found: $path — ${e.message}")
        }
    }

    private fun releasePlayer() {
        try { mediaPlayer?.stop() } catch (_: Exception) {}
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun releaseLoopPlayer() {
        try { loopPlayer?.stop() } catch (_: Exception) {}
        loopPlayer?.release()
        loopPlayer = null
    }

    override fun onCleared() {
        super.onCleared()
        releasePlayer()
        releaseLoopPlayer()
        cageTimerJob?.cancel()
        questionTimerJob?.cancel()
        hintJob?.cancel()
    }
}
