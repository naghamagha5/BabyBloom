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
import androidx.compose.ui.text.style.TextDirection
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
import com.babybloom.ui.theme.TraceBadgeBorder
import com.babybloom.ui.theme.TraceBadgeText
import com.babybloom.ui.theme.WarmPeach
import com.babybloom.ui.theme.dragColorForContentId
import com.babybloom.util.AssetPathResolver
import com.babybloom.util.ImageAsset
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// ── Layout constants ──────────────────────────────────────────────────────────
private val DROP_RADIUS_DP              = 90.dp
private val CAGE_DROP_RADIUS_FACTOR     = 1.8f
private val COLOR_GAME_SHAPE_SIZE              = 220.dp
private val COLOR_GAME_PEN_BOX_H               = 110.dp
private val COLOR_GAME_SWATCH_H                = 96.dp
private val COLOR_GAME_SWATCH_H_TEST           = 114.dp
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
private val CAGE_IN_CAGE_IMG_MAX           = 120.dp
private val CAGE_IN_CAGE_IMG_MIN           = 50.dp
private val CAGE_INSTRUCTION_SIZE_SP       = 18
private val CAGE_X_CENTER_ADJUST           = (-18).dp
private val ATTEMPT_DOT_SIZE               = 12.dp

// Shape game — bigger tiles
private val SHAPE_TILE_SIZE                = 180.dp
private val SHAPE_OUTLINE_SLOT_SIZE        = 190.dp
private val SHAPE_DROP_RADIUS_DP           = 115.dp
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

// Hand hint animation constants
private const val HAND_HINT_CYCLE_MS       = 2600
private const val HAND_HINT_PAUSE_MS       = 1_000L

// ── Pen tip offset (from center to tip in local space, at -25deg rotation) ──
private const val PEN_TIP_X_FRACTION = -0.36f
private const val PEN_TIP_Y_FRACTION =  0.40f

// ── Shape-game color palettes ─────────────────────────────────────────────────
private val calmShapeSwatches = listOf(
    GameCalmSwatch1, GameCalmSwatch2, GameCalmSwatch3, GameCalmSwatch4, GameCalmSwatch5
)
private val activeShapeSwatches = listOf(
    GameActiveSwatch1, GameActiveSwatch2, GameActiveSwatch3, GameActiveSwatch4, GameActiveSwatch5
)

private fun shapeColorForIndex(index: Int, isCalmMode: Boolean): Color {
    val swatches = if (isCalmMode) calmShapeSwatches else activeShapeSwatches
    return swatches[index % swatches.size]
}

