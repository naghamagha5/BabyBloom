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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
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
import com.babybloom.presentation.viewmodels.ColorOption
import com.babybloom.presentation.viewmodels.DragAnimalOption
import com.babybloom.presentation.viewmodels.DragGameState
import com.babybloom.presentation.viewmodels.DragGameViewModel
import com.babybloom.presentation.viewmodels.DragType
import com.babybloom.presentation.viewmodels.LetterOption
import com.babybloom.presentation.viewmodels.ShapeOption
import com.babybloom.ui.theme.DragAttemptDotFull
import com.babybloom.ui.theme.DragProgressIdle
import com.babybloom.ui.theme.GameActiveSwatch1
import com.babybloom.ui.theme.GameActiveSwatch2
import com.babybloom.ui.theme.GameActiveSwatch3
import com.babybloom.ui.theme.GameActiveSwatch4
import com.babybloom.ui.theme.GameActiveSwatch5
import com.babybloom.ui.theme.GameCalmSwatch1
import com.babybloom.ui.theme.GameCalmSwatch2
import com.babybloom.ui.theme.GameCalmSwatch3
import com.babybloom.ui.theme.GameCalmSwatch4
import com.babybloom.ui.theme.GameCalmSwatch5
import com.babybloom.ui.theme.LocalGameColorScheme
import com.babybloom.ui.theme.WarmPeach
import com.babybloom.ui.theme.dragColorForContentId
import com.babybloom.util.AssetPathResolver
import com.babybloom.util.ImageAsset
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// ── Layout constants ──────────────────────────────────────────────────────────
private val DROP_RADIUS_DP              = 90.dp
private val CAGE_DROP_RADIUS_FACTOR     = 1.8f
private val COLOR_GAME_SHAPE_SIZE              = 180.dp
private val COLOR_GAME_PEN_BOX_H               = 110.dp
private val COLOR_GAME_SWATCH_H                = 96.dp
private val COLOR_GAME_LEARNING_SWATCH_H       = 132.dp
private const val COLOR_GAME_INSTRUCTION_SIZE_SP     = 18
private val COLOR_SWATCH_TILE_RADIUS           = 18.dp
private val COLOR_PICK_RADIUS_DP               = 72.dp
private val LETTER_ANIMAL_IMG_SIZE         = 165.dp
private val LETTER_TILE_SIZE_ASSESSMENT    = 92.dp
private val LETTER_TILE_SIZE_LEARNING      = 92.dp
private val LETTER_SLOT_SIZE               = 72.dp
private val LETTER_PUZZLE_FONT_SP          = 65
private val LETTER_INSTRUCTION_SIZE_SP     = 17
private val LETTER_FALLBACK_FONT_SP        = 65
private val CAGE_ANIMAL_SIZE               = 280.dp
private val CAGE_IMG_PADDING               = 0.dp
private val CAGE_INTERIOR_HORPAD_FRACTION  = 0.22f
private val CAGE_INTERIOR_Y_OFFSET_FRACTION= 0.08f
private val CAGE_PROGRESS_DOT_SIZE         = 18.dp
private val CAGE_IN_CAGE_IMG_MAX           = 120.dp
private val CAGE_IN_CAGE_IMG_MIN           = 50.dp
private val CAGE_INSTRUCTION_SIZE_SP       = 18
private val CAGE_X_CENTER_ADJUST           = (-18).dp
private val ATTEMPT_DOT_SIZE               = 12.dp
// Shape game — bigger tiles, no borders
private val SHAPE_TILE_SIZE                = 130.dp   // was 96.dp
private val SHAPE_OUTLINE_SLOT_SIZE        = 130.dp   // was 110.dp
private val SHAPE_DROP_RADIUS_DP           = 90.dp    // slightly larger to match bigger tiles
private val SHAPE_INSTRUCTION_SIZE_SP      = 18

// ── Drag/animation constants ──────────────────────────────────────────────────
private const val DRAG_SCALE_ACTIVE        = 1.25f
private const val DRAG_SCALE_DEFAULT       = 1f
private const val PEN_DRAG_SCALE           = 1.4f
private const val PEN_DRAG_SCALE_DEFAULT   = 1f
private const val POOL_DRAG_SCALE          = 1.35f
private const val POOL_IN_CAGE_SCALE       = 0.6f
private const val HINT_WOBBLE_DURATION_MS  = 700
private const val HINT_WOBBLE_PAUSE_MS     = 300L
private const val REJECT_ANIM_DURATION_MS  = 350
private const val SHAKE_ANIM_DURATION_MS   = 500

// ── Shape-game color palettes (mode-driven, never hardcoded per shape) ────────
private val calmShapeSwatches = listOf(
    GameCalmSwatch1,
    GameCalmSwatch2,
    GameCalmSwatch3,
    GameCalmSwatch4,
    GameCalmSwatch5
)
private val activeShapeSwatches = listOf(
    GameActiveSwatch1,
    GameActiveSwatch2,
    GameActiveSwatch3,
    GameActiveSwatch4,
    GameActiveSwatch5
)

/**
 * Returns a stable color for [index] based on the current game mode.
 * Colors cycle through the mode-appropriate swatch list.
 * [isCalmMode] is derived from the GameColorScheme accent — callers pass it
 * from [DragGameState.isCalmMode] or detect it via the LocalGameColorScheme.
 */
private fun shapeColorForIndex(index: Int, isCalmMode: Boolean): Color {
    val swatches = if (isCalmMode) calmShapeSwatches else activeShapeSwatches
    return swatches[index % swatches.size]
}

