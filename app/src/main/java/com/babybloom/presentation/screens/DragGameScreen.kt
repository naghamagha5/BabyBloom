package com.babybloom.presentation.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.babybloom.R
import com.babybloom.domain.model.ActivityContent
import com.babybloom.ui.theme.*
import com.babybloom.presentation.viewmodels.DragAnimalOption
import com.babybloom.presentation.viewmodels.ColorOption
import com.babybloom.presentation.viewmodels.DragGameState
import com.babybloom.presentation.viewmodels.DragGameViewModel
import com.babybloom.presentation.viewmodels.DragType
import com.babybloom.presentation.viewmodels.LetterOption
import com.babybloom.presentation.viewmodels.ScatterPosition
import com.babybloom.util.ImageAsset
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// ── UI constants ──────────────────────────────────────────────────────────────
private val DROP_RADIUS_DP          = 90.dp
private val CAGE_DROP_RADIUS_FACTOR = 1.8f   // cage drop zone = DROP_RADIUS * factor

// Color-game
private val COLOR_GAME_SHAPE_SIZE   = 180.dp
private val COLOR_GAME_PEN_BOX_H    = 110.dp
private val COLOR_GAME_SWATCH_H     = 96.dp
private val COLOR_GAME_EDGE_PAD     = 4.dp
private val COLOR_GAME_FILL_DIVISOR = 3000f  // pen-stroke fill sensitivity
private val COLOR_GAME_INSTRUCTION_SIZE_SP = 18

// Letter-game
private val LETTER_ANIMAL_IMG_SIZE  = 130.dp
private val LETTER_TILE_SIZE        = 80.dp
private val LETTER_TILE_TOUCH_SIZE  = 88.dp
private val LETTER_SLOT_SIZE        = 72.dp
private val LETTER_PUZZLE_FONT_SP   = 52
private val LETTER_INSTRUCTION_SIZE_SP = 17
private val LETTER_FALLBACK_FONT_SP = 40

// Cage-game
private val CAGE_ANIMAL_SIZE        = 200.dp
private val CAGE_IMG_PADDING        = 4.dp
private val CAGE_INTERIOR_HORPAD_FRACTION = 0.22f  // horizontal padding as fraction of cage width
private val CAGE_INTERIOR_Y_OFFSET_FRACTION = 0.08f
private val CAGE_PROGRESS_DOT_SIZE  = 18.dp
private val CAGE_TIMER_ARC_SIZE     = 52.dp
private val CAGE_TIMER_STROKE_DP    = 5
private val CAGE_IN_CAGE_IMG_MAX    = 120.dp
private val CAGE_IN_CAGE_IMG_MIN    = 50.dp
private val CAGE_INSTRUCTION_SIZE_SP = 18
private val CAGE_TIMER_NUMBER_SIZE_SP = 22 // headlineSmall equivalent

// Attempt dots
private val ATTEMPT_DOT_SIZE        = 12.dp

// Drag scale
private const val DRAG_SCALE_ACTIVE  = 1.25f
private const val DRAG_SCALE_DEFAULT = 1f
private const val PEN_DRAG_SCALE     = 1.4f
private const val PEN_DRAG_SCALE_DEFAULT = 1f
private const val POOL_DRAG_SCALE    = 1.35f
private const val POOL_IN_CAGE_SCALE = 0.6f

// Hint wobble timing (keyframe values)
private const val HINT_WOBBLE_DURATION_MS   = 700
private const val HINT_WOBBLE_PAUSE_MS      = 300L
private const val REJECT_ANIM_DURATION_MS   = 350
private const val SHAKE_ANIM_DURATION_MS    = 500

// Timer animation
private const val TIMER_ARC_TWEEN_MS        = 900
private const val TIMER_LOW_FRACTION        = 0.3f
private const val TIMER_MID_FRACTION        = 0.5f

// ─────────────────────────────────────────────────────────────────────────────
// Entry point
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun DragGameScreen(
    currentItem: ActivityContent,
    isCalmMode : Boolean,
    onComplete : (isCorrect: Boolean, elapsedMs: Long) -> Unit,
    viewModel  : DragGameViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(currentItem.contentId) {
        viewModel.loadContent(currentItem, isCalmMode, onComplete)
    }

    val prevSignal = remember { mutableStateOf(0) }
    LaunchedEffect(state.sessionCompleteSignal) {
        if (state.sessionCompleteSignal > prevSignal.value) {
            prevSignal.value = state.sessionCompleteSignal
            onComplete(
                state.sessionCorrectCount > state.sessionWrongAttempts,
                state.sessionElapsedMs
            )
        }
    }

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        RoundIndicator(state)
        AttemptsRow(state)
        when (state.dragType) {
            DragType.COLOR_TO_SHAPE  -> ColorToPaintGame(state, isCalmMode, viewModel)
            DragType.LETTER_TO_WORD  -> LetterToWordGame(state, viewModel)
            DragType.ANIMALS_TO_CAGE -> AnimalsToCageGame(state, viewModel)
        }
    }
}