// ─────────────────────────────────────────────────────────────────────────────
// Instruction badge
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun DragInstructionBadge(text: String, accentColor: Color = TraceBadgeText) {
    Box(
        modifier = Modifier
            .border(1.5.dp, TraceBadgeBorder, RoundedCornerShape(50))
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text       = text,
                fontSize   = 17.sp,
                fontWeight = FontWeight.Bold,
                color      = accentColor,
                maxLines   = 1,
                style      = LocalTextStyle.current.copy(textDirection = TextDirection.Rtl)
            )
            Spacer(Modifier.width(8.dp))
            Image(
                painter            = painterResource(R.drawable.front_hand),
                contentDescription = null,
                modifier           = Modifier.size(26.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Color name badge for test layout
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ColorNameBadge(label: String, colorId: String) {
    val displayColor = dragColorForContentId(colorId)
    val isNearWhite  = displayColor.red   > 0.92f &&
            displayColor.green > 0.92f &&
            displayColor.blue  > 0.92f
    val badgeBg   = if (isNearWhite) Color(0xFF2C2C2E) else Color.White
    val textColor = if (isNearWhite) Color.White       else displayColor

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(badgeBg)
            .border(2.dp, displayColor.copy(alpha = 0.55f), RoundedCornerShape(50))
            .padding(horizontal = 28.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text       = label,
            fontSize   = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color      = textColor,
            textAlign  = TextAlign.Center,
            style      = LocalTextStyle.current.copy(textDirection = TextDirection.Rtl)
        )
    }
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

// ── Attempts row ──────────────────────────────────────────────────────────────
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
    val density   = LocalDensity.current
    val resetKey  = "${state.correctId}_${state.resetTrigger}"
    val strokeKey = "${resetKey}_${state.attemptsUsed}"

    var shapeCenterPx   by remember { mutableStateOf(Offset.Zero) }
    var shapeHalfSizePx by remember { mutableFloatStateOf(0f) }
    var boxTopLeftPx    by remember { mutableStateOf(Offset.Zero) }

    val penStrokePts = remember(strokeKey) { mutableStateListOf<Offset>() }

    val localPenColorIdState   = remember(strokeKey) { mutableStateOf<String?>(null) }
    val lastPickedColorIdState = remember(strokeKey) { mutableStateOf<String?>(null) }
    var localPenColorId   by localPenColorIdState
    var lastPickedColorId by lastPickedColorIdState

    // Pen must touch the color box before it can paint the shape
    val penHasVisitedColorBox = remember(strokeKey) { mutableStateOf(false) }

    val shakeAnim = remember { Animatable(0f) }
    LaunchedEffect(state.attemptsUsed) {
        if (state.attemptsUsed > 0 && !state.isCorrect) {
            shakeAnim.snapTo(0f)
            shakeAnim.animateTo(0f, animationSpec = keyframes {
                durationMillis = SHAKE_ANIM_DURATION_MS
                (-22f) at 60; 22f at 120; (-16f) at 200; 16f at 280; (-8f) at 360; 0f at SHAKE_ANIM_DURATION_MS
            })
        }
    }

    val hintAnim = remember { Animatable(0f) }
    LaunchedEffect(state.hintColorId, state.isAnswered) {
        if (state.hintColorId != null && !state.isAnswered) {
            while (true) {
                hintAnim.animateTo(0f, animationSpec = keyframes {
                    durationMillis = HINT_WOBBLE_DURATION_MS
                    (-6f) at 100; 6f at 250; (-4f) at 380; 4f at 490; (-2f) at 580; 0f at HINT_WOBBLE_DURATION_MS
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

    if (state.isTest) {
        TestColorLayoutV2(
            state                  = state,
            strokeKey              = strokeKey,
            shapeSize              = COLOR_GAME_SHAPE_SIZE,
            penStrokePts           = penStrokePts,
            activeColor            = activeColor,
            hintAnim               = hintAnim,
            shakeAnim              = shakeAnim,
            viewModel              = viewModel,
            lastPickedColorIdState = lastPickedColorIdState,
            onLocalColorId         = { newId ->
                localPenColorId = newId
                if (newId != null) lastPickedColorId = newId
            }
        )
    } else {
        // ── Learning layout ──────────────────────────────────────────────────
        // The pen rests centered on the shape. Because the pen Box is always
        // positioned so its center coincides with shapeCenterPx (by construction
        // of the offset calculation below), we do NOT need a separate
        // penRestCenter state variable — shapeCenterPx IS the rest center.
        // Using shapeCenterPx directly avoids a race-condition where
        // onGloballyPositioned fires before shapeCenterPx is resolved on the
        // first composition, capturing a garbage (but non-zero) coordinate that
        // prevents the hint loop from waiting properly.

        var swatchCenter       by remember { mutableStateOf(Offset.Zero) }
        var hintFromCenter     by remember { mutableStateOf(Offset.Zero) }
        var hintToCenter       by remember { mutableStateOf(Offset.Zero) }
        val handProgress       = remember { Animatable(0f) }
        var isColorPenDragging by remember { mutableStateOf(false) }

        // Track the outer Box's root origin so we can convert shapeCenterPx
        // (which is in root coords) into an offset local to this Box.
        var outerBoxOriginPx   by remember { mutableStateOf(Offset.Zero) }

        // ── Hand hint: pen resting pos (= shapeCenterPx) → color swatch ─────
        // Keys: hintColorId (starts/restarts the hint) + isAnswered (cancels).
        // Loops until the question is answered or the user starts dragging.
        // We wait until shapeCenterPx and swatchCenter are both non-zero so
        // both layout anchors are fully resolved before kicking off animation.
        LaunchedEffect(state.hintColorId, state.isAnswered) {
            if (state.hintColorId != null && !state.isAnswered) {
                // Wait for the shape canvas and the swatch tile to be measured.
                while (shapeCenterPx == Offset.Zero || swatchCenter == Offset.Zero) {
                    delay(16L)
                }
                while (true) {
                    // Snapshot each cycle: shapeCenterPx can shift as the layout
                    // settles (e.g. first-frame offset delta), so we always read
                    // the freshest value rather than capturing it once up-front.
                    hintFromCenter = shapeCenterPx
                    hintToCenter   = swatchCenter
                    handProgress.snapTo(0f)
                    handProgress.animateTo(
                        targetValue   = 1f,
                        animationSpec = tween(
                            durationMillis = HAND_HINT_CYCLE_MS,
                            easing         = EaseInOutCubic
                        )
                    )
                    delay(HAND_HINT_PAUSE_MS)
                }
            } else {
                // Cancel any in-flight animation immediately.
                handProgress.snapTo(0f)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords ->
                    outerBoxOriginPx = coords.positionInRoot()
                }
        ) {

            Column(
                modifier            = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Instruction badge — pinned at top
                DragInstructionBadge(text = stringResource(R.string.drag_color_instruction))

                Spacer(Modifier.weight(0.55f))

                // Shape canvas
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
                    ColorShapeCanvas(
                        state        = state,
                        penStrokePts = penStrokePts,
                        activeColor  = if (penHasVisitedColorBox.value) activeColor else null,
                        colors       = colors
                    )
                }

                Spacer(Modifier.height(28.dp))

                val option = state.colorOptions.firstOrNull()
                if (option != null) {
                    // Color swatch tile — capture its center for the hand hint end-point
                    Box(
                        modifier = Modifier.onGloballyPositioned { coords ->
                            val pos = coords.positionInRoot()
                            val sz  = coords.size
                            swatchCenter = Offset(pos.x + sz.width / 2f, pos.y + sz.height / 2f)
                        },
                        contentAlignment = Alignment.Center
                    ) {
                        ColorSwatchTile(
                            option     = option,
                            size       = COLOR_GAME_LEARNING_SWATCH_H,
                            isAnswered = state.isAnswered
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    ColorNameBadge(
                        label   = state.currentLabel,
                        colorId = option.colorId
                    )
                }

                Spacer(Modifier.weight(1.45f))
            }

            // ── Pen overlay ──────────────────────────────────────────────────
            // Anchored to TopStart and offset so the pen box center lands exactly
            // on shapeCenterPx. No separate onGloballyPositioned needed here —
            // shapeCenterPx already IS the pen rest center by construction.
            val option = state.colorOptions.firstOrNull()
            if (option != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset {
                            val penBoxPx = COLOR_GAME_PEN_BOX_H.toPx()
                            IntOffset(
                                x = (shapeCenterPx.x - outerBoxOriginPx.x - penBoxPx / 2f).roundToInt(),
                                y = (shapeCenterPx.y - outerBoxOriginPx.y - penBoxPx / 2f).roundToInt(),
                            )
                        }
                ) {
                    DraggablePen(
                        colorOption    = option,
                        penBoxDp       = COLOR_GAME_PEN_BOX_H,
                        isAnswered     = state.isAnswered,
                        resetKey       = strokeKey,
                        fillColor      = if (penHasVisitedColorBox.value) activeColor else null,
                        // Wobble hint is suppressed while dragging so rotation
                        // doesn't fight the finger position.
                        hintRotation   = if (state.hintColorId != null && !isColorPenDragging)
                            hintAnim.value else 0f,
                        onDragStarted  = {
                            isColorPenDragging = true
                            viewModel.onColorPickedUp(option.colorId)
                        },
                        onDragFinished = {
                            isColorPenDragging = false
                            viewModel.onPenDragReleased()
                        },
                        onPenMove      = { fingerPx, drag ->
                            viewModel.onTouchPoint(fingerPx)

                            if (!penHasVisitedColorBox.value && swatchCenter != Offset.Zero) {
                                val swatchRadiusPx = with(density) {
                                    (COLOR_GAME_LEARNING_SWATCH_H / 2f).toPx() * 1.3f
                                }
                                if ((fingerPx - swatchCenter).getDistance() < swatchRadiusPx) {
                                    penHasVisitedColorBox.value = true
                                    viewModel.onColorPickedUp(option.colorId)
                                }
                            }

                            if (penHasVisitedColorBox.value &&
                                !state.isAnswered && state.fillProgress < 1f &&
                                shapeCenterPx != Offset.Zero &&
                                (fingerPx - shapeCenterPx).getDistance() < shapeHalfSizePx * 1.4f
                            ) {
                                penStrokePts.add(fingerPx - boxTopLeftPx)
                                viewModel.onPenMovedOverShape(drag.getDistance(), option.colorId)
                            }
                        }
                    )
                }
            }

            // ── Hand hint overlay ────────────────────────────────────────────
            // Shown above everything (zIndex = 5 inside HandHintOverlay).
            // Conditions:
            //   • hintColorId set by the ViewModel
            //   • question still open
            //   • both anchor points are resolved (non-Zero)
            //   • user is NOT currently dragging
            if (state.hintColorId != null && !state.isAnswered &&
                hintFromCenter != Offset.Zero && hintToCenter != Offset.Zero &&
                !isColorPenDragging
            ) {
                HandHintOverlay(
                    progress    = handProgress.value,
                    startOffset = hintFromCenter - outerBoxOriginPx,
                    endOffset   = hintToCenter - outerBoxOriginPx
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Color shape canvas (shared between learning and test layouts)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ColorShapeCanvas(
    state       : DragGameState,
    penStrokePts: List<Offset>,
    activeColor : Color?,
    colors      : com.babybloom.ui.theme.GameColorScheme
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

// ─────────────────────────────────────────────────────────────────────────────
// Test colour layout V2
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun TestColorLayoutV2(
    state                  : DragGameState,
    strokeKey              : Any,
    shapeSize              : Dp,
    penStrokePts           : androidx.compose.runtime.snapshots.SnapshotStateList<Offset>,
    activeColor            : Color?,
    hintAnim               : Animatable<Float, AnimationVector1D>,
    shakeAnim              : Animatable<Float, AnimationVector1D>,
    viewModel              : DragGameViewModel,
    lastPickedColorIdState : MutableState<String?>,
    onLocalColorId         : (String?) -> Unit
) {
    val colors       = LocalGameColorScheme.current
    val density      = LocalDensity.current
    val pickRadiusPx = with(density) { COLOR_PICK_RADIUS_DP.toPx() }

    val swatchCenters = remember(strokeKey) { mutableStateMapOf<String, Offset>() }
    var shapeCenterPx   by remember(strokeKey) { mutableStateOf(Offset.Zero) }
    var shapeHalfSizePx by remember(strokeKey) { mutableFloatStateOf(0f) }
    var boxTopLeftPx    by remember(strokeKey) { mutableStateOf(Offset.Zero) }

    Column(
        modifier            = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DragInstructionBadge(text = stringResource(R.string.drag_color_instruction))
        Spacer(Modifier.height(6.dp))

        ColorNameBadge(
            label   = state.currentLabel,
            colorId = state.correctId
        )
        Spacer(Modifier.height(4.dp))

        Box(
            modifier         = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center
        ) {
            val swatchSize = COLOR_GAME_SWATCH_H_TEST
            val edgePad    = 8.dp
            val vertInset  = 38.dp

            val cornerAlignments = listOf(
                Alignment.TopStart,
                Alignment.TopEnd,
                Alignment.BottomStart,
                Alignment.BottomEnd
            )

            for ((idx, option) in state.colorOptions.take(4).withIndex()) {
                val alignment = cornerAlignments[idx]
                val isTop    = alignment == Alignment.TopStart    || alignment == Alignment.TopEnd
                val isBottom = alignment == Alignment.BottomStart || alignment == Alignment.BottomEnd
                val isLeft   = alignment == Alignment.TopStart    || alignment == Alignment.BottomStart
                val isRight  = alignment == Alignment.TopEnd      || alignment == Alignment.BottomEnd

                Box(
                    modifier = Modifier
                        .align(alignment)
                        .padding(
                            start  = if (isLeft)   edgePad   else 0.dp,
                            end    = if (isRight)  edgePad   else 0.dp,
                            top    = if (isTop)    vertInset else 0.dp,
                            bottom = if (isBottom) vertInset else 0.dp,
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
                        size       = swatchSize,
                        isAnswered = state.isAnswered
                    )
                }
            }

            // Shape in the center
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(shapeSize)
                    .graphicsLayer { translationX = shakeAnim.value }
                    .onGloballyPositioned { coords ->
                        val pos = coords.positionInRoot()
                        val sz  = coords.size
                        shapeCenterPx   = Offset(pos.x + sz.width / 2f, pos.y + sz.height / 2f)
                        shapeHalfSizePx = sz.width / 2f
                        boxTopLeftPx    = pos
                    },
                contentAlignment = Alignment.Center
            ) {
                ColorShapeCanvas(
                    state        = state,
                    penStrokePts = penStrokePts,
                    activeColor  = activeColor,
                    colors       = colors
                )
            }

            val representativeOption = state.colorOptions.firstOrNull() ?: return@Box

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

                        if (resolvedColorId != null && !state.isAnswered && state.fillProgress < 1f &&
                            shapeCenterPx != Offset.Zero &&
                            (fingerPx - shapeCenterPx).getDistance() < shapeHalfSizePx * 1.4f
                        ) {
                            penStrokePts.add(fingerPx - boxTopLeftPx)
                            viewModel.onPenMovedOverShape(drag.getDistance(), resolvedColorId)
                        }
                    }
                )
            }
        }
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
    var offsetX    by remember(resetKey) { mutableStateOf(0f) }
    var offsetY    by remember(resetKey) { mutableStateOf(0f) }
    var isDragging by remember(resetKey) { mutableStateOf(false) }
    var rootPos    by remember { mutableStateOf(Offset.Zero) }

    val density = LocalDensity.current

    val scale = if (isDragging) PEN_DRAG_SCALE else PEN_DRAG_SCALE_DEFAULT

    fun tipOffsetPx(penBoxPx: Float): Offset =
        Offset(
            PEN_TIP_X_FRACTION * penBoxPx * PEN_DRAG_SCALE,
            PEN_TIP_Y_FRACTION * penBoxPx * PEN_DRAG_SCALE
        )

    fun offsetForPointer(localPointer: Offset, penBoxPx: Float): Offset {
        val tipOffset = tipOffsetPx(penBoxPx)
        return Offset(
            x = localPointer.x - penBoxPx / 2f - tipOffset.x,
            y = localPointer.y - penBoxPx / 2f - tipOffset.y
        )
    }

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
                    onDragStart  = { localPointer ->
                        onDragStarted()
                        isDragging = true
                        val penBoxPx = with(density) { penBoxDp.toPx() }
                        val anchoredOffset = offsetForPointer(localPointer, penBoxPx)
                        offsetX = anchoredOffset.x
                        offsetY = anchoredOffset.y
                    },
                    onDrag       = { change, drag ->
                        change.consume()
                        val penBoxPx = with(density) { penBoxDp.toPx() }
                        offsetX += drag.x
                        offsetY += drag.y

                        val tipOffset = tipOffsetPx(penBoxPx)
                        val tipRootPx = Offset(
                            x = rootPos.x + penBoxPx / 2f + tipOffset.x + offsetX,
                            y = rootPos.y + penBoxPx / 2f + tipOffset.y + offsetY
                        )
                        onPenMove(tipRootPx, drag)
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
                (-22f) at 60; 22f at 120; (-16f) at 200; 16f at 280; 0f at SHAKE_ANIM_DURATION_MS
            })
        }
    }

    val hintAnim = remember { Animatable(0f) }
    LaunchedEffect(state.hintLetterId, state.isAnswered) {
        if (state.hintLetterId != null && !state.isAnswered) {
            if (state.isTest) {
                hintAnim.animateTo(0f, animationSpec = keyframes {
                    durationMillis = 4_000
                    (-6f)  at  300;  6f  at  700
                    (-5f)  at 1_100;  5f  at 1_500
                    (-4f)  at 1_900;  4f  at 2_300
                    (-3f)  at 2_700;  3f  at 3_100
                    (-2f)  at 3_400;  2f  at 3_700
                    0f    at 4_000
                })
            } else {
                while (true) {
                    hintAnim.animateTo(0f, animationSpec = keyframes {
                        durationMillis = HINT_WOBBLE_DURATION_MS
                        (-6f) at 100; 6f at 250; (-4f) at 380; 4f at 490; (-2f) at 580; 0f at HINT_WOBBLE_DURATION_MS
                    })
                    delay(HINT_WOBBLE_PAUSE_MS)
                }
            }
        } else {
            hintAnim.snapTo(0f)
        }
    }

    var tileCenter     by remember { mutableStateOf(Offset.Zero) }
    var hintFromCenter by remember { mutableStateOf(Offset.Zero) }
    var hintToCenter   by remember { mutableStateOf(Offset.Zero) }
    val handProgress   = remember { Animatable(0f) }
    var isLetterDragging by remember { mutableStateOf(false) }

    LaunchedEffect(state.hintLetterId, state.isAnswered) {
        if (!state.isTest && state.hintLetterId != null && !state.isAnswered) {
            while (tileCenter == Offset.Zero || gapCenterPx == Offset.Zero) {
                delay(16L)
            }
            while (true) {
                hintFromCenter = tileCenter
                hintToCenter   = gapCenterPx
                handProgress.snapTo(0f)
                handProgress.animateTo(
                    1f,
                    animationSpec = tween(durationMillis = HAND_HINT_CYCLE_MS, easing = EaseInOutCubic)
                )
                delay(HAND_HINT_PAUSE_MS)
            }
        } else {
            handProgress.snapTo(0f)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        Column(
            modifier            = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DragInstructionBadge(text = stringResource(R.string.drag_letter_instruction))

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

                val correctOption = if (!state.isTest)
                    state.letterOptions.firstOrNull { it.isCorrect } ?: state.letterOptions.firstOrNull()
                else null

                for (letterOption in state.letterOptions) {
                    val isCorrectTile = !state.isTest && letterOption == correctOption
                    Box(
                        modifier = Modifier
                            .size(touchSize)
                            .then(
                                if (isCorrectTile) Modifier.onGloballyPositioned { coords ->
                                    val pos = coords.positionInRoot()
                                    val sz  = coords.size
                                    tileCenter = Offset(pos.x + sz.width / 2f, pos.y + sz.height / 2f)
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        DraggableLetterTile(
                            option         = letterOption,
                            isAnswered     = state.isAnswered,
                            tileSize       = tileSize,
                            resetKey       = resetKey,
                            hintRotation   = if (letterOption.letterId == state.hintLetterId) hintAnim.value else 0f,
                            onDragStarted  = {
                                isLetterDragging = true
                                viewModel.onLetterTileDragStarted(letterOption.letterId)
                            },
                            onDragFinished = {
                                isLetterDragging = false
                                viewModel.onLetterTileDragReleased()
                            },
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

        if (!state.isTest && state.hintLetterId != null && !state.isAnswered &&
            hintFromCenter != Offset.Zero && hintToCenter != Offset.Zero &&
            !isLetterDragging
        ) {
            HandHintOverlay(
                progress    = handProgress.value,
                startOffset = hintFromCenter,
                endOffset   = hintToCenter
            )
        }
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
    val density      = LocalDensity.current
    val dropRadiusPx = with(density) { DROP_RADIUS_DP.toPx() * CAGE_DROP_RADIUS_FACTOR }

    val cageCenterPx = remember { mutableStateOf(Offset.Zero) }

    val shakeAnim = remember { Animatable(0f) }
    LaunchedEffect(state.rejectIdx) {
        if (state.rejectIdx >= 0) {
            shakeAnim.snapTo(0f)
            shakeAnim.animateTo(0f, animationSpec = keyframes {
                durationMillis = REJECT_ANIM_DURATION_MS
                (-18f) at 50; 18f at 110; (-12f) at 180; 12f at 250; 0f at REJECT_ANIM_DURATION_MS
            })
        }
    }

    var firstAnimalCenter by remember { mutableStateOf(Offset.Zero) }
    var hintFromCenter    by remember { mutableStateOf(Offset.Zero) }
    var hintToCenter      by remember { mutableStateOf(Offset.Zero) }
    val handProgress      = remember { Animatable(0f) }
    var isAnimalDragging  by remember { mutableStateOf(false) }

    val showCageHint = !state.isTest && !state.isAnswered && state.inCageSet.isEmpty()

    LaunchedEffect(showCageHint) {
        if (showCageHint) {
            while (firstAnimalCenter == Offset.Zero || cageCenterPx.value == Offset.Zero) {
                delay(16L)
            }
            delay(5_000L)
            while (!state.isAnswered && state.inCageSet.isEmpty()) {
                hintFromCenter = firstAnimalCenter
                hintToCenter   = cageCenterPx.value
                handProgress.snapTo(0f)
                handProgress.animateTo(
                    1f,
                    animationSpec = tween(durationMillis = HAND_HINT_CYCLE_MS, easing = EaseInOutCubic)
                )
                delay(HAND_HINT_PAUSE_MS)
            }
        } else {
            handProgress.snapTo(0f)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        Column(
            modifier            = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier         = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                DragInstructionBadge(text = state.instructionText)
            }

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
                    for (row in rows) {
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally),
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            for ((idx, animal) in row) {
                                val isFirstAnimal = !state.isTest && idx == 0

                                Box(
                                    modifier = if (isFirstAnimal) {
                                        Modifier.onGloballyPositioned { coords ->
                                            val pos = coords.positionInRoot()
                                            val sz  = coords.size
                                            firstAnimalCenter = Offset(
                                                pos.x + sz.width / 2f,
                                                pos.y + sz.height / 2f
                                            )
                                        }
                                    } else Modifier
                                ) {
                                    DraggablePoolAnimal(
                                        animal        = animal,
                                        isInCage      = idx in state.inCageSet,
                                        isRejecting   = idx == state.rejectIdx,
                                        isAnswered    = state.isAnswered,
                                        animalSize    = animalSz,
                                        onDragStarted = {
                                            isAnimalDragging = true
                                            viewModel.onCageAnimalDragStarted(idx)
                                        },
                                        onDragMove    = { pos -> viewModel.onTouchPoint(pos) },
                                        onDropped     = { dropPx ->
                                            isAnimalDragging = false
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
                        for (animal in cageAnimals) {
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

        if (showCageHint && !isAnimalDragging &&
            hintFromCenter != Offset.Zero && hintToCenter != Offset.Zero
        ) {
            HandHintOverlay(
                progress    = handProgress.value,
                startOffset = hintFromCenter,
                endOffset   = hintToCenter
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
                (-15f) at 50; 15f at 110; (-10f) at 170; 10f at 230; 0f at REJECT_ANIM_DURATION_MS
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

    val slotCenters = remember(resetKey) { mutableStateMapOf<String, Offset>() }

    val shapeColorMap = remember(state.shapeOptions, isCalmMode) {
        state.shapeOptions.mapIndexed { idx, option ->
            option.shapeId to shapeColorForIndex(idx, isCalmMode)
        }.toMap()
    }

    var isShapeDragging by remember { mutableStateOf(false) }

    val shakeAnim = remember { Animatable(0f) }
    LaunchedEffect(state.attemptsUsed) {
        if (state.attemptsUsed > 0 && !state.isCorrect) {
            shakeAnim.snapTo(0f)
            shakeAnim.animateTo(0f, animationSpec = keyframes {
                durationMillis = SHAKE_ANIM_DURATION_MS
                (-22f) at 60; 22f at 120; (-16f) at 200; 16f at 280; 0f at SHAKE_ANIM_DURATION_MS
            })
        }
    }

    val hintAnim = remember { Animatable(0f) }
    LaunchedEffect(state.hintShapeId, state.isAnswered) {
        if (state.hintShapeId != null && !state.isAnswered) {
            while (true) {
                hintAnim.animateTo(0f, animationSpec = keyframes {
                    durationMillis = HINT_WOBBLE_DURATION_MS
                    (-6f) at 100; 6f at 250; (-4f) at 380; 4f at 490; (-2f) at 580; 0f at HINT_WOBBLE_DURATION_MS
                })
                delay(HINT_WOBBLE_PAUSE_MS)
            }
        } else {
            hintAnim.snapTo(0f)
        }
    }

    val handProgress = remember { Animatable(0f) }
    var tileCenter by remember { mutableStateOf(Offset.Zero) }
    var slotCenter by remember { mutableStateOf(Offset.Zero) }
    var hintFromCenter by remember { mutableStateOf(Offset.Zero) }
    var hintToCenter   by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(state.hintShapeId, state.isAnswered) {
        if (!state.isTest && state.hintShapeId != null && !state.isAnswered) {
            while (tileCenter == Offset.Zero || slotCenters.isEmpty()) {
                delay(16L)
            }
            while (true) {
                hintFromCenter = tileCenter
                hintToCenter   = slotCenters.values.firstOrNull() ?: slotCenter
                handProgress.snapTo(0f)
                handProgress.animateTo(
                    1f,
                    animationSpec = tween(durationMillis = HAND_HINT_CYCLE_MS, easing = EaseInOutCubic)
                )
                delay(HAND_HINT_PAUSE_MS)
            }
        } else {
            handProgress.snapTo(0f)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .graphicsLayer { translationX = shakeAnim.value }
    ) {
        Column(
            modifier            = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DragInstructionBadge(text = stringResource(R.string.drag_shape_instruction))

            if (!state.isTest) {
                val targetSlotId = state.outlineSlots.firstOrNull() ?: return@Column
                val tileOption   = state.shapeOptions.firstOrNull() ?: return@Column
                val tileColor    = shapeColorMap[tileOption.shapeId] ?: shapeColorForIndex(0, isCalmMode)

                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    DraggableShapeTile(
                        option         = tileOption,
                        isAnswered     = state.isAnswered,
                        tileSize       = SHAPE_TILE_SIZE,
                        resetKey       = resetKey,
                        fillColor      = tileColor,
                        hintRotation   = if (tileOption.shapeId == state.hintShapeId) hintAnim.value else 0f,
                        onDragMove     = { viewModel.onTouchPoint(it) },
                        onDragStarted  = { isShapeDragging = true },
                        onDragEnded    = { isShapeDragging = false },
                        onPositioned   = { center -> tileCenter = center },
                        onDropped      = { shapeId, dropPx ->
                            val nearest = slotCenters
                                .minByOrNull { (_, c) -> (dropPx - c).getDistance() }
                                ?.takeIf { (_, c) -> (dropPx - c).getDistance() < dropRadiusPx }
                            if (nearest != null) viewModel.onShapeDroppedToOutline(shapeId, nearest.key)
                        }
                    )
                }

                Spacer(Modifier.weight(1f))

                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
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
                            val center = Offset(pos.x + sz.width / 2f, pos.y + sz.height / 2f)
                            slotCenters[targetSlotId] = center
                            slotCenter = center
                        }
                    )
                }

            } else {
                val tileOption = state.shapeOptions.firstOrNull() ?: return@Column
                val tileColor  = shapeColorMap[tileOption.shapeId] ?: shapeColorForIndex(0, isCalmMode)

                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    DraggableShapeTile(
                        option       = tileOption,
                        isAnswered   = state.isAnswered,
                        tileSize     = SHAPE_TILE_SIZE,
                        resetKey     = resetKey,
                        fillColor    = tileColor,
                        hintRotation = if (tileOption.shapeId == state.hintShapeId) hintAnim.value else 0f,
                        onDragMove   = { viewModel.onTouchPoint(it) },
                        onDragStarted = { isShapeDragging = true },
                        onDragEnded   = { isShapeDragging = false },
                        onDropped    = { shapeId, dropPx ->
                            val nearest = slotCenters
                                .minByOrNull { (_, c) -> (dropPx - c).getDistance() }
                                ?.takeIf { (_, c) -> (dropPx - c).getDistance() < dropRadiusPx }
                            if (nearest != null) viewModel.onShapeDroppedToOutline(shapeId, nearest.key)
                        }
                    )
                }

                Spacer(Modifier.weight(1f))

                ShapeOutlineSlotsTriangle(
                    outlineSlots    = state.outlineSlots,
                    droppedShapes   = state.droppedShapes,
                    isCorrect       = state.isCorrect,
                    wrongDropSlotId = state.wrongDropSlotId,
                    isAnswered      = state.isAnswered,
                    shapeOptions    = state.shapeOptions,
                    shapeColorMap   = shapeColorMap,
                    slotCenters     = slotCenters
                )
            }
        }

        if (!state.isTest && state.hintShapeId != null && !state.isAnswered &&
            hintFromCenter != Offset.Zero && hintToCenter != Offset.Zero &&
            !isShapeDragging
        ) {
            HandHintOverlay(
                progress    = handProgress.value,
                startOffset = hintFromCenter,
                endOffset   = hintToCenter
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3-slot triangle layout for test shape game
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ShapeOutlineSlotsTriangle(
    outlineSlots    : List<String>,
    droppedShapes   : Map<String, String?>,
    isCorrect       : Boolean,
    wrongDropSlotId : String?,
    isAnswered      : Boolean,
    shapeOptions    : List<ShapeOption>,
    shapeColorMap   : Map<String, Color>,
    slotCenters     : MutableMap<String, Offset>
) {
    val slots = outlineSlots.take(3)
    Column(
        modifier            = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (slots.isNotEmpty()) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ShapeOutlineSlot(
                    slotShapeId    = slots[0],
                    droppedShapeId = droppedShapes[slots[0]],
                    isCorrect      = isCorrect,
                    isWrong        = wrongDropSlotId == slots[0],
                    isAnswered     = isAnswered,
                    shapeOptions   = shapeOptions,
                    shapeColorMap  = shapeColorMap,
                    modifier       = Modifier.onGloballyPositioned { coords ->
                        val pos = coords.positionInRoot()
                        val sz  = coords.size
                        slotCenters[slots[0]] = Offset(pos.x + sz.width / 2f, pos.y + sz.height / 2f)
                    }
                )
            }
        }
        if (slots.size > 1) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                for (slotId in slots.drop(1)) {
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
                            slotCenters[slotId] = Offset(pos.x + sz.width / 2f, pos.y + sz.height / 2f)
                        }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Animated hand hint overlay — shared by all four learning layouts
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun HandHintOverlay(
    progress   : Float,
    startOffset: Offset,
    endOffset  : Offset
) {
    val density = LocalDensity.current
    val handX = startOffset.x + (endOffset.x - startOffset.x) * progress
    val handY = startOffset.y + (endOffset.y - startOffset.y) * progress
    val halfHandPx = with(density) { 24.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(5f)
    ) {
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (handX - halfHandPx).roundToInt(),
                        (handY - halfHandPx).roundToInt()
                    )
                }
                .size(48.dp)
                .graphicsLayer { alpha = 0.85f - progress * 0.3f }
        ) {
            Image(
                painter            = painterResource(R.drawable.ic_pointing_finger),
                contentDescription = null,
                modifier           = Modifier.fillMaxSize(),
                contentScale       = ContentScale.Fit
            )
        }
    }
}

// ── Shape outline drop slot ───────────────────────────────────────────────────
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
            .background(
                if (bgColor == Color.Transparent) colors.background.copy(alpha = 0.72f) else bgColor
            )
            .border(3.dp, colors.accent.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        if (droppedShapeId != null) {
            val droppedOption = shapeOptions.find { it.shapeId == droppedShapeId }
            val droppedColor  = shapeColorMap[droppedShapeId]
            if (droppedOption != null) {
                ColoredShapeImage(option = droppedOption, fillColor = droppedColor, alpha = 1f)
            }
        } else {
            val targetOption = shapeOptions.find { it.shapeId == slotShapeId }
            if (targetOption != null) {
                ColoredShapeImage(option = targetOption, fillColor = null, alpha = 0.22f)
            }
        }
    }
}

// ── Draggable shape tile ──────────────────────────────────────────────────────
@Composable
private fun DraggableShapeTile(
    option        : ShapeOption,
    isAnswered    : Boolean,
    tileSize      : Dp,
    resetKey      : Any   = Unit,
    fillColor     : Color? = null,
    hintRotation  : Float = 0f,
    onDragMove    : ((Offset) -> Unit)? = null,
    onDragStarted : () -> Unit = {},
    onDragEnded   : () -> Unit = {},
    onPositioned  : ((Offset) -> Unit)? = null,
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
            .onGloballyPositioned { coords ->
                if (!isDragging) {
                    rootPos = coords.positionInRoot()
                    sizePx  = Offset(coords.size.width.toFloat(), coords.size.height.toFloat())
                    onPositioned?.invoke(
                        Offset(rootPos.x + sizePx.x / 2f, rootPos.y + sizePx.y / 2f)
                    )
                }
            }
            .pointerInput(isAnswered, resetKey) {
                if (isAnswered) return@pointerInput
                detectDragGestures(
                    onDragStart  = {
                        isDragging = true
                        onDragStarted()
                    },
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
                        onDragEnded()
                    },
                    onDragCancel = {
                        offsetX = 0f; offsetY = 0f; isDragging = false
                        onDragEnded()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        ColoredShapeImage(
            option    = option,
            fillColor = fillColor,
            alpha     = 1f,
            modifier  = Modifier.fillMaxSize().padding(8.dp)
        )
    }
}

// ── Colored shape image helper ────────────────────────────────────────────────
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