// ─────────────────────────────────────────────────────────────────────────────
// Entry point
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun DragGameScreen(
    currentItem : ActivityContent,
    isCalmMode  : Boolean,
    isTest      : Boolean,
    onComplete  : (isCorrect: Boolean, elapsedMs: Long, touchComplexity: Float) -> Unit,
    viewModel   : DragGameViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(currentItem.contentId, isCalmMode, isTest) {
        viewModel.loadContent(currentItem, isCalmMode, isTest, onComplete)
    }

    val prevSignal = remember { mutableIntStateOf(0) }
    LaunchedEffect(state.sessionCompleteSignal) {
        if (state.sessionCompleteSignal > prevSignal.intValue) {
            prevSignal.intValue = state.sessionCompleteSignal
            onComplete(
                state.sessionCorrectCount > state.sessionWrongAttempts,
                state.sessionElapsedMs,
                0f
            )
        }
    }

    if (state.isLoading) {
        val colors = LocalGameColorScheme.current
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = colors.accent)
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            RoundIndicator(state)
            // Game content — fills the available space; AttemptsRow sits at the bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (state.dragType) {
                    DragType.COLOR_TO_SHAPE   -> ColorToPaintGame(state, viewModel)
                    DragType.LETTER_TO_WORD   -> LetterToWordGame(state, viewModel)
                    DragType.ANIMALS_TO_CAGE  -> AnimalsToCageGame(state, viewModel)
                    DragType.SHAPE_TO_OUTLINE -> ShapeToOutlineGame(state, viewModel, isCalmMode)
                }
            }
            // ── Attempts row — now at the BOTTOM, dots fill as attempts are used ──
            AttemptsRow(state)
        }
        if (state.showCelebration) {
            GoodJobPopup()
        }
    }
}

// ── Round indicator ───────────────────────────────────────────────────────────
@Composable
private fun RoundIndicator(state: DragGameState) {
    if (state.totalRounds == 0 || state.questionsInRound == 0) return
    val colors = LocalGameColorScheme.current
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
                            i < state.questionInRound - 1  -> colors.accent
                            i == state.questionInRound - 1 -> colors.accent.copy(alpha = 0.5f)
                            else                           -> DragProgressIdle
                        },
                        shape = CircleShape
                    )
                )
            }
        }
    }
}