// ── Round indicator ───────────────────────────────────────────────────────────
@Composable
private fun RoundIndicator(state: DragGameState) {
    if (state.totalRounds == 0 || state.questionsInRound == 0) return
    Row(
        modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(
            text  = stringResource(R.string.drag_round_indicator, state.currentRound, state.totalRounds),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(state.questionsInRound) { i ->
                Box(
                    modifier = Modifier.size(10.dp).background(
                        color = when {
                            i < state.questionInRound - 1  -> MaterialTheme.colorScheme.primary
                            i == state.questionInRound - 1 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else                           -> DragProgressIdle
                        },
                        shape = CircleShape
                    )
                )
            }
        }
    }
}

// ── Attempts row — filled/empty dots showing remaining attempts ────────────────
@Composable
private fun AttemptsRow(state: DragGameState) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        repeat(3) { i ->
            val isFull = i < state.attemptsLeft
            Box(
                modifier = Modifier
                    .padding(start = 6.dp)
                    .size(ATTEMPT_DOT_SIZE)
                    .background(
                        color = if (isFull) DragAttemptDotFull else DragProgressIdle,
                        shape = CircleShape
                    )
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GAME 1 — COLOR_TO_SHAPE
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ColorToPaintGame(
    state     : DragGameState,
    isCalmMode: Boolean,
    viewModel : DragGameViewModel
) {
    val cardBorder = if (isCalmMode) DragCalmCardBorder else DragActiveCardBorder
    val cardColor  = if (isCalmMode) DragCalmCardBg else DragActiveCardBg
    val resetKey   = "${state.correctId}_${state.resetTrigger}"
    val strokeKey  = "${resetKey}_${state.attemptsUsed}"

    var shapeCenterPx   by remember { mutableStateOf(Offset.Zero) }
    var shapeHalfSizePx by remember { mutableStateOf(0f) }
    var boxTopLeftPx    by remember { mutableStateOf(Offset.Zero) }
    val penStrokePts    = remember(strokeKey) { mutableStateListOf<Offset>() }

    val shakeAnim = remember { Animatable(0f) }
    LaunchedEffect(state.attemptsUsed) {
        if (state.attemptsUsed > 0 && !state.isCorrect) {
            shakeAnim.snapTo(0f)
            shakeAnim.animateTo(0f, animationSpec = keyframes {
                durationMillis = SHAKE_ANIM_DURATION_MS
                -22f at 60; 22f at 120; -16f at 200; 16f at 280; -8f at 360; 0f at SHAKE_ANIM_DURATION_MS
            })
        }
    }

    val activeColor = state.activeColorId?.let { id ->
        state.colorOptions.find { it.colorId == id }?.hexColor?.let { Color(it) }
    }

    Column(
        modifier            = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text      = stringResource(R.string.drag_color_instruction),
            style     = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Bold, fontSize = COLOR_GAME_INSTRUCTION_SIZE_SP.sp
            ),
            textAlign = TextAlign.Center,
            modifier  = Modifier.padding(bottom = 8.dp)
        )

        val correctHex = state.colorOptions.find { it.colorId == state.correctId }?.hexColor
            ?: colorHexForDisplay(state.correctId)
        Text(
            text  = state.currentLabel,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold, color = Color(correctHex)
            ),
            textAlign = TextAlign.Center,
            modifier  = Modifier.padding(bottom = 12.dp)
        )

        Box(
            modifier = Modifier
                .size(COLOR_GAME_SHAPE_SIZE)
                .graphicsLayer { translationX = shakeAnim.value }
                .onGloballyPositioned { coords ->
                    val pos = coords.positionInRoot()
                    val sz  = coords.size
                    boxTopLeftPx    = pos
                    shapeCenterPx   = Offset(pos.x + sz.width / 2f, pos.y + sz.height / 2f)
                    shapeHalfSizePx = sz.width / 2f
                },
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val path = buildCirclePath(size)
                val base = when {
                    state.isCorrect                      -> activeColor ?: cardColor
                    state.isAnswered && !state.isCorrect -> DragShapeWrongBg
                    else                                 -> cardColor
                }
                drawPath(path = path, color = base)
                if (penStrokePts.size >= 2 && activeColor != null) {
                    clipPath(path) {
                        val strokePath = Path()
                        strokePath.moveTo(penStrokePts[0].x, penStrokePts[0].y)
                        penStrokePts.drop(1).forEach { strokePath.lineTo(it.x, it.y) }
                        drawPath(
                            path  = strokePath, color = activeColor,
                            style = Stroke(width = 40.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }
                drawPath(
                    path  = path,
                    color = if (state.isCorrect) DragResultCorrectText else cardBorder,
                    style = Stroke(width = 10f)
                )
            }
            when {
                state.isCorrect ->
                    Text(stringResource(R.string.drag_checkmark), fontSize = 60.sp, color = Color.White, fontWeight = FontWeight.Bold)
                state.isAnswered && !state.isCorrect ->
                    Text(stringResource(R.string.drag_crossmark), fontSize = 60.sp, color = DragResultWrongText, fontWeight = FontWeight.Bold)
                penStrokePts.isEmpty() ->
                    Text(stringResource(R.string.drag_color_shape_placeholder), fontSize = 60.sp, fontWeight = FontWeight.Bold, color = cardBorder)
            }
        }

        BoxWithConstraints(
            modifier         = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center
        ) {
            val shapeSize2 = COLOR_GAME_SHAPE_SIZE
            val shapeR2    = shapeSize2 / 2
            val penBoxH    = COLOR_GAME_PEN_BOX_H
            val swatchH    = COLOR_GAME_SWATCH_H
            val colH2      = penBoxH + 6.dp + swatchH
            val edgePad    = COLOR_GAME_EDGE_PAD
            val colVPad    = ((maxHeight / 2) - shapeR2 - edgePad - colH2).coerceAtLeast(0.dp)

            val cornerMods = listOf(
                Modifier.align(Alignment.TopStart)   .padding(start = edgePad, top    = colVPad),
                Modifier.align(Alignment.TopEnd)     .padding(end   = edgePad, top    = colVPad),
                Modifier.align(Alignment.BottomStart).padding(start = edgePad, bottom = colVPad),
                Modifier.align(Alignment.BottomEnd)  .padding(end   = edgePad, bottom = colVPad)
            )

            state.colorOptions.forEachIndexed { idx, option ->
                val cornerMod = cornerMods.getOrNull(idx) ?: return@forEachIndexed
                Column(
                    modifier            = cornerMod,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DraggablePen(
                        colorOption   = option,
                        penBoxDp      = penBoxH,
                        isAnswered    = state.isAnswered,
                        resetKey      = strokeKey,
                        onDragStarted = { viewModel.onColorPickedUp(option.colorId) },
                        onPenMove     = { penRootPx, dragDelta ->
                            if (!state.isAnswered && state.fillProgress < 1f &&
                                (penRootPx - shapeCenterPx).getDistance() < shapeHalfSizePx * 1.4f) {
                                penStrokePts.add(penRootPx - boxTopLeftPx)
                                viewModel.onPenMovedOverShape(option.colorId, dragDelta.getDistance())
                            }
                        }
                    )
                    DragImage(
                        asset    = option.drawableImage,
                        label    = option.labelAr,
                        modifier = Modifier
                            .size(swatchH)
                            .graphicsLayer { alpha = if (state.isAnswered) 0.4f else 1f }
                    )
                }
            }
        }
    }
}

// ── Draggable pen icon for color game ─────────────────────────────────────────
@Composable
private fun DraggablePen(
    colorOption   : ColorOption,
    penBoxDp      : Dp  = 68.dp,
    isAnswered    : Boolean,
    resetKey      : Any = Unit,
    onDragStarted : () -> Unit = {},
    onPenMove     : (penRootPx: Offset, dragDelta: Offset) -> Unit
) {
    var offsetX    by remember(resetKey) { mutableStateOf(0f) }
    var offsetY    by remember(resetKey) { mutableStateOf(0f) }
    var isDragging by remember(resetKey) { mutableStateOf(false) }
    var rootPos    by remember { mutableStateOf(Offset.Zero) }
    var sizePx     by remember { mutableStateOf(Offset.Zero) }

    val scale by animateFloatAsState(
        targetValue   = if (isDragging) PEN_DRAG_SCALE else PEN_DRAG_SCALE_DEFAULT,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label         = "penScale"
    )

    Box(
        modifier = Modifier
            .size(penBoxDp)
            .zIndex(if (isDragging) 10f else 1f)
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onGloballyPositioned { coords ->
                if (!isDragging) {
                    rootPos = coords.positionInRoot()
                    sizePx  = Offset(coords.size.width.toFloat(), coords.size.height.toFloat())
                }
            }
            .pointerInput(isAnswered, resetKey) {
                if (isAnswered) return@pointerInput
                detectDragGestures(
                    onDragStart  = { onDragStarted(); isDragging = true },
                    onDrag       = { change, drag ->
                        change.consume(); offsetX += drag.x; offsetY += drag.y
                        onPenMove(
                            Offset(rootPos.x + sizePx.x / 2f + offsetX, rootPos.y + sizePx.y / 2f + offsetY),
                            drag
                        )
                    },
                    onDragEnd    = { offsetX = 0f; offsetY = 0f; isDragging = false },
                    onDragCancel = { offsetX = 0f; offsetY = 0f; isDragging = false }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter            = painterResource(R.drawable.ic_pen),
            contentDescription = colorOption.labelAr,
            modifier           = Modifier
                .size(if (isDragging) penBoxDp * 0.95f else penBoxDp * 0.85f)
                .graphicsLayer {
                    rotationZ = -25f
                    alpha     = if (isAnswered) 0.4f else 1f
                }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GAME 2 — LETTER_TO_WORD
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun LetterToWordGame(
    state    : DragGameState,
    viewModel: DragGameViewModel
) {
    val density      = LocalDensity.current
    val dropRadiusPx = with(density) { DROP_RADIUS_DP.toPx() }
    val resetKey     = "${state.correctId}_${state.resetTrigger}_${state.attemptsUsed}"

    var gapCenterPx by remember { mutableStateOf(Offset.Zero) }

    val shakeAnim = remember { Animatable(0f) }
    LaunchedEffect(state.attemptsUsed) {
        if (state.attemptsUsed > 0 && !state.isCorrect) {
            shakeAnim.snapTo(0f)
            shakeAnim.animateTo(0f, animationSpec = keyframes {
                durationMillis = SHAKE_ANIM_DURATION_MS
                -22f at 60; 22f at 120; -16f at 200; 16f at 280; 0f at SHAKE_ANIM_DURATION_MS
            })
        }
    }

    Column(
        modifier            = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text      = stringResource(R.string.drag_letter_instruction),
            style     = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Bold, fontSize = LETTER_INSTRUCTION_SIZE_SP.sp
            ),
            textAlign = TextAlign.Center
        )

        Box(
            modifier = Modifier
                .size(LETTER_ANIMAL_IMG_SIZE)
                .shadow(4.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(DragLetterTileBg)
                .border(3.dp, DragActiveCardBorder, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            val animalPath = state.animalQuestionImage
            if (animalPath != null) {
                val ctx = LocalContext.current
                AsyncImage(
                    model              = ImageRequest.Builder(ctx)
                        .data("file:///android_asset/$animalPath")
                        .crossfade(true)
                        .build(),
                    contentDescription = state.currentLabel,
                    modifier           = Modifier.fillMaxSize().padding(8.dp),
                    contentScale       = ContentScale.Fit
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .border(3.dp, DragActiveCardBorder, RoundedCornerShape(20.dp))
                .background(DragActiveCardBg)
                .padding(vertical = 18.dp, horizontal = 16.dp)
                .graphicsLayer { translationX = shakeAnim.value },
            contentAlignment = Alignment.Center
        ) {
            if (state.isCorrect) {
                Text(
                    text  = state.wordFullText,
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize   = LETTER_PUZZLE_FONT_SP.sp,
                        color      = DragResultCorrectText
                    )
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    if (state.wordPuzzleText.isNotEmpty()) {
                        Text(
                            text  = state.wordPuzzleText,
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontWeight = FontWeight.Bold, fontSize = LETTER_PUZZLE_FONT_SP.sp
                            )
                        )
                    }
                    LetterGapSlot(
                        droppedLetter = state.droppedLetterId?.let { id ->
                            state.letterOptions.find { it.letterId == id }
                        },
                        isCorrect  = state.isCorrect,
                        isAnswered = state.isAnswered,
                        modifier   = Modifier.onGloballyPositioned { coords ->
                            val pos = coords.positionInRoot()
                            val sz  = coords.size
                            gapCenterPx = Offset(pos.x + sz.width / 2f, pos.y + sz.height / 2f)
                        }
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            state.letterOptions.forEach { letterOption ->
                Box(modifier = Modifier.size(LETTER_TILE_TOUCH_SIZE), contentAlignment = Alignment.Center) {
                    DraggableLetterTile(
                        option        = letterOption,
                        isAnswered    = state.isAnswered,
                        resetKey      = resetKey,
                        onDragStarted = { viewModel.onLetterTileDragStarted(letterOption.letterId) },
                        onDropped     = { letterId, dropPx ->
                            if ((dropPx - gapCenterPx).getDistance() < dropRadiusPx)
                                viewModel.onLetterDroppedToSlot(letterId)
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

// ── Letter gap slot ───────────────────────────────────────────────────────────
@Composable
private fun LetterGapSlot(
    droppedLetter: LetterOption?,
    isCorrect    : Boolean,
    isAnswered   : Boolean,
    modifier     : Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(LETTER_SLOT_SIZE)
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 3.dp,
                color = when {
                    isCorrect                -> DragResultCorrectText
                    isAnswered && !isCorrect -> DragResultWrongText
                    else                     -> DragActiveCardBorder
                },
                shape = RoundedCornerShape(12.dp)
            )
            .background(
                when {
                    isCorrect                -> DragLetterSlotCorrectBg
                    isAnswered && !isCorrect -> DragLetterSlotWrongBg
                    else                     -> Color.White.copy(alpha = 0.7f)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (droppedLetter != null) {
            DragImage(
                asset    = droppedLetter.drawableImage,
                label    = droppedLetter.labelAr,
                modifier = Modifier.fillMaxSize().padding(6.dp)
            )
        } else {
            Text(
                text  = stringResource(R.string.drag_letter_gap_placeholder),
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize   = LETTER_FALLBACK_FONT_SP.sp,
                    color      = DragActiveCardBorder
                )
            )
        }
    }
}

// ── Draggable letter tile ─────────────────────────────────────────────────────
@Composable
private fun DraggableLetterTile(
    option       : LetterOption,
    isAnswered   : Boolean,
    resetKey     : Any = Unit,
    onDragStarted: () -> Unit = {},
    onDropped    : (letterId: String, dropPx: Offset) -> Unit
) {
    var offsetX    by remember(resetKey) { mutableStateOf(0f) }
    var offsetY    by remember(resetKey) { mutableStateOf(0f) }
    var isDragging by remember(resetKey) { mutableStateOf(false) }
    var rootPos    by remember { mutableStateOf(Offset.Zero) }
    var sizePx     by remember { mutableStateOf(Offset.Zero) }

    val scale by animateFloatAsState(
        targetValue   = if (isDragging) DRAG_SCALE_ACTIVE else DRAG_SCALE_DEFAULT,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label         = "letterTileScale"
    )

    Box(
        modifier = Modifier
            .size(LETTER_TILE_SIZE)
            .zIndex(if (isDragging) 10f else 1f)
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (isAnswered) 0.5f else 1f }
            .shadow(if (isDragging) 8.dp else 2.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(DragLetterTileBg)
            .border(2.dp, DragActiveCardBorder, RoundedCornerShape(16.dp))
            .onGloballyPositioned { coords ->
                if (!isDragging) {
                    rootPos = coords.positionInRoot()
                    sizePx  = Offset(coords.size.width.toFloat(), coords.size.height.toFloat())
                }
            }
            .pointerInput(isAnswered, resetKey) {
                if (isAnswered) return@pointerInput
                detectDragGestures(
                    onDragStart  = { onDragStarted(); isDragging = true },
                    onDrag       = { change, drag ->
                        change.consume(); offsetX += drag.x; offsetY += drag.y
                    },
                    onDragEnd    = {
                        onDropped(
                            option.letterId,
                            Offset(rootPos.x + sizePx.x / 2f + offsetX, rootPos.y + sizePx.y / 2f + offsetY)
                        )
                        offsetX = 0f; offsetY = 0f; isDragging = false
                    },
                    onDragCancel = { offsetX = 0f; offsetY = 0f; isDragging = false }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        DragImage(
            asset    = option.drawableImage,
            label    = option.labelAr,
            modifier = Modifier.fillMaxSize().padding(8.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GAME 3 — ANIMALS_TO_CAGE
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun AnimalsToCageGame(
    state    : DragGameState,
    viewModel: DragGameViewModel
) {
    val density      = LocalDensity.current
    val dropRadiusPx = with(density) { DROP_RADIUS_DP.toPx() * CAGE_DROP_RADIUS_FACTOR }

    // The cage center is measured from the cage Box via onGloballyPositioned.
    // cageCenterX is used to align the top-center animal's horizontal position.
    val cageCenterPx = remember { mutableStateOf(Offset.Zero) }
    val inCageCount  = state.inCageSet.size

    val shakeAnim = remember { Animatable(0f) }
    LaunchedEffect(state.rejectIdx) {
        if (state.rejectIdx >= 0) {
            shakeAnim.snapTo(0f)
            shakeAnim.animateTo(0f, animationSpec = keyframes {
                durationMillis = REJECT_ANIM_DURATION_MS
                -18f at 50; 18f at 110; -12f at 180; 12f at 250; 0f at REJECT_ANIM_DURATION_MS
            })
        }
    }

    val hintAnim = remember { Animatable(0f) }
    LaunchedEffect(state.showHint) {
        if (state.showHint && !state.isAnswered) {
            while (true) {
                hintAnim.animateTo(0f, animationSpec = keyframes {
                    durationMillis = HINT_WOBBLE_DURATION_MS
                    -6f at 100; 6f at 250; -4f at 380; 4f at 490; -2f at 580; 0f at HINT_WOBBLE_DURATION_MS
                })
                delay(HINT_WOBBLE_PAUSE_MS)
            }
        } else {
            hintAnim.snapTo(0f)
        }
    }

    Column(
        modifier            = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── FIX 1: Timer arc sits to the LEFT of the instruction text ─────────
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Timer widget — always reserves its space so the instruction text
            // doesn't jump when the question is answered and the timer hides.
            Box(
                modifier       = Modifier.size(CAGE_TIMER_ARC_SIZE + 8.dp), // match widget size
                contentAlignment = Alignment.Center
            ) {
                if (!state.isAnswered) {
                    CageTimerArc(state)
                }
            }

            Spacer(Modifier.width(8.dp))

            // Instruction text fills the remaining space
            Text(
                text      = state.instructionText,
                style     = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold, fontSize = CAGE_INSTRUCTION_SIZE_SP.sp
                ),
                textAlign = TextAlign.Start,
                modifier  = Modifier.weight(1f)
            )
        }

        // ── Animal pool ───────────────────────────────────────────────────────
        // We need the cage center's X so the top-center animal can be offset to
        // align with it. We measure the cage Box below and store cageCenterPx.
        // The pool Box reads cageCenterPx.value.x when placing the first animal.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.52f)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            val animalSz    = CAGE_ANIMAL_SIZE
            val boxWidthDp  = maxWidth
            val boxHeightDp = maxHeight

            // FIX 2: position of the top-center animal.
            // Its X offset = (cage-center-X in root) - (this Box's left in root) - (animal width / 2)
            // We capture the pool box's own root position so we can translate cage coords.
            var poolBoxRootX by remember { mutableStateOf(0f) }

            // Positions:
            //   idx 0 — top-center, X derived from cageCenterPx so it lines up vertically
            //           with the cage center; Y stays at top.
            //   idx 1 — bottom-left
            //   idx 2 — bottom-right
            val bottomY = (boxHeightDp - animalSz).value.coerceAtLeast(0f)
            val bottomLeft  = Offset(0f,                                              bottomY)
            val bottomRight = Offset((boxWidthDp - animalSz).value.coerceAtLeast(0f), bottomY)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coords ->
                        poolBoxRootX = coords.positionInRoot().x
                    }
            ) {
                // Compute which pool indices should wobble:
                //   • hint is only active when showHint=true and game is still live
                //   • only remaining (not-yet-caged) animals can wobble
                //   • exactly (targetCount - inCageSet.size) of those remaining animals wobble
                //     e.g. targetCount=2, 1 already caged → only 1 remaining animal wobbles
                val wobbleIndices: Set<Int> = if (state.showHint && !state.isAnswered) {
                    val stillNeeded  = (state.targetCount - state.inCageSet.size).coerceAtLeast(0)
                    val remainingIdx = (0 until state.cagePool.size).filter { it !in state.inCageSet }
                    remainingIdx.take(stillNeeded).toSet()
                } else {
                    emptySet()
                }

                state.cagePool.forEachIndexed { idx, animal ->
                    // For idx 0 derive X from cage center so animal aligns vertically with cage.
                    // For idx 1 & 2 use fixed left/right positions.
                    val xDp: Float
                    val yDp: Float
                    when (idx) {
                        0 -> {
                            val cageCenterLocalX = cageCenterPx.value.x - poolBoxRootX
                            val animalHalfPx     = with(density) { (animalSz / 2).toPx() }
                            xDp = with(density) { (cageCenterLocalX - animalHalfPx).toDp().value }
                                .coerceIn(0f, (boxWidthDp - animalSz).value.coerceAtLeast(0f))
                            yDp = 0f
                        }
                        1    -> { xDp = bottomLeft.x;  yDp = bottomLeft.y }
                        else -> { xDp = bottomRight.x; yDp = bottomRight.y }
                    }

                    Box(modifier = Modifier.offset(x = xDp.dp, y = yDp.dp)) {
                        DraggablePoolAnimal(
                            animal        = animal,
                            isInCage      = idx in state.inCageSet,
                            isRejecting   = idx == state.rejectIdx,
                            isAnswered    = state.isAnswered,
                            animalSize    = animalSz,
                            // Wobble only the exact number of animals still needed
                            hintRotation  = if (idx in wobbleIndices) hintAnim.value else 0f,
                            onDragStarted = { viewModel.onCageAnimalDragStarted(idx) },
                            onDropped     = { dropPx ->
                                if ((dropPx - cageCenterPx.value).getDistance() < dropRadiusPx)
                                    viewModel.onAnimalDroppedToCage(idx)
                            },
                            onRejectDone  = { viewModel.onRejectAnimationDone(idx) }
                        )
                    }
                }
            }
        }

        // ── Cage ──────────────────────────────────────────────────────────────
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.48f)
                .graphicsLayer { translationX = shakeAnim.value }
                .onGloballyPositioned { coords ->
                    val pos = coords.positionInRoot()
                    val sz  = coords.size
                    // Store cage center in root coordinates — used by pool animal placement above
                    cageCenterPx.value = Offset(pos.x + sz.width / 2f, pos.y + sz.height / 2f)
                },
            contentAlignment = Alignment.Center
        ) {
            // Layer 1: faint cage body behind animals
            Image(
                painter            = painterResource(R.drawable.ic_cage),
                contentDescription = null,
                modifier           = Modifier
                    .fillMaxSize()
                    .padding(CAGE_IMG_PADDING)
                    .graphicsLayer { alpha = 0.25f },
                contentScale       = ContentScale.Fit
            )

            // Layer 2: animals inside the cage interior
            val cageAnimals = state.cagePool.filterIndexed { idx, _ -> idx in state.inCageSet }
            if (cageAnimals.isNotEmpty()) {
                val n      = cageAnimals.size.coerceAtLeast(1)
                val horPad = maxWidth * CAGE_INTERIOR_HORPAD_FRACTION
                val availW = maxWidth - horPad * 2f - CAGE_IMG_PADDING * (n - 1).toFloat()
                val imgSz  = (availW / n.toFloat()).coerceIn(CAGE_IN_CAGE_IMG_MIN, CAGE_IN_CAGE_IMG_MAX)
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = horPad, vertical = CAGE_IMG_PADDING)
                        .offset(y = (maxHeight * CAGE_INTERIOR_Y_OFFSET_FRACTION)),
                    horizontalArrangement = Arrangement.spacedBy(CAGE_IMG_PADDING, Alignment.CenterHorizontally),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    cageAnimals.forEach { animal ->
                        val ctx = LocalContext.current
                        AsyncImage(
                            model              = ImageRequest.Builder(ctx)
                                .data("file:///android_asset/${animal.assetPath}")
                                .crossfade(true)
                                .build(),
                            contentDescription = animal.animalId,
                            modifier           = Modifier.size(imgSz),
                            contentScale       = ContentScale.Fit
                        )
                    }
                }
            }

            // Layer 3: cage bars drawn OVER animals
            Image(
                painter            = painterResource(R.drawable.ic_cage),
                contentDescription = stringResource(R.string.cd_cage),
                modifier           = Modifier.fillMaxSize().padding(CAGE_IMG_PADDING),
                contentScale       = ContentScale.Fit
            )

            // Result banner
            if (state.isAnswered) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .background(
                            color = if (state.isCorrect) DragResultCorrectBg else DragResultWrongBg,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text  = if (state.isCorrect) stringResource(R.string.drag_result_correct) else stringResource(R.string.drag_result_wrong),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color      = if (state.isCorrect) DragResultCorrectText else DragResultWrongText
                        )
                    )
                }
            }
        }

        // Progress dots
        Row(
            modifier              = Modifier.padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(state.targetCount) { i ->
                Box(
                    modifier = Modifier
                        .size(CAGE_PROGRESS_DOT_SIZE)
                        .shadow(2.dp, CircleShape)
                        .background(
                            color = if (i < inCageCount) DragProgressDone else DragProgressIdle,
                            shape = CircleShape
                        )
                )
            }
        }
    }
}

// ── Cage timer arc (arc + number, no Row wrapper — caller places it) ──────────
@Composable
private fun CageTimerArc(state: DragGameState) {
    val total    = state.cageTimerTotalSeconds.toFloat().coerceAtLeast(1f)
    val fraction = state.cageTimerSeconds / total

    val animatedFraction by animateFloatAsState(
        targetValue   = fraction,
        animationSpec = tween(durationMillis = TIMER_ARC_TWEEN_MS, easing = LinearEasing),
        label         = "cageTimerArc"
    )

    val timerColor = when {
        fraction <= TIMER_LOW_FRACTION -> DragTimerLow
        fraction <= TIMER_MID_FRACTION -> DragTimerMid
        else                           -> DragTimerOk
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(CAGE_TIMER_ARC_SIZE)) {
            drawArc(
                color      = DragTimerTrack,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter  = false,
                style      = Stroke(width = CAGE_TIMER_STROKE_DP.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color      = timerColor,
                startAngle = -90f,
                sweepAngle = animatedFraction * 360f,
                useCenter  = false,
                style      = Stroke(width = CAGE_TIMER_STROKE_DP.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Text(
            text  = "${state.cageTimerSeconds}",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                color      = timerColor,
                fontSize   = CAGE_TIMER_NUMBER_SIZE_SP.sp
            )
        )
    }
}

// ── Draggable pool animal ─────────────────────────────────────────────────────
@Composable
private fun DraggablePoolAnimal(
    animal       : DragAnimalOption,
    isInCage     : Boolean,
    isRejecting  : Boolean,
    isAnswered   : Boolean,
    animalSize   : Dp = 80.dp,
    hintRotation : Float = 0f,
    onDragStarted: () -> Unit = {},
    onDropped    : (dropPx: Offset) -> Unit,
    onRejectDone : () -> Unit
) {
    var offsetX    by remember { mutableStateOf(0f) }
    var offsetY    by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var rootPos    by remember { mutableStateOf(Offset.Zero) }
    var sizePx     by remember { mutableStateOf(Offset.Zero) }

    val rejectAnim = remember { Animatable(0f) }
    LaunchedEffect(isRejecting) {
        if (isRejecting) {
            rejectAnim.snapTo(0f)
            rejectAnim.animateTo(0f, animationSpec = keyframes {
                durationMillis = REJECT_ANIM_DURATION_MS
                -15f at 50; 15f at 110; -10f at 170; 10f at 230; 0f at REJECT_ANIM_DURATION_MS
            })
            onRejectDone()
        }
    }

    val scale by animateFloatAsState(
        targetValue   = when {
            isDragging -> POOL_DRAG_SCALE
            isInCage   -> POOL_IN_CAGE_SCALE
            else       -> DRAG_SCALE_DEFAULT
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label         = "poolAnimalScale"
    )

    Box(
        modifier = Modifier
            .size(animalSize)
            .zIndex(if (isDragging) 10f else 1f)
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .graphicsLayer {
                scaleX       = scale
                scaleY       = scale
                alpha        = if (isInCage) 0.25f else 1f
                translationX = rejectAnim.value
                rotationZ    = hintRotation
            }
            .onGloballyPositioned { coords ->
                if (!isDragging) {
                    rootPos = coords.positionInRoot()
                    sizePx  = Offset(coords.size.width.toFloat(), coords.size.height.toFloat())
                }
            }
            .pointerInput(isInCage, isAnswered) {
                if (isInCage || isAnswered) return@pointerInput
                detectDragGestures(
                    onDragStart  = { onDragStarted(); isDragging = true },
                    onDrag       = { change, drag -> change.consume(); offsetX += drag.x; offsetY += drag.y },
                    onDragEnd    = {
                        onDropped(Offset(rootPos.x + sizePx.x / 2f + offsetX, rootPos.y + sizePx.y / 2f + offsetY))
                        offsetX = 0f; offsetY = 0f; isDragging = false
                    },
                    onDragCancel = { offsetX = 0f; offsetY = 0f; isDragging = false }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        val context = LocalContext.current
        AsyncImage(
            model              = ImageRequest.Builder(context)
                .data("file:///android_asset/${animal.assetPath}")
                .crossfade(true)
                .build(),
            contentDescription = animal.animalId,
            modifier           = Modifier.fillMaxSize().padding(CAGE_IMG_PADDING),
            contentScale       = ContentScale.Fit
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun buildCirclePath(size: Size): Path {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r  = minOf(size.width, size.height) / 2f * 0.85f
    return Path().apply { addOval(Rect(Offset(cx, cy), r)) }
}

private fun colorHexForDisplay(colorId: String): Long = when (colorId) {
    "color_red"    -> DragColorRedHex
    "color_blue"   -> DragColorBlueHex
    "color_yellow" -> DragColorYellowHex
    "color_green"  -> DragColorGreenHex
    else           -> DragColorGrayHex
}

@Composable
private fun DragImage(
    asset   : ImageAsset,
    label   : String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    when (asset) {
        is ImageAsset.PngAsset -> {
            AsyncImage(
                model              = ImageRequest.Builder(context)
                    .data("file:///android_asset/${asset.path}")
                    .crossfade(true)
                    .build(),
                contentDescription = label,
                modifier           = modifier,
                contentScale       = ContentScale.Fit
            )
        }
        is ImageAsset.SvgDrawable -> {
            val drawableId = context.resources.getIdentifier(
                asset.drawableName, "drawable", context.packageName
            )
            if (drawableId != 0) {
                Image(
                    painter            = painterResource(id = drawableId),
                    contentDescription = label,
                    modifier           = modifier,
                    contentScale       = ContentScale.Fit
                )
            } else {
                Box(
                    modifier         = modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text      = label,
                        style     = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}