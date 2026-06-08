package com.babybloom.presentation.viewmodels

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babybloom.R
import com.babybloom.data.local.dao.LearningContentDao
import com.babybloom.di.AppSoundSettings
import com.babybloom.domain.model.ActivityContent
import com.babybloom.util.AssetPathResolver
import com.babybloom.util.ImageAsset
import com.babybloom.util.SoundEffect
import com.babybloom.util.touch.TouchPatternAnalyzer
import com.babybloom.util.touch.TouchScoringMode
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
    val showTargetNumberCard: Boolean             = false,

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
    private val learningContentDao: LearningContentDao,
    private val appSoundSettings: AppSoundSettings
) : ViewModel() {

    private companion object {
        const val MAX_ATTEMPTS             = 3
        // ── Timers ────────────────────────────────────────────────────────────
        // 10-second timeout per attempt / inactivity window.
        const val QUESTION_TIMEOUT_MS      = 10_000L
        // ── Hint ─────────────────────────────────────────────────────────────
        const val HINT_DELAY_MS            = 5_000L
        // ── Encoding ─────────────────────────────────────────────────────────
        const val ATTEMPT_ENCODE_FACTOR    = 100_000L
        // ── Delays ───────────────────────────────────────────────────────────
        const val WRONG_LETTER_FLASH_MS            = 600L
        const val WRONG_SHAPE_FLASH_MS             = 600L
        const val CAGE_SUCCESS_CONFIRM_MS          = 2_000L
        const val CAGE_FINAL_NUMBER_PAUSE_MS       = 500L
        const val CAGE_TARGET_CARD_BEAT_MS         = 300L
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
    private var onComplete: ((isCorrect: Boolean, elapsedMs: Long, touchQualityScore: Float) -> Unit)? = null

    // ── Media ─────────────────────────────────────────────────────────────────
    private var mediaPlayer : MediaPlayer? = null
    private var loopPlayer  : MediaPlayer? = null

    // ── Jobs ──────────────────────────────────────────────────────────────────
    private var cageTimerJob    : Job? = null
    private var cageValidationJob: Job? = null
    private var questionTimerJob: Job? = null
    private var hintJob         : Job? = null
    private var loadJob         : Job? = null
    private var loadGeneration  : Long = 0L
    private var completedGeneration: Long = -1L
    private var suppressNextCageTimerRestart = false

    // ── Touch analysis ────────────────────────────────────────────────────────
    private val touchAnalyzer = TouchPatternAnalyzer()
    private var colorScoringStarted = false
    private var colorMoveCount = 0
    private var colorInsideShapeMoveCount = 0

    // ─────────────────────────────────────────────────────────────────────────
    // Public API — touch tracking
    // ─────────────────────────────────────────────────────────────────────────

    fun onTouchPoint(offset: Offset) {
        touchAnalyzer.onPointerEvent(offset)
    }

    fun onTouchStart(offset: Offset? = null) {
        touchAnalyzer.onStrokeStart(offset)
    }

    fun onTouchEnd() {
        touchAnalyzer.onStrokeEnd()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Entry point
    // ─────────────────────────────────────────────────────────────────────────

    fun loadContent(
        currentItem: ActivityContent,
        isCalmMode : Boolean,
        isTest     : Boolean,
        onComplete : (isCorrect: Boolean, elapsedMs: Long, touchQualityScore: Float) -> Unit
    ) {
        loadGeneration++
        completedGeneration = -1L
        this.onComplete = onComplete
        loadJob?.cancel()
        cageTimerJob?.cancel()
        cageValidationJob?.cancel()
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
        loadGeneration++
        onComplete = null
        loadJob?.cancel()
        cageTimerJob?.cancel()
        cageValidationJob?.cancel()
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
        resetColorShapeAdherence()
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
            showTargetNumberCard = false,
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
     * Inactivity timer for ANIMALS_TO_CAGE.
     * The child only fails after [QUESTION_TIMEOUT_MS] of no engagement.
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
        questionTimerJob?.cancel()
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
        onTouchEnd()
        val current = _state.value
        if (current.isPenDragging) {
            _state.value = current.copy(isPenDragging = false)
        }
        if (!current.isAnswered && current.dragType == DragType.COLOR_TO_SHAPE) {
            startQuestionTimer()
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

    fun onPenColoringMotion(
        delta: Float,
        resolvedColorId: String,
        isInsideShape: Boolean,
        isInColoringZone: Boolean
    ) {
        val current = _state.value
        if (current.isAnswered || current.fillProgress >= 1f) return

        if (isInColoringZone) colorScoringStarted = true
        if (!colorScoringStarted) return

        colorMoveCount++
        if (isInsideShape) colorInsideShapeMoveCount++

        if (isInColoringZone) {
            onPenMovedOverShape(delta, resolvedColorId)
        }
    }

    private fun submitColor(colorId: String) {
        val current = _state.value
        if (current.isAnswered) return
        val isCorrect        = colorId == current.correctId
        val successAudioPath = if (isCorrect)
            current.colorOptions.find { it.colorId == colorId }?.audioPath
        else null
        if (isCorrect) {
            _state.value = current.copy(
                activeColorId = colorId,
                fillProgress = 1f
            )
        }
        handleAnswer(isCorrect, successAudioPath)
        if (!isCorrect && current.isTest) scheduleColorHint(current.correctId)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // User events — LETTER_TO_WORD
    // ─────────────────────────────────────────────────────────────────────────

    fun onLetterTileDragStarted(letterId: String) {
        onTouchStart()
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
        onTouchEnd()
        releaseLoopPlayer()
    }

    fun onLetterDroppedToSlot(
        droppedLetterId: String,
        dropPx: Offset? = null,
        slotCenterPx: Offset? = null,
        dropRadiusPx: Float? = null
    ) {
        val current = _state.value
        if (current.isAnswered) return

        releaseLoopPlayer()

        val isCorrect = droppedLetterId == current.correctId
        if (isCorrect) {
            _state.value = current.copy(droppedLetterId = droppedLetterId)
            val correctOption = current.letterOptions.find { it.letterId == droppedLetterId }
            handleAnswer(
                isCorrect = true,
                successAudioPath = correctOption?.animalAudioPath?.takeIf { it.isNotEmpty() },
                releasePoint = dropPx,
                targetCenter = slotCenterPx,
                snapRadiusPx = dropRadiusPx
            )
            return
        } else {
            _state.value = current.copy(wrongDropLetterId = droppedLetterId)
            viewModelScope.launch {
                delay(WRONG_LETTER_FLASH_MS)
                _state.value = _state.value.copy(wrongDropLetterId = null)
            }
            if (current.isTest) scheduleLetterHint(current.correctId)
        }
        handleAnswer(
            isCorrect = false,
            releasePoint = dropPx,
            targetCenter = slotCenterPx,
            snapRadiusPx = dropRadiusPx
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // User events — ANIMALS_TO_CAGE
    // ─────────────────────────────────────────────────────────────────────────

    fun onCageAnimalDragStarted(poolIdx: Int) {
        onTouchStart()
        cageTimerJob?.cancel()
        // Audio is played on successful cage drop only.
    }

    fun onCageAnimalDragReleased() {
        onTouchEnd()
        if (suppressNextCageTimerRestart) {
            suppressNextCageTimerRestart = false
            return
        }
        val current = _state.value
        if (!current.isAnswered) startCageTimer()
    }

    private fun countingSoundPath(count: Int): String =
        AssetPathResolver.audioPathFor("number_$count", CATEGORY_NUMBER)

    private fun playCountingSound(count: Int) = playSound(countingSoundPath(count))

    fun onAnimalDroppedToCage(
        poolIdx: Int,
        dropPx: Offset? = null,
        cageCenterPx: Offset? = null,
        dropRadiusPx: Float? = null
    ) {
        val current  = _state.value
        if (current.isAnswered || poolIdx in current.inCageSet) return
        val poolItem = current.cagePool.getOrNull(poolIdx) ?: return

        if (poolItem.isCorrect) {
            cageValidationJob?.cancel()
            val newSet = current.inCageSet + poolIdx
            _state.value = current.copy(
                inCageSet            = newSet,
                sessionTotalAttempts = current.sessionTotalAttempts + 1
            )
            val updated = _state.value
            when {
                newSet.size < current.targetCount -> {
                    playCountingSound(newSet.size)
                }

                newSet.size == current.targetCount -> {
                    suppressNextCageTimerRestart = true
                    if (current.isTest) {
                        cageTimerJob?.cancel()
                        playCountingSound(newSet.size)
                        scheduleCageSuccessValidation(
                            dropPx = dropPx,
                            cageCenterPx = cageCenterPx,
                            dropRadiusPx = dropRadiusPx
                        )
                    } else {
                        playLearningCageCompletionSequence(
                            current = updated,
                            dropPx = dropPx,
                            cageCenterPx = cageCenterPx,
                            dropRadiusPx = dropRadiusPx
                        )
                    }
                }

                else -> {
                    handleCageOverfill(
                        dropPx = dropPx,
                        cageCenterPx = cageCenterPx,
                        dropRadiusPx = dropRadiusPx
                    )
                }
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
    fun onShapeDroppedToOutline(
        droppedShapeId: String,
        slotShapeId: String,
        dropPx: Offset? = null,
        slotCenterPx: Offset? = null,
        dropRadiusPx: Float? = null
    ) {
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
            handleAnswer(
                isCorrect = true,
                successAudioPath = shapeAudio,
                releasePoint = dropPx,
                targetCenter = slotCenterPx,
                snapRadiusPx = dropRadiusPx
            )
        } else {
            // Wrong drop — flash and count as an attempt
            _state.value = current.copy(wrongDropSlotId = slotShapeId)
            viewModelScope.launch {
                delay(WRONG_SHAPE_FLASH_MS)
                _state.value = _state.value.copy(wrongDropSlotId = null)
            }
            if (current.isTest) scheduleShapeHint(current.correctId)
            handleAnswer(
                isCorrect = false,
                releasePoint = dropPx,
                targetCenter = slotCenterPx,
                snapRadiusPx = dropRadiusPx
            )
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
        cageValidationJob?.cancel()

        val elapsedMs       = System.currentTimeMillis() - current.startTimeMs
        val newUsed         = current.attemptsUsed + 1
        val newLeft         = (current.attemptsLeft - 1).coerceAtLeast(0)
        val touchAnalysis = analyzeDragTouch(current, newUsed)

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
            viewModelScope.launch {
                dispatchCompleteOnce(
                    false,
                    encoded,
                    touchAnalysis.touchQualityScore,
                    loadGeneration
                )
            }
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

    private fun scheduleCageSuccessValidation(
        dropPx: Offset? = null,
        cageCenterPx: Offset? = null,
        dropRadiusPx: Float? = null
    ) {
        val generation = loadGeneration
        val contentId = _state.value.contentId
        cageValidationJob?.cancel()
        cageValidationJob = viewModelScope.launch {
            delay(CAGE_SUCCESS_CONFIRM_MS)

            val current = _state.value
            if (
                generation != loadGeneration ||
                current.contentId != contentId ||
                current.isAnswered ||
                current.inCageSet.size != current.targetCount
            ) return@launch

            finalizeCageSuccess(
                current = current,
                dropPx = dropPx,
                cageCenterPx = cageCenterPx,
                dropRadiusPx = dropRadiusPx
            )
        }
    }

    private fun playLearningCageCompletionSequence(
        current: DragGameState,
        dropPx: Offset? = null,
        cageCenterPx: Offset? = null,
        dropRadiusPx: Float? = null
    ) {
        val generation = loadGeneration
        val contentId = current.contentId
        cageValidationJob?.cancel()
        playSequence(listOf(countingSoundPath(current.targetCount))) {
            cageValidationJob = viewModelScope.launch {
                delay(CAGE_FINAL_NUMBER_PAUSE_MS)

                val latest = _state.value
                if (
                    generation != loadGeneration ||
                    latest.contentId != contentId ||
                    latest.isAnswered ||
                    latest.inCageSet.size != latest.targetCount
                ) return@launch

                finalizeCageSuccess(
                    current = latest,
                    dropPx = dropPx,
                    cageCenterPx = cageCenterPx,
                    dropRadiusPx = dropRadiusPx,
                    successAudioPaths = listOf(numberAnnouncementSoundPath(latest)),
                    showTargetNumberCard = true
                )
            }
        }
    }

    private fun finalizeCageSuccess(
        current: DragGameState,
        dropPx: Offset? = null,
        cageCenterPx: Offset? = null,
        dropRadiusPx: Float? = null,
        successAudioPaths: List<String> = listOf(AssetPathResolver.soundEffectPath(SoundEffect.CORRECT)),
        showTargetNumberCard: Boolean = false
    ) {
        cageTimerJob?.cancel()
        cageValidationJob?.cancel()

        val elapsedMs       = System.currentTimeMillis() - current.startTimeMs
        val finalAttempts   = current.attemptsUsed + 1
        val encoded         = elapsedMs + finalAttempts.toLong() * ATTEMPT_ENCODE_FACTOR
        val touchAnalysis = analyzeDragTouch(
            current = current,
            attempts = finalAttempts,
            releasePoint = dropPx,
            targetCenter = cageCenterPx,
            snapRadiusPx = dropRadiusPx
        )

        val contentId = current.contentId
        _state.value = current.copy(
            isAnswered          = true,
            isCorrect           = true,
            attemptsUsed        = finalAttempts,
            showCelebration     = false,
            showTargetNumberCard = showTargetNumberCard,
            sessionCorrectCount = current.sessionCorrectCount + 1
        )
        playSequence(successAudioPaths) {
            viewModelScope.launch {
                if (showTargetNumberCard) delay(CAGE_TARGET_CARD_BEAT_MS)
                _state.value = _state.value.copy(showTargetNumberCard = false)
                showCelebrationThenComplete(
                    contentId,
                    true,
                    encoded,
                    touchAnalysis.touchQualityScore,
                    loadGeneration
                )
            }
        }
        advanceSession(elapsedMs)
    }

    private fun numberAnnouncementSoundPath(current: DragGameState): String =
        AssetPathResolver.audioPathFor(current.correctId, CATEGORY_NUMBER)

    private fun handleCageOverfill(
        dropPx: Offset? = null,
        cageCenterPx: Offset? = null,
        dropRadiusPx: Float? = null
    ) {
        val current = _state.value
        if (current.isAnswered) return

        cageTimerJob?.cancel()
        cageValidationJob?.cancel()

        val elapsedMs       = System.currentTimeMillis() - current.startTimeMs
        val newUsed         = current.attemptsUsed + 1
        val newLeft         = (current.attemptsLeft - 1).coerceAtLeast(0)
        val touchAnalysis = analyzeDragTouch(
            current = current,
            attempts = newUsed,
            releasePoint = dropPx,
            targetCenter = cageCenterPx,
            snapRadiusPx = dropRadiusPx
        )

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
                sessionTotalAttempts = current.sessionTotalAttempts + 1
            )
            viewModelScope.launch {
                dispatchCompleteOnce(
                    false,
                    encoded,
                    touchAnalysis.touchQualityScore,
                    loadGeneration
                )
            }
            advanceSession(elapsedMs)
        } else {
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

    private fun analyzeDragTouch(
        current: DragGameState,
        attempts: Int,
        releasePoint: Offset? = null,
        targetCenter: Offset? = null,
        snapRadiusPx: Float? = null
    ) = when (current.dragType) {
        DragType.COLOR_TO_SHAPE -> touchAnalyzer.analyze(
            attempts = attempts,
            progress = current.fillProgress,
            pathAdherence = colorShapeAdherenceScore(),
            mode = TouchScoringMode.COVERAGE_STROKES
        )

        DragType.ANIMALS_TO_CAGE -> touchAnalyzer.analyze(
            attempts = attempts,
            releasePoint = releasePoint,
            targetCenter = targetCenter,
            snapRadiusPx = snapRadiusPx,
            expectedStrokeCount = current.targetCount,
            mode = TouchScoringMode.PRECISION_RELEASE
        )

        DragType.SHAPE_TO_OUTLINE -> touchAnalyzer.analyze(
            attempts = attempts,
            releasePoint = releasePoint,
            targetCenter = targetCenter,
            snapRadiusPx = snapRadiusPx,
            expectedStrokeCount = current.outlineSlots.size.takeIf { it > 1 },
            mode = TouchScoringMode.PRECISION_RELEASE
        )

        DragType.LETTER_TO_WORD -> touchAnalyzer.analyze(
            attempts = attempts,
            releasePoint = releasePoint,
            targetCenter = targetCenter,
            snapRadiusPx = snapRadiusPx,
            mode = TouchScoringMode.PRECISION_RELEASE
        )
    }

    private fun resetColorShapeAdherence() {
        colorScoringStarted = false
        colorMoveCount = 0
        colorInsideShapeMoveCount = 0
    }

    private fun colorShapeAdherenceScore(): Float {
        if (colorMoveCount == 0) return 1f
        val insideRate = colorInsideShapeMoveCount.toFloat() / colorMoveCount.toFloat()
        return ((insideRate - 0.55f) / 0.45f).coerceIn(0f, 1f)
    }

    private fun handleAnswer(
        isCorrect: Boolean,
        successAudioPath: String? = null,
        releasePoint: Offset? = null,
        targetCenter: Offset? = null,
        snapRadiusPx: Float? = null
    ) {
        val current = _state.value
        if (current.isAnswered) return

        val elapsedMs       = System.currentTimeMillis() - current.startTimeMs
        val newUsed         = current.attemptsUsed + 1
        val newLeft         = (current.attemptsLeft - 1).coerceAtLeast(0)
        val touchAnalysis = analyzeDragTouch(
            current = current,
            attempts = newUsed,
            releasePoint = releasePoint,
            targetCenter = targetCenter,
            snapRadiusPx = snapRadiusPx
        )

        if (isCorrect) {
            // ── Correct ───────────────────────────────────────────────────────
            questionTimerJob?.cancel()
            hintJob?.cancel()

            val contentId = current.contentId
            _state.value = current.copy(
                isAnswered           = true,
                isCorrect            = true,
                attemptsUsed         = newUsed,
                attemptsLeft         = newLeft,
                showCelebration      = false,
                hintColorId          = null,
                hintLetterId         = null,
                hintShapeId          = null,
                sessionCorrectCount  = current.sessionCorrectCount + 1,
                sessionTotalAttempts = current.sessionTotalAttempts + newUsed
            )
            val encoded = elapsedMs + (newUsed.toLong() * ATTEMPT_ENCODE_FACTOR)
            playSequence(
                listOfNotNull(
                    AssetPathResolver.soundEffectPath(SoundEffect.CORRECT),
                    successAudioPath
                )
            ) {
                showCelebrationThenComplete(
                    contentId,
                    true,
                    encoded,
                    touchAnalysis.touchQualityScore,
                    loadGeneration
                )
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
            viewModelScope.launch {
                dispatchCompleteOnce(
                    false,
                    encoded,
                    touchAnalysis.touchQualityScore,
                    loadGeneration
                )
            }
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

    private fun showCelebrationThenComplete(
        contentId: String?,
        isCorrect: Boolean,
        encoded: Long,
        touchQualityScore: Float,
        generation: Long
    ) {
        viewModelScope.launch {
            if (generation != loadGeneration || _state.value.contentId != contentId) return@launch
            _state.value = _state.value.copy(showCelebration = true)
            delay(CELEBRATION_DURATION_MS)
            if (generation != loadGeneration || _state.value.contentId != contentId) return@launch
            _state.value = _state.value.copy(showCelebration = false)
            dispatchCompleteOnce(isCorrect, encoded, touchQualityScore, generation)
        }
    }

    private fun dispatchCompleteOnce(
        isCorrect: Boolean,
        encoded: Long,
        touchQualityScore: Float,
        generation: Long
    ) {
        if (generation != loadGeneration || completedGeneration == generation) return
        completedGeneration = generation
        onComplete?.invoke(isCorrect, encoded, touchQualityScore)
    }

    private fun playSequence(paths: List<String>, index: Int = 0, onComplete: () -> Unit = {}) {
        if (index >= paths.size) {
            onComplete()
            return
        }
        val path = paths[index]
        if (isDisabledSoundEffectPath(path)) {
            playSequence(paths, index + 1, onComplete)
            return
        }
        try {
            releasePlayer()
            mediaPlayer = MediaPlayer()
            val afd = context.assets.openFd(path)
            mediaPlayer?.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            mediaPlayer?.setOnCompletionListener { playSequence(paths, index + 1, onComplete) }
            mediaPlayer?.setOnErrorListener { _, _, _ ->
                Log.w("DragGameVM", "Error playing: $path")
                playSequence(paths, index + 1, onComplete)
                true
            }
            mediaPlayer?.prepare()
            mediaPlayer?.start()
        } catch (e: Exception) {
            Log.w("DragGameVM", "Audio not found: $path — ${e.message}")
            playSequence(paths, index + 1, onComplete)
        }
    }

    fun playSound(path: String) = playSequence(listOf(path))

    private fun playSoundOnce(path: String) = playSound(path)

    private fun startLoopSound(path: String) {
        releaseLoopPlayer()
        if (isDisabledSoundEffectPath(path)) return
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

    private fun isDisabledSoundEffectPath(path: String): Boolean {
        val fileName = path.substringAfterLast('/')
        return fileName in SoundEffect.fileNames && !appSoundSettings.soundEnabled.value
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