// ── Attempts row — BOTTOM, empty → fill as attempts are used ─────────────────
@Composable
private fun AttemptsRow(state: DragGameState) {
    val colors = LocalGameColorScheme.current
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        repeat(3) { i ->
            val isFilled = i < state.attemptsUsed
            Box(
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .size(ATTEMPT_DOT_SIZE)
                    .border(
                        width = 2.dp,
                        color = if (isFilled) DragAttemptDotFull else colors.accent.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
                    .background(
                        color = if (isFilled) DragAttemptDotFull else Color.Transparent,
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
    state    : DragGameState,
    viewModel: DragGameViewModel
) {
    val colors    = LocalGameColorScheme.current
    val resetKey  = "${state.correctId}_${state.resetTrigger}"
    val strokeKey = "${resetKey}_${state.attemptsUsed}"

    var shapeCenterPx   by remember { mutableStateOf(Offset.Zero) }
    var shapeHalfSizePx by remember { mutableFloatStateOf(0f) }
    var boxTopLeftPx    by remember { mutableStateOf(Offset.Zero) }
    val penStrokePts    = remember(strokeKey) { mutableStateListOf<Offset>() }

    val localPenColorIdState   = remember(strokeKey) { mutableStateOf<String?>(null) }
    val lastPickedColorIdState = remember(strokeKey) { mutableStateOf<String?>(null) }
    var localPenColorId   by localPenColorIdState
    var lastPickedColorId by lastPickedColorIdState

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

    val hintAnim = remember { Animatable(0f) }
    LaunchedEffect(state.hintColorId, state.isAnswered) {
        if (state.hintColorId != null && !state.isAnswered) {
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

    val activeColorId = localPenColorId ?: lastPickedColorId ?: state.activeColorId
    val activeColor   = activeColorId?.let { id ->
        state.colorOptions.find { it.colorId == id }
    }?.let { option -> dragColorForContentId(option.colorId) }

    Column(
        modifier            = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text      = stringResource(R.string.drag_color_instruction),
            style     = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize   = COLOR_GAME_INSTRUCTION_SIZE_SP.sp
            ),
            textAlign = TextAlign.Center,
            modifier  = Modifier.padding(bottom = 8.dp)
        )

        val correctDisplayColor = dragColorForContentId(state.correctId)
        Text(
            text  = state.currentLabel,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color      = correctDisplayColor
            ),
            textAlign = TextAlign.Center,
            modifier  = Modifier.padding(bottom = 12.dp)
        )

        Box(
            modifier = Modifier
                .size(COLOR_GAME_SHAPE_SIZE)
                .graphicsLayer { translationX = shakeAnim.value }
                .onGloballyPositioned { coords ->
                    val pos         = coords.positionInRoot()
                    val sz          = coords.size
                    boxTopLeftPx    = pos
                    shapeCenterPx   = Offset(pos.x + sz.width / 2f, pos.y + sz.height / 2f)
                    shapeHalfSizePx = sz.width / 2f
                },
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val path = buildShapePath(size, state.colorShapeId)
                val base = when {
                    state.isCorrect                      -> activeColor ?: colors.background
                    state.isAnswered && !state.isCorrect -> colors.wrong.copy(alpha = 0.25f)
                    else                                 -> colors.background
                }
                drawPath(path = path, color = base)
                if (penStrokePts.size >= 2 && activeColor != null) {
                    clipPath(path) {
                        val strokePath = Path()
                        strokePath.moveTo(penStrokePts[0].x, penStrokePts[0].y)
                        penStrokePts.drop(1).forEach { strokePath.lineTo(it.x, it.y) }
                        drawPath(
                            path  = strokePath,
                            color = activeColor,
                            style = Stroke(
                                width = 40.dp.toPx(),
                                cap   = StrokeCap.Round,
                                join  = StrokeJoin.Round
                            )
                        )
                    }
                }
                drawPath(
                    path  = path,
                    color = when {
                        state.isCorrect                      -> colors.correct
                        state.isAnswered && !state.isCorrect -> colors.wrong
                        else                                 -> colors.accent
                    },
                    style = Stroke(width = 10f)
                )
            }
            when {
                state.isCorrect ->
                    Text(
                        stringResource(R.string.drag_checkmark),
                        fontSize   = 60.sp,
                        color      = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                state.isAnswered && !state.isCorrect ->
                    Text(
                        stringResource(R.string.drag_crossmark),
                        fontSize   = 60.sp,
                        color      = colors.wrong,
                        fontWeight = FontWeight.Bold
                    )
                penStrokePts.isEmpty() ->
                    Text(
                        stringResource(R.string.drag_color_shape_placeholder),
                        fontSize   = 60.sp,
                        fontWeight = FontWeight.Bold,
                        color      = colors.accent
                    )
            }
        }

        BoxWithConstraints(
            modifier         = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center
        ) {
            if (state.isTest) {
                TestColorLayout(
                    state                  = state,
                    strokeKey              = strokeKey,
                    shapeCenterPxState     = remember { derivedStateOf { shapeCenterPx } },
                    shapeHalfSizePxState   = remember { derivedStateOf { shapeHalfSizePx } },
                    boxTopLeftPxState      = remember { derivedStateOf { boxTopLeftPx } },
                    penStrokePts           = penStrokePts,
                    activeColor            = activeColor,
                    hintAnim               = hintAnim,
                    viewModel              = viewModel,
                    lastPickedColorIdState = lastPickedColorIdState,
                    onLocalColorId         = { newId ->
                        localPenColorId = newId
                        if (newId != null) lastPickedColorId = newId
                    }
                )
            } else {
                state.colorOptions.firstOrNull()?.let { option ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        DraggablePen(
                            colorOption    = option,
                            penBoxDp       = COLOR_GAME_PEN_BOX_H,
                            isAnswered     = state.isAnswered,
                            resetKey       = strokeKey,
                            fillColor      = activeColor,
                            hintRotation   = 0f,
                            onDragStarted  = { viewModel.onColorPickedUp(option.colorId) },
                            onDragFinished = { viewModel.onPenDragReleased() },
                            onPenMove      = { fingerPx, drag ->
                                viewModel.onTouchPoint(fingerPx)
                                if (!state.isAnswered && state.fillProgress < 1f &&
                                    (fingerPx - shapeCenterPx).getDistance() < shapeHalfSizePx * 1.4f
                                ) {
                                    penStrokePts.add(fingerPx - boxTopLeftPx)
                                    viewModel.onPenMovedOverShape(drag.getDistance(), option.colorId)
                                }
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                        ColorSwatchTile(
                            option     = option,
                            size       = COLOR_GAME_LEARNING_SWATCH_H,
                            isAnswered = state.isAnswered
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Test colour layout — 4 swatches in corners, pen in center
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun BoxWithConstraintsScope.TestColorLayout(
    state                  : DragGameState,
    strokeKey              : Any,
    shapeCenterPxState     : State<Offset>,
    shapeHalfSizePxState   : State<Float>,
    boxTopLeftPxState      : State<Offset>,
    penStrokePts           : androidx.compose.runtime.snapshots.SnapshotStateList<Offset>,
    activeColor            : Color?,
    hintAnim               : Animatable<Float, AnimationVector1D>,
    viewModel              : DragGameViewModel,
    lastPickedColorIdState : MutableState<String?>,
    onLocalColorId         : (String?) -> Unit
) {
    val swatchCenters = remember(strokeKey) { mutableStateMapOf<String, Offset>() }
    val pickRadiusPx  = with(LocalDensity.current) { COLOR_PICK_RADIUS_DP.toPx() }

    val horzEdgePad   = 15.dp
    val topEdgePad    = 90.dp
    val bottomEdgePad = 90.dp

    val cornerAlignments = listOf(
        Alignment.TopStart,
        Alignment.TopEnd,
        Alignment.BottomStart,
        Alignment.BottomEnd
    )

    state.colorOptions.take(4).forEachIndexed { idx, option ->
        val alignment = cornerAlignments[idx]
        Box(
            modifier = Modifier
                .align(alignment)
                .padding(
                    start  = if (alignment == Alignment.TopStart    || alignment == Alignment.BottomStart) horzEdgePad   else 0.dp,
                    end    = if (alignment == Alignment.TopEnd      || alignment == Alignment.BottomEnd)   horzEdgePad   else 0.dp,
                    top    = if (alignment == Alignment.TopStart    || alignment == Alignment.TopEnd)      topEdgePad    else 0.dp,
                    bottom = if (alignment == Alignment.BottomStart || alignment == Alignment.BottomEnd)   bottomEdgePad else 0.dp,
                )
                .onGloballyPositioned { coords ->
                    val pos = coords.positionInRoot()
                    val sz  = coords.size
                    swatchCenters[option.colorId] = Offset(
                        pos.x + sz.width  / 2f,
                        pos.y + sz.height / 2f
                    )
                }
        ) {
            ColorSwatchTile(
                option     = option,
                size       = COLOR_GAME_SWATCH_H,
                isAnswered = state.isAnswered
            )
        }
    }

    val representativeOption = state.colorOptions.firstOrNull() ?: return

    Box(modifier = Modifier.align(Alignment.Center)) {
        DraggablePen(
            colorOption    = representativeOption,
            penBoxDp       = COLOR_GAME_PEN_BOX_H,
            isAnswered     = state.isAnswered,
            resetKey       = strokeKey,
            fillColor      = activeColor,
            hintRotation   = if (state.hintColorId != null) hintAnim.value else 0f,
            onDragStarted  = {
                val last = lastPickedColorIdState.value
                onLocalColorId(last)
                viewModel.onColorPickedUp(last ?: "")
            },
            onDragFinished = { viewModel.onPenDragReleased() },
            onPenMove      = { fingerPx, drag ->
                viewModel.onTouchPoint(fingerPx)

                val shapeCenter   = shapeCenterPxState.value
                val shapeHalfSize = shapeHalfSizePxState.value
                val boxTopLeft    = boxTopLeftPxState.value

                val nearest = swatchCenters
                    .minByOrNull { (_, centre) -> (fingerPx - centre).getDistance() }
                    ?.takeIf   { (_, centre) -> (fingerPx - centre).getDistance() < pickRadiusPx }

                if (nearest != null) {
                    onLocalColorId(nearest.key)
                    viewModel.onPenTouchedColor(nearest.key)
                }

                val resolvedColorId: String? =
                    nearest?.key
                        ?: lastPickedColorIdState.value
                        ?: state.activeColorId

                if (resolvedColorId != null &&
                    !state.isAnswered &&
                    state.fillProgress < 1f &&
                    shapeCenter != Offset.Zero &&
                    (fingerPx - shapeCenter).getDistance() < shapeHalfSize * 1.4f
                ) {
                    penStrokePts.add(fingerPx - boxTopLeft)
                    viewModel.onPenMovedOverShape(drag.getDistance(), resolvedColorId)
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DraggablePen
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun DraggablePen(
    colorOption   : ColorOption,
    penBoxDp      : Dp      = 68.dp,
    isAnswered    : Boolean,
    resetKey      : Any     = Unit,
    fillColor     : Color?  = null,
    hintRotation  : Float   = 0f,
    onDragStarted : ()      -> Unit = {},
    onDragFinished: ()      -> Unit = {},
    onPenMove     : (fingerPx: Offset, dragDelta: Offset) -> Unit
) {
    var offsetX        by remember(resetKey) { mutableStateOf(0f) }
    var offsetY        by remember(resetKey) { mutableStateOf(0f) }
    var isDragging     by remember(resetKey) { mutableStateOf(false) }
    var rootPos        by remember { mutableStateOf(Offset.Zero) }
    var dragStartLocal by remember(resetKey) { mutableStateOf(Offset.Zero) }

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
            .graphicsLayer {
                scaleX    = scale
                scaleY    = scale
                rotationZ = hintRotation
            }
            .onGloballyPositioned { coords ->
                if (!isDragging) rootPos = coords.positionInRoot()
            }
            .pointerInput(isAnswered, resetKey) {
                if (isAnswered) return@pointerInput
                detectDragGestures(
                    onDragStart  = { localOffset ->
                        dragStartLocal = localOffset
                        onDragStarted()
                        isDragging = true
                    },
                    onDrag       = { change, drag ->
                        change.consume()
                        offsetX += drag.x
                        offsetY += drag.y
                        val fingerX = rootPos.x + dragStartLocal.x + offsetX
                        val fingerY = rootPos.y + dragStartLocal.y + offsetY
                        onPenMove(Offset(fingerX, fingerY), drag)
                    },
                    onDragEnd    = {
                        onDragFinished()
                        offsetX = 0f; offsetY = 0f; isDragging = false
                    },
                    onDragCancel = {
                        onDragFinished()
                        offsetX = 0f; offsetY = 0f; isDragging = false
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        val iconSize  = if (isDragging) penBoxDp * 0.95f else penBoxDp * 0.85f
        val baseAlpha = if (isAnswered) 0.4f else 1f

        Image(
            painter            = painterResource(R.drawable.ic_pen_color_layer),
            contentDescription = null,
            modifier           = Modifier
                .size(iconSize)
                .graphicsLayer { rotationZ = -25f; alpha = baseAlpha },
            contentScale = ContentScale.Fit,
            colorFilter  = fillColor?.let { ColorFilter.tint(it) }
        )
        Image(
            painter            = painterResource(R.drawable.ic_pen_static_layer),
            contentDescription = colorOption.labelAr,
            modifier           = Modifier
                .size(iconSize)
                .graphicsLayer { rotationZ = -25f; alpha = baseAlpha },
            contentScale = ContentScale.Fit
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Colour swatch tile
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ColorSwatchTile(
    option    : ColorOption,
    size      : Dp,
    isAnswered: Boolean,
    modifier  : Modifier = Modifier
) {
    val colors = LocalGameColorScheme.current
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(COLOR_SWATCH_TILE_RADIUS))
            .background(WarmPeach.copy(alpha = 0.72f))
            .border(3.dp, colors.accent.copy(alpha = 0.75f), RoundedCornerShape(COLOR_SWATCH_TILE_RADIUS))
            .padding(12.dp)
            .graphicsLayer { alpha = if (isAnswered) 0.4f else 1f },
        contentAlignment = Alignment.Center
    ) {
        DragImage(
            asset    = option.drawableImage,
            label    = option.labelAr,
            modifier = Modifier.fillMaxSize()
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
    val colors       = LocalGameColorScheme.current
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

    val hintAnim = remember { Animatable(0f) }
    LaunchedEffect(state.hintLetterId, state.isAnswered) {
        if (state.hintLetterId != null && !state.isAnswered) {
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
        modifier            = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text      = stringResource(R.string.drag_letter_instruction),
            style     = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize   = LETTER_INSTRUCTION_SIZE_SP.sp
            ),
            textAlign = TextAlign.Center
        )

        Box(
            modifier = Modifier
                .size(LETTER_ANIMAL_IMG_SIZE)
                .shadow(4.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(colors.background)
                .border(3.dp, colors.accent, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            val animalPath = state.animalQuestionImage
            if (animalPath != null) {
                val ctx = LocalContext.current
                AsyncImage(
                    model              = ImageRequest.Builder(ctx)
                        .data(AssetPathResolver.androidAssetUri(animalPath))
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
                        color      = MaterialTheme.colorScheme.onSurface
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
                                fontWeight = FontWeight.Bold,
                                fontSize   = LETTER_PUZZLE_FONT_SP.sp,
                                color      = MaterialTheme.colorScheme.onSurface
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
            val tileSize  = if (state.isTest) LETTER_TILE_SIZE_ASSESSMENT else LETTER_TILE_SIZE_LEARNING
            val touchSize = tileSize + 12.dp
            state.letterOptions.forEach { letterOption ->
                Box(modifier = Modifier.size(touchSize), contentAlignment = Alignment.Center) {
                    DraggableLetterTile(
                        option         = letterOption,
                        isAnswered     = state.isAnswered,
                        tileSize       = tileSize,
                        resetKey       = resetKey,
                        hintRotation   = if (letterOption.letterId == state.hintLetterId) hintAnim.value else 0f,
                        onDragStarted  = { viewModel.onLetterTileDragStarted(letterOption.letterId) },
                        onDragFinished = { viewModel.onLetterTileDragReleased() },
                        onDragMove     = { pos -> viewModel.onTouchPoint(pos) },
                        onDropped      = { letterId, dropPx ->
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
    val colors = LocalGameColorScheme.current
    Box(
        modifier = modifier
            .size(LETTER_SLOT_SIZE)
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 3.dp,
                color = when {
                    isCorrect                -> colors.correct
                    isAnswered && !isCorrect -> colors.wrong
                    else                     -> colors.accent
                },
                shape = RoundedCornerShape(12.dp)
            )
            .background(
                when {
                    isCorrect                -> colors.correct.copy(alpha = 0.15f)
                    isAnswered && !isCorrect -> colors.wrong.copy(alpha = 0.15f)
                    else                     -> colors.background
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
                    color      = colors.accent.copy(alpha = 0.4f)
                )
            )
        }
    }
}

// ── Draggable letter tile ─────────────────────────────────────────────────────
@Composable
private fun DraggableLetterTile(
    option        : LetterOption,
    isAnswered    : Boolean,
    tileSize      : Dp,
    resetKey      : Any    = Unit,
    hintRotation  : Float  = 0f,
    onDragStarted : ()     -> Unit = {},
    onDragFinished: ()     -> Unit = {},
    onDragMove    : ((Offset) -> Unit)? = null,
    onDropped     : (letterId: String, dropPx: Offset) -> Unit
) {
    val colors = LocalGameColorScheme.current
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
            .size(tileSize)
            .zIndex(if (isDragging) 10f else 1f)
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .graphicsLayer {
                scaleX    = scale
                scaleY    = scale
                alpha     = if (isAnswered) 0.5f else 1f
                rotationZ = hintRotation
            }
            .shadow(if (isDragging) 8.dp else 2.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(colors.background)
            .border(2.dp, colors.accent, RoundedCornerShape(16.dp))
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
                        change.consume()
                        offsetX += drag.x
                        offsetY += drag.y
                        val cx = rootPos.x + sizePx.x / 2f + offsetX
                        val cy = rootPos.y + sizePx.y / 2f + offsetY
                        onDragMove?.invoke(Offset(cx, cy))
                    },
                    onDragEnd    = {
                        onDropped(
                            option.letterId,
                            Offset(rootPos.x + sizePx.x / 2f + offsetX,
                                rootPos.y + sizePx.y / 2f + offsetY)
                        )
                        onDragFinished()
                        offsetX = 0f; offsetY = 0f; isDragging = false
                    },
                    onDragCancel = {
                        onDragFinished()
                        offsetX = 0f; offsetY = 0f; isDragging = false
                    }
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
    val colors       = LocalGameColorScheme.current
    val density      = LocalDensity.current
    val dropRadiusPx = with(density) { DROP_RADIUS_DP.toPx() * CAGE_DROP_RADIUS_FACTOR }

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

    Column(
        modifier            = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text      = state.instructionText,
            style     = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize   = CAGE_INSTRUCTION_SIZE_SP.sp
            ),
            textAlign = TextAlign.Center,
            modifier  = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp)
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.52f)
                .padding(horizontal = 0.dp, vertical = 2.dp)
        ) {
            val rows     = state.cagePool.withIndex().chunked(4)
            val rowCount = rows.size.coerceAtLeast(1)
            val spacing  = (-20).dp
            val animalSz = minOf(
                (maxWidth + 60.dp) / 4f,
                (maxHeight - 8.dp) / rowCount.toFloat()
            ).coerceIn(CAGE_IN_CAGE_IMG_MIN, CAGE_ANIMAL_SIZE)

            Column(
                modifier            = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                rows.forEach { row ->
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        row.forEach { (idx, animal) ->
                            DraggablePoolAnimal(
                                animal        = animal,
                                isInCage      = idx in state.inCageSet,
                                isRejecting   = idx == state.rejectIdx,
                                isAnswered    = state.isAnswered,
                                animalSize    = animalSz,
                                onDragStarted = { viewModel.onCageAnimalDragStarted(idx) },
                                onDragMove    = { pos -> viewModel.onTouchPoint(pos) },
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
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.48f)
                .offset(x = CAGE_X_CENTER_ADJUST)
                .graphicsLayer { translationX = shakeAnim.value }
                .onGloballyPositioned { coords ->
                    val pos = coords.positionInRoot()
                    val sz  = coords.size
                    cageCenterPx.value = Offset(pos.x + sz.width / 2f, pos.y + sz.height / 2f)
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter            = painterResource(R.drawable.ic_cage),
                contentDescription = null,
                modifier           = Modifier
                    .fillMaxSize()
                    .padding(CAGE_IMG_PADDING)
                    .graphicsLayer { alpha = 0.25f },
                contentScale       = ContentScale.Fit
            )

            val cageAnimals = state.cagePool.filterIndexed { idx, _ -> idx in state.inCageSet }
            if (cageAnimals.isNotEmpty()) {
                val n      = cageAnimals.size.coerceAtLeast(1)
                val horPad = maxWidth * CAGE_INTERIOR_HORPAD_FRACTION
                val availW = maxWidth - horPad * 2f - 4.dp * (n - 1).toFloat()
                val imgSz  = (availW / n.toFloat()).coerceIn(CAGE_IN_CAGE_IMG_MIN, CAGE_IN_CAGE_IMG_MAX)
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = horPad, vertical = 4.dp)
                        .offset(y = (maxHeight * CAGE_INTERIOR_Y_OFFSET_FRACTION)),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    cageAnimals.forEach { animal ->
                        val ctx = LocalContext.current
                        AsyncImage(
                            model              = ImageRequest.Builder(ctx)
                                .data(AssetPathResolver.androidAssetUri(animal.assetPath))
                                .crossfade(true)
                                .build(),
                            contentDescription = animal.animalId,
                            modifier           = Modifier.size(imgSz),
                            contentScale       = ContentScale.Fit
                        )
                    }
                }
            }

            Image(
                painter            = painterResource(R.drawable.ic_cage),
                contentDescription = stringResource(R.string.cd_cage),
                modifier           = Modifier.fillMaxSize().padding(CAGE_IMG_PADDING),
                contentScale       = ContentScale.Fit
            )
        }
    }
}

// ── Draggable pool animal ─────────────────────────────────────────────────────
@Composable
private fun DraggablePoolAnimal(
    animal       : DragAnimalOption,
    isInCage     : Boolean,
    isRejecting  : Boolean,
    isAnswered   : Boolean,
    animalSize   : Dp    = 80.dp,
    hintRotation : Float = 0f,
    onDragStarted: ()    -> Unit = {},
    onDragMove   : ((Offset) -> Unit)? = null,
    onDropped    : (dropPx: Offset) -> Unit,
    onRejectDone : ()    -> Unit
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
                    onDrag       = { change, drag ->
                        change.consume()
                        offsetX += drag.x
                        offsetY += drag.y
                        val cx = rootPos.x + sizePx.x / 2f + offsetX
                        val cy = rootPos.y + sizePx.y / 2f + offsetY
                        onDragMove?.invoke(Offset(cx, cy))
                    },
                    onDragEnd    = {
                        onDropped(
                            Offset(rootPos.x + sizePx.x / 2f + offsetX,
                                rootPos.y + sizePx.y / 2f + offsetY)
                        )
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
                .data(AssetPathResolver.androidAssetUri(animal.assetPath))
                .crossfade(true)
                .build(),
            contentDescription = animal.animalId,
            modifier           = Modifier.fillMaxSize().padding(CAGE_IMG_PADDING),
            contentScale       = ContentScale.Fit
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GAME 4 — SHAPE_TO_OUTLINE
//
// Layout (both learning & testing):
//   TOP    → colored filled shape tiles arranged in a 2-column grid (draggable)
//   BOTTOM → outline/ghost drop slots arranged in a 2-column grid
//
// Colors are driven entirely by the mode swatch lists — no hardcoding.
// Borders are removed from both tiles and slots.
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ShapeToOutlineGame(
    state      : DragGameState,
    viewModel  : DragGameViewModel,
    isCalmMode : Boolean
) {
    val density      = LocalDensity.current
    val dropRadiusPx = with(density) { SHAPE_DROP_RADIUS_DP.toPx() }
    val resetKey     = "${state.correctId}_${state.resetTrigger}_${state.attemptsUsed}"

    // Map from slotShapeId → centre position in screen space
    val slotCenters = remember(resetKey) { mutableStateMapOf<String, Offset>() }

    // Build a stable index→color map so each shape always gets the same color
    // within a round, keyed by the shape's position in shapeOptions list.
    val shapeColorMap = remember(state.shapeOptions, isCalmMode) {
        state.shapeOptions.mapIndexed { idx, option ->
            option.shapeId to shapeColorForIndex(idx, isCalmMode)
        }.toMap()
    }

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

    val hintAnim = remember { Animatable(0f) }
    LaunchedEffect(state.hintShapeId, state.isAnswered) {
        if (state.hintShapeId != null && !state.isAnswered) {
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
        modifier            = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .graphicsLayer { translationX = shakeAnim.value },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text      = stringResource(R.string.drag_shape_instruction),
            style     = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize   = SHAPE_INSTRUCTION_SIZE_SP.sp
            ),
            textAlign = TextAlign.Center,
            modifier  = Modifier.fillMaxWidth()
        )

        if (!state.isTest) {
            // ── Learning: 1 colored tile on top, 1 outline slot below ─────────
            val targetSlotId = state.outlineSlots.firstOrNull() ?: return@Column
            val tileOption   = state.shapeOptions.firstOrNull() ?: return@Column
            val tileColor    = shapeColorMap[tileOption.shapeId] ?: shapeColorForIndex(0, isCalmMode)

            // Colored draggable tile — TOP
            DraggableShapeTile(
                option         = tileOption,
                isAnswered     = state.isAnswered,
                tileSize       = SHAPE_TILE_SIZE,
                resetKey       = resetKey,
                fillColor      = tileColor,
                hintRotation   = if (tileOption.shapeId == state.hintShapeId) hintAnim.value else 0f,
                onDragMove     = { viewModel.onTouchPoint(it) },
                onDropped      = { shapeId, dropPx ->
                    val nearest = slotCenters
                        .minByOrNull { (_, c) -> (dropPx - c).getDistance() }
                        ?.takeIf { (_, c) -> (dropPx - c).getDistance() < dropRadiusPx }
                    if (nearest != null) {
                        viewModel.onShapeDroppedToOutline(shapeId, nearest.key)
                    }
                }
            )

            Spacer(Modifier.weight(1f))

            // Outline drop slot — BOTTOM
            ShapeOutlineSlot(
                slotShapeId    = targetSlotId,
                droppedShapeId = state.droppedShapes[targetSlotId],
                isCorrect      = state.isCorrect,
                isWrong        = state.wrongDropSlotId == targetSlotId,
                isAnswered     = state.isAnswered,
                shapeOptions   = state.shapeOptions,
                shapeColorMap  = shapeColorMap,
                modifier       = Modifier.onGloballyPositioned { coords ->
                    val pos = coords.positionInRoot()
                    val sz  = coords.size
                    slotCenters[targetSlotId] = Offset(
                        pos.x + sz.width  / 2f,
                        pos.y + sz.height / 2f
                    )
                }
            )

        } else {
            // ── Testing: 2×2 grid of colored tiles on top,
            //             2×2 grid of outline slots on bottom ─────────────────

            // Colored draggable tiles — TOP (2-column grid)
            ShapeTilesGrid(
                shapeOptions  = state.shapeOptions,
                isAnswered    = state.isAnswered,
                droppedShapes = state.droppedShapes,
                shapeColorMap = shapeColorMap,
                hintShapeId   = state.hintShapeId,
                hintRotation  = hintAnim.value,
                resetKey      = resetKey,
                slotCenters   = slotCenters,
                dropRadiusPx  = dropRadiusPx,
                viewModel     = viewModel
            )

            Spacer(Modifier.weight(1f))

            // Outline drop slots — BOTTOM (2-column grid)
            ShapeOutlineSlotsGrid(
                outlineSlots   = state.outlineSlots,
                droppedShapes  = state.droppedShapes,
                isCorrect      = state.isCorrect,
                wrongDropSlotId = state.wrongDropSlotId,
                isAnswered     = state.isAnswered,
                shapeOptions   = state.shapeOptions,
                shapeColorMap  = shapeColorMap,
                slotCenters    = slotCenters
            )
        }
    }
}

// ── 2-column grid of colored draggable shape tiles ────────────────────────────
@Composable
private fun ShapeTilesGrid(
    shapeOptions  : List<ShapeOption>,
    isAnswered    : Boolean,
    droppedShapes : Map<String, String?>,
    shapeColorMap : Map<String, Color>,
    hintShapeId   : String?,
    hintRotation  : Float,
    resetKey      : Any,
    slotCenters   : MutableMap<String, Offset>,
    dropRadiusPx  : Float,
    viewModel     : DragGameViewModel
) {
    // Chunk into rows of 2 for a 2-column layout
    val rows = shapeOptions.chunked(2)
    Column(
        modifier            = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        rows.forEach { rowOptions ->
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                rowOptions.forEach { option ->
                    val isPlaced = droppedShapes.any { (slotId, dropped) ->
                        slotId == option.shapeId && dropped == option.shapeId
                    }
                    val tileColor = shapeColorMap[option.shapeId] ?: Color.Gray
                    DraggableShapeTile(
                        option         = option,
                        isAnswered     = isAnswered || isPlaced,
                        tileSize       = SHAPE_TILE_SIZE,
                        resetKey       = resetKey,
                        fillColor      = tileColor,
                        hintRotation   = if (option.shapeId == hintShapeId) hintRotation else 0f,
                        onDragMove     = { viewModel.onTouchPoint(it) },
                        onDropped      = { shapeId, dropPx ->
                            val nearest = slotCenters
                                .minByOrNull { (_, c) -> (dropPx - c).getDistance() }
                                ?.takeIf { (_, c) -> (dropPx - c).getDistance() < dropRadiusPx }
                            if (nearest != null) {
                                viewModel.onShapeDroppedToOutline(shapeId, nearest.key)
                            }
                        }
                    )
                }
                // If the row has only 1 item, add a spacer to keep alignment
                if (rowOptions.size < 2) {
                    Spacer(modifier = Modifier.size(SHAPE_TILE_SIZE))
                }
            }
        }
    }
}

// ── 2-column grid of outline drop slots ──────────────────────────────────────
@Composable
private fun ShapeOutlineSlotsGrid(
    outlineSlots    : List<String>,
    droppedShapes   : Map<String, String?>,
    isCorrect       : Boolean,
    wrongDropSlotId : String?,
    isAnswered      : Boolean,
    shapeOptions    : List<ShapeOption>,
    shapeColorMap   : Map<String, Color>,
    slotCenters     : MutableMap<String, Offset>
) {
    val rows = outlineSlots.chunked(2)
    Column(
        modifier            = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        rows.forEach { rowSlots ->
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                rowSlots.forEach { slotId ->
                    ShapeOutlineSlot(
                        slotShapeId    = slotId,
                        droppedShapeId = droppedShapes[slotId],
                        isCorrect      = isCorrect,
                        isWrong        = wrongDropSlotId == slotId,
                        isAnswered     = isAnswered,
                        shapeOptions   = shapeOptions,
                        shapeColorMap  = shapeColorMap,
                        modifier       = Modifier.onGloballyPositioned { coords ->
                            val pos = coords.positionInRoot()
                            val sz  = coords.size
                            slotCenters[slotId] = Offset(
                                pos.x + sz.width  / 2f,
                                pos.y + sz.height / 2f
                            )
                        }
                    )
                }
                // Pad the last row if odd count
                if (rowSlots.size < 2) {
                    Spacer(modifier = Modifier.size(SHAPE_OUTLINE_SLOT_SIZE))
                }
            }
        }
    }
}

// ── Shape outline drop slot ───────────────────────────────────────────────────
/**
 * Renders the outline (ghost/low-alpha) of [slotShapeId] as a drop target.
 * When a shape has been dropped, it shows the filled colored shape image inside.
 * No border is drawn — state is communicated via background tint only.
 */
@Composable
private fun ShapeOutlineSlot(
    slotShapeId    : String,
    droppedShapeId : String?,
    isCorrect      : Boolean,
    isWrong        : Boolean,
    isAnswered     : Boolean,
    shapeOptions   : List<ShapeOption>,
    shapeColorMap  : Map<String, Color>,
    modifier       : Modifier = Modifier
) {
    val colors = LocalGameColorScheme.current
    val bgColor = when {
        isCorrect || (droppedShapeId == slotShapeId) -> colors.correct.copy(alpha = 0.12f)
        isWrong                                       -> colors.wrong.copy(alpha = 0.12f)
        else                                          -> Color.Transparent
    }

    Box(
        modifier = modifier
            .size(SHAPE_OUTLINE_SLOT_SIZE)
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            // No border — deliberately removed
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        if (droppedShapeId != null) {
            // Show the colored filled shape that was dropped
            val droppedOption = shapeOptions.find { it.shapeId == droppedShapeId }
            val droppedColor  = shapeColorMap[droppedShapeId]
            if (droppedOption != null) {
                ColoredShapeImage(
                    option    = droppedOption,
                    fillColor = droppedColor,
                    alpha     = 1f
                )
            }
        } else {
            // Show the ghost/outline of the target shape (low alpha)
            val targetOption = shapeOptions.find { it.shapeId == slotShapeId }
            if (targetOption != null) {
                ColoredShapeImage(
                    option    = targetOption,
                    fillColor = null,   // no tint → renders as-is at low alpha
                    alpha     = 0.22f
                )
            }
        }
    }
}

// ── Draggable shape tile ──────────────────────────────────────────────────────
/**
 * A draggable tile that renders the shape image tinted with [fillColor].
 * No border is drawn.
 */
@Composable
private fun DraggableShapeTile(
    option        : ShapeOption,
    isAnswered    : Boolean,
    tileSize      : Dp,
    resetKey      : Any   = Unit,
    fillColor     : Color? = null,
    hintRotation  : Float = 0f,
    onDragMove    : ((Offset) -> Unit)? = null,
    onDropped     : (shapeId: String, dropPx: Offset) -> Unit
) {
    var offsetX    by remember(resetKey) { mutableFloatStateOf(0f) }
    var offsetY    by remember(resetKey) { mutableFloatStateOf(0f) }
    var isDragging by remember(resetKey) { mutableStateOf(false) }
    var rootPos    by remember { mutableStateOf(Offset.Zero) }
    var sizePx     by remember { mutableStateOf(Offset.Zero) }

    val scale by animateFloatAsState(
        targetValue   = if (isDragging) DRAG_SCALE_ACTIVE else DRAG_SCALE_DEFAULT,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label         = "shapeTileScale"
    )

    Box(
        modifier = Modifier
            .size(tileSize)
            .zIndex(if (isDragging) 10f else 1f)
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .graphicsLayer {
                scaleX    = scale
                scaleY    = scale
                alpha     = if (isAnswered) 0.4f else 1f
                rotationZ = hintRotation
            }
            .shadow(if (isDragging) 10.dp else 3.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            // No border — background is the fill color itself for a clean look
            .background(fillColor?.copy(alpha = 0.18f) ?: Color.Transparent)
            .onGloballyPositioned { coords ->
                if (!isDragging) {
                    rootPos = coords.positionInRoot()
                    sizePx  = Offset(coords.size.width.toFloat(), coords.size.height.toFloat())
                }
            }
            .pointerInput(isAnswered, resetKey) {
                if (isAnswered) return@pointerInput
                detectDragGestures(
                    onDragStart  = { isDragging = true },
                    onDrag       = { change, drag ->
                        change.consume()
                        offsetX += drag.x
                        offsetY += drag.y
                        val cx = rootPos.x + sizePx.x / 2f + offsetX
                        val cy = rootPos.y + sizePx.y / 2f + offsetY
                        onDragMove?.invoke(Offset(cx, cy))
                    },
                    onDragEnd    = {
                        onDropped(
                            option.shapeId,
                            Offset(
                                rootPos.x + sizePx.x / 2f + offsetX,
                                rootPos.y + sizePx.y / 2f + offsetY
                            )
                        )
                        offsetX = 0f; offsetY = 0f; isDragging = false
                    },
                    onDragCancel = { offsetX = 0f; offsetY = 0f; isDragging = false }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        ColoredShapeImage(
            option    = option,
            fillColor = fillColor,
            alpha     = 1f,
            modifier  = Modifier.fillMaxSize().padding(12.dp)
        )
    }
}

// ── Colored shape image helper ────────────────────────────────────────────────
/**
 * Renders a shape's [DragImage] with an optional color tint applied via
 * [ColorFilter.tint]. When [fillColor] is null the image renders untinted.
 * [alpha] controls the overall opacity (used for the ghost/outline effect).
 */
@Composable
private fun ColoredShapeImage(
    option   : ShapeOption,
    fillColor: Color?,
    alpha    : Float,
    modifier : Modifier = Modifier.fillMaxSize()
) {
    val colors  = LocalGameColorScheme.current
    val context = LocalContext.current

    Box(
        modifier         = modifier.graphicsLayer { this.alpha = alpha },
        contentAlignment = Alignment.Center
    ) {
        when (val asset = option.drawableImage) {
            is ImageAsset.PngAsset -> {
                AsyncImage(
                    model              = ImageRequest.Builder(context)
                        .data(AssetPathResolver.androidAssetUri(asset.path))
                        .crossfade(true)
                        .build(),
                    contentDescription = option.labelAr,
                    modifier           = Modifier.fillMaxSize(),
                    contentScale       = ContentScale.Fit,
                    colorFilter        = fillColor?.let { ColorFilter.tint(it) }
                )
            }
            is ImageAsset.SvgDrawable -> {
                val drawableId = context.resources.getIdentifier(
                    asset.drawableName, "drawable", context.packageName
                )
                if (drawableId != 0) {
                    Image(
                        painter            = painterResource(id = drawableId),
                        contentDescription = option.labelAr,
                        modifier           = Modifier.fillMaxSize(),
                        contentScale       = ContentScale.Fit,
                        colorFilter        = fillColor?.let { ColorFilter.tint(it) }
                    )
                } else {
                    Box(
                        modifier         = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background((fillColor ?: colors.accent).copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text      = option.labelAr,
                            style     = MaterialTheme.typography.bodyMedium,
                            color     = fillColor ?: colors.accent,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun buildShapePath(size: Size, shapeId: String): Path {
    val cx = size.width  / 2f
    val cy = size.height / 2f
    val r  = minOf(size.width, size.height) / 2f * 0.85f
    return Path().apply {
        when {
            shapeId.endsWith("square",    ignoreCase = true) ->
                addRect(Rect(cx - r, cy - r, cx + r, cy + r))
            shapeId.endsWith("triangle",  ignoreCase = true) -> {
                moveTo(cx, cy - r)
                lineTo(cx + r, cy + r)
                lineTo(cx - r, cy + r)
                close()
            }
            shapeId.endsWith("rectangle", ignoreCase = true) ->
                addRect(Rect(cx - r, cy - r * 0.55f, cx + r, cy + r * 0.55f))
            else ->
                addOval(Rect(Offset(cx, cy), r))
        }
    }
}

@Composable
private fun DragImage(
    asset   : ImageAsset,
    label   : String,
    modifier: Modifier = Modifier
) {
    val colors  = LocalGameColorScheme.current
    val context = LocalContext.current
    when (asset) {
        is ImageAsset.PngAsset -> {
            AsyncImage(
                model              = ImageRequest.Builder(context)
                    .data(AssetPathResolver.androidAssetUri(asset.path))
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
                        .background(colors.accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text      = label,
                        style     = MaterialTheme.typography.bodyMedium,
                        color     = colors.accent,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}