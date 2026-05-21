package com.babybloom.presentation.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.babybloom.R
import com.babybloom.domain.model.ActivityContent
import com.babybloom.presentation.viewmodels.ALL_HABITATS
import com.babybloom.presentation.viewmodels.ANIMAL_HABITAT_MAP
import com.babybloom.presentation.viewmodels.AnimalOption
import com.babybloom.presentation.viewmodels.AnswerState
import com.babybloom.presentation.viewmodels.Habitat
import com.babybloom.presentation.viewmodels.MatchCardState
import com.babybloom.presentation.viewmodels.MatchViewModel
import com.babybloom.ui.theme.LocalGameColorScheme
import com.babybloom.ui.theme.RevealGold
import com.babybloom.ui.theme.TraceBadgeBorder
import com.babybloom.ui.theme.TraceBadgeText
import com.babybloom.util.AssetPathResolver
import com.babybloom.util.ImageAsset
import kotlin.math.roundToInt

// ══════════════════════════════════════════════════════════════════════════════
//  LAYOUT CONSTANTS — tune here if you need to adjust spacing
// ══════════════════════════════════════════════════════════════════════════════

// ── TEST MODE (4-corner layout) ───────────────────────────────────────────────
//
//   CONTENT_SIZE_TEST_DP — total size of the center content card.
//     Change without affecting option card size.
//
//   OPT_SIZE_TEST_DP — total size of each of the 4 option cards.
//     Change without affecting content card size.
//
//   OPT_GAP_DP — gap between the content card's edge and each option card's
//                nearest edge (works both horizontally and vertically).
//
//   OPT_PAIR_SPREAD_DP — extra outward push for left and right options.
//     0.dp  → TL/TR and BL/BR sit at the default (minimum) horizontal distance.
//     Increase → left options shift further left, right options shift further
//                right, widening the gap between TL↔TR and BL↔BR pairs.
//     Does NOT change the vertical positions (top/bottom pairs stay put).
//
private val CONTENT_SIZE_TEST_DP = 160.dp
private val OPT_SIZE_TEST_DP     = 130.dp
private val OPT_GAP_DP           = 70.dp
private val OPT_PAIR_SPREAD_DP   = (-120).dp   // ★ change this to widen left↔right gap

// ── LEARNING MODE (top / bottom layout) ───────────────────────────────────────
//
//   LEARNING_MODE_GAP_DP — vertical space between content card (top) and
//                           answer card (bottom).
//
private val LEARNING_MODE_GAP_DP  = 130.dp
private val LEARNING_TOP_PAD      = 32.dp

private val CONTENT_CARD_LEARN_DP = 180.dp
private val OPT_CARD_LEARN_DP     = 180.dp

// ── Snap radius (line dragging) ───────────────────────────────────────────────
private val SNAP_RADIUS_DP = 80.dp

// ── Attempt dots (bottom row) ─────────────────────────────────────────────────
private val ATTEMPT_DOT_SIZE = 12.dp

// ── Letter SVG centering nudge ─────────────────────────────────────────────
// Positive value → shifts RIGHT, negative → shifts LEFT.
// Tune until the glyph sits visually centered in the card.
private val SVG_LETTER_NUDGE_DP = 12.dp   // ★ change this

// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun wiggleOffset(trigger: Int): Float {
    val offset = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        repeat(4) { offset.animateTo(10f, tween(60)); offset.animateTo(-10f, tween(60)) }
        offset.animateTo(0f, tween(60))
    }
    return offset.value
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)

// ══════════════════════════════════════════════════════════════════════════════
//  Entry point
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun MatchScreen(
    contentItems : List<ActivityContent>,
    isCalmMode   : Boolean,
    isTest       : Boolean,
    isAssessment : Boolean,
    configJson   : String = "{\"matchType\":\"ANIMAL_TO_HABITAT\"}",
    onCardResult : (contentId: String, isCorrect: Boolean, correct: Int, incorrect: Int, attempts: Int, touchQualityScore: Float) -> Unit,
    onComplete   : (elapsedMs: Long, correctCount: Int) -> Unit,
    viewModel    : MatchViewModel = hiltViewModel()
) {
    val colors = LocalGameColorScheme.current

    LaunchedEffect(
        configJson, isCalmMode, isTest, isAssessment,
        contentItems.joinToString("|") { it.contentId }
    ) {
        viewModel.loadActivity(
            contentItems, isCalmMode, isTest, isAssessment,
            configJson, onCardResult, onComplete
        )
    }

    val cardState  by viewModel.cardState.collectAsStateWithLifecycle()
    val wiggleTick by viewModel.wiggleTick.collectAsStateWithLifecycle()

    val showCelebration = when (val s = cardState) {
        is MatchCardState.AnimalHabitatCard -> s.showCelebration
        is MatchCardState.LetterAnimalCard  -> s.showCelebration
        else -> false
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val state = cardState) {
            is MatchCardState.Loading ->
                CircularProgressIndicator(color = colors.accent)
            is MatchCardState.Done ->
                CircularProgressIndicator(color = colors.correct)
            is MatchCardState.AnimalHabitatCard ->
                AnimalHabitatGame(state, isCalmMode, wiggleTick, viewModel)
            is MatchCardState.LetterAnimalCard ->
                LetterAnimalGame(state, isCalmMode, wiggleTick, viewModel)
        }
        if (showCelebration) GoodJobPopup(coverage = -1f)
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  GAME 1 — Animal → Habitat
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun AnimalHabitatGame(
    state      : MatchCardState.AnimalHabitatCard,
    isCalmMode : Boolean,
    wiggleTick : Int,
    viewModel  : MatchViewModel
) {
    val density      = LocalDensity.current
    val snapRadiusPx = with(density) { SNAP_RADIUS_DP.toPx() }
    val colors       = LocalGameColorScheme.current
    val isAnswerable = state.answerState == AnswerState.Idle
    val resetKey     = state.questionIndex

    var gameAreaTopLeft by remember(resetKey) { mutableStateOf(Offset.Zero) }
    var contentCenter   by remember(resetKey) { mutableStateOf(Offset.Zero) }
    val optionCenters   = remember(resetKey)  { mutableStateMapOf<String, Offset>() }
    var isDragging      by remember(resetKey) { mutableStateOf(false) }
    var fingerPosRoot   by remember(resetKey) { mutableStateOf(Offset.Zero) }
    var snappedId       by remember(resetKey) { mutableStateOf<String?>(null) }
    var localDragPos    by remember(resetKey) { mutableStateOf(Offset.Zero) }

    Column(
        modifier            = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(4.dp))
        MatchPromptBanner(stringResource(R.string.match_prompt_where_lives))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .onGloballyPositioned { coords -> gameAreaTopLeft = coords.positionInRoot() }
                .pointerInput(isAnswerable, resetKey) {
                    if (!isAnswerable) return@pointerInput
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            localDragPos  = startOffset
                            fingerPosRoot = gameAreaTopLeft + startOffset
                            isDragging    = true
                            viewModel.onTouchStart(fingerPosRoot)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            localDragPos  += dragAmount
                            fingerPosRoot  = gameAreaTopLeft + localDragPos
                            viewModel.onTouchMove(fingerPosRoot)
                            snappedId = optionCenters
                                .minByOrNull { (_, c) -> (fingerPosRoot - c).getDistance() }
                                ?.takeIf    { (_, c) -> (fingerPosRoot - c).getDistance() < snapRadiusPx }
                                ?.key
                        },
                        onDragEnd = {
                            snappedId?.let { selectedId ->
                                viewModel.onAnswerSelected(
                                    selectedId = selectedId,
                                    releasePoint = fingerPosRoot,
                                    targetCenter = optionCenters[selectedId],
                                    snapRadiusPx = snapRadiusPx
                                )
                            }
                            isDragging = false; snappedId = null
                        },
                        onDragCancel = { isDragging = false; snappedId = null }
                    )
                }
        ) {
            if (state.isTest) {
                TestAnimalHabitatLayout(state, isCalmMode, wiggleTick, snappedId,
                    onContentCenter = { contentCenter = it },
                    onOptionCenter  = { id, c -> optionCenters[id] = c })
            } else {
                LearningAnimalHabitatLayout(state, isCalmMode, wiggleTick, snappedId,
                    onContentCenter = { contentCenter = it },
                    onOptionCenter  = { id, c -> optionCenters[id] = c })
            }

            if (isDragging && contentCenter != Offset.Zero) {
                val endRoot    = snappedId?.let { optionCenters[it] } ?: fingerPosRoot
                val startLocal = contentCenter - gameAreaTopLeft
                val endLocal   = endRoot - gameAreaTopLeft
                val isSnapped  = snappedId != null
                Canvas(modifier = Modifier.fillMaxSize().zIndex(5f)) {
                    drawLine(colors.accent, startLocal, endLocal, 7.dp.toPx(), StrokeCap.Round)
                    drawCircle(colors.accent, 10.dp.toPx(), startLocal)
                    drawCircle(
                        color  = if (isSnapped) colors.correct else colors.accent.copy(alpha = 0.55f),
                        radius = if (isSnapped) 15.dp.toPx()   else 10.dp.toPx(),
                        center = endLocal
                    )
                }
            }

            if (state.showHandHint && !isDragging) {
                val correctCenter = optionCenters[state.correctHabitatId]
                if (correctCenter != null && contentCenter != Offset.Zero) {
                    HandHintOverlay(contentCenter, correctCenter, gameAreaTopLeft)
                }
            }
        }

        MatchAttemptsRow(attemptsLeft = state.attemptsLeft)
        Spacer(Modifier.height(8.dp))
    }
}

// ── Test layout: animal CENTER, 4 habitats in corners ────────────────────────
@Composable
private fun TestAnimalHabitatLayout(
    state           : MatchCardState.AnimalHabitatCard,
    isCalmMode      : Boolean,
    wiggleTick      : Int,
    snappedId       : String?,
    onContentCenter : (Offset) -> Unit,
    onOptionCenter  : (String, Offset) -> Unit
) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val contentHalfPx = with(density) { (CONTENT_SIZE_TEST_DP / 2).toPx() }
        val optHalfPx     = with(density) { (OPT_SIZE_TEST_DP / 2).toPx() }
        val innerGapPx    = with(density) { OPT_GAP_DP.toPx() }
        val spreadPx      = with(density) { OPT_PAIR_SPREAD_DP.toPx() }
        val cx = constraints.maxWidth  / 2f
        val cy = constraints.maxHeight / 2f

        // dX uses the extra spread so left↔right options can be pushed apart
        // independently from the top↔bottom distance (dY stays unaffected).
        val dX = contentHalfPx + innerGapPx + optHalfPx + spreadPx
        val dY = contentHalfPx + innerGapPx + optHalfPx

        val signs = listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f)
        state.options.take(4).forEachIndexed { idx, habitat ->
            val (sx, sy) = signs[idx]
            val topLeftX = (cx + sx * dX - optHalfPx).roundToInt()
            val topLeftY = (cy + sy * dY - optHalfPx).roundToInt()
            val isCorrect = habitat.id == state.correctHabitatId
            val wiggle    = wiggleOffset(if (isCorrect && state.showCorrectWiggle) wiggleTick else 0)

            Box(
                modifier = Modifier
                    .offset { IntOffset(topLeftX, topLeftY) }
                    .size(OPT_SIZE_TEST_DP)
                    .graphicsLayer { translationX = wiggle }
                    .onGloballyPositioned { coords ->
                        val p = coords.positionInRoot(); val s = coords.size
                        onOptionCenter(habitat.id, Offset(p.x + s.width / 2f, p.y + s.height / 2f))
                    }
            ) {
                HabitatOptionCard(habitat, isCorrect, habitat.id == snappedId,
                    state.answerState, state.lastWrongId, isCalmMode)
            }
        }

        val contentTopLeftX = (cx - contentHalfPx).roundToInt()
        val contentTopLeftY = (cy - contentHalfPx).roundToInt()
        Box(
            modifier = Modifier
                .offset { IntOffset(contentTopLeftX, contentTopLeftY) }
                .size(CONTENT_SIZE_TEST_DP)
                .onGloballyPositioned { coords ->
                    val p = coords.positionInRoot(); val s = coords.size
                    onContentCenter(Offset(p.x + s.width / 2f, p.y + s.height / 2f))
                }
        ) {
            AnimalContentCard(state.animal, isCalmMode, state.answerState)
        }
    }
}

// ── Learning layout: animal TOP, single habitat BOTTOM ───────────────────────
@Composable
private fun LearningAnimalHabitatLayout(
    state           : MatchCardState.AnimalHabitatCard,
    isCalmMode      : Boolean,
    wiggleTick      : Int,
    snappedId       : String?,
    onContentCenter : (Offset) -> Unit,
    onOptionCenter  : (String, Offset) -> Unit
) {
    val habitat = state.options.firstOrNull() ?: return
    val wiggle  = wiggleOffset(if (state.showCorrectWiggle) wiggleTick else 0)

    Column(
        modifier            = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(LEARNING_TOP_PAD))

        Box(
            modifier = Modifier
                .size(CONTENT_CARD_LEARN_DP)
                .onGloballyPositioned { coords ->
                    val p = coords.positionInRoot(); val s = coords.size
                    onContentCenter(Offset(p.x + s.width / 2f, p.y + s.height / 2f))
                }
        ) { AnimalContentCard(state.animal, isCalmMode, state.answerState) }

        Spacer(Modifier.height(LEARNING_MODE_GAP_DP))

        Box(
            modifier = Modifier
                .size(OPT_CARD_LEARN_DP)
                .graphicsLayer { translationX = wiggle }
                .onGloballyPositioned { coords ->
                    val p = coords.positionInRoot(); val s = coords.size
                    onOptionCenter(habitat.id, Offset(p.x + s.width / 2f, p.y + s.height / 2f))
                }
        ) {
            HabitatOptionCard(habitat, isCorrect = true, isSnapped = habitat.id == snappedId,
                state.answerState, state.lastWrongId, isCalmMode)
        }

        Spacer(Modifier.weight(1f))
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  GAME 2 — Letter → Animal
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun LetterAnimalGame(
    state      : MatchCardState.LetterAnimalCard,
    isCalmMode : Boolean,
    wiggleTick : Int,
    viewModel  : MatchViewModel
) {
    val density      = LocalDensity.current
    val snapRadiusPx = with(density) { SNAP_RADIUS_DP.toPx() }
    val colors       = LocalGameColorScheme.current
    val isAnswerable = state.answerState == AnswerState.Idle
    val resetKey     = state.questionIndex

    var gameAreaTopLeft by remember(resetKey) { mutableStateOf(Offset.Zero) }
    var contentCenter   by remember(resetKey) { mutableStateOf(Offset.Zero) }
    val optionCenters   = remember(resetKey)  { mutableStateMapOf<String, Offset>() }
    var isDragging      by remember(resetKey) { mutableStateOf(false) }
    var fingerPosRoot   by remember(resetKey) { mutableStateOf(Offset.Zero) }
    var snappedId       by remember(resetKey) { mutableStateOf<String?>(null) }
    var localDragPos    by remember(resetKey) { mutableStateOf(Offset.Zero) }

    Column(
        modifier            = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(4.dp))
        MatchPromptBanner(stringResource(R.string.match_prompt_choose_animal))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .onGloballyPositioned { coords -> gameAreaTopLeft = coords.positionInRoot() }
                .pointerInput(isAnswerable, resetKey) {
                    if (!isAnswerable) return@pointerInput
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            localDragPos  = startOffset
                            fingerPosRoot = gameAreaTopLeft + startOffset
                            isDragging    = true
                            viewModel.onTouchStart(fingerPosRoot)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            localDragPos  += dragAmount
                            fingerPosRoot  = gameAreaTopLeft + localDragPos
                            viewModel.onTouchMove(fingerPosRoot)
                            snappedId = optionCenters
                                .minByOrNull { (_, c) -> (fingerPosRoot - c).getDistance() }
                                ?.takeIf    { (_, c) -> (fingerPosRoot - c).getDistance() < snapRadiusPx }
                                ?.key
                        },
                        onDragEnd = {
                            snappedId?.let { selectedId ->
                                viewModel.onAnswerSelected(
                                    selectedId = selectedId,
                                    releasePoint = fingerPosRoot,
                                    targetCenter = optionCenters[selectedId],
                                    snapRadiusPx = snapRadiusPx
                                )
                            }
                            isDragging = false; snappedId = null
                        },
                        onDragCancel = { isDragging = false; snappedId = null }
                    )
                }
        ) {
            if (state.isTest) {
                TestLetterAnimalLayout(state, isCalmMode, wiggleTick, snappedId,
                    onContentCenter = { contentCenter = it },
                    onOptionCenter  = { id, c -> optionCenters[id] = c })
            } else {
                LearningLetterAnimalLayout(state, isCalmMode, wiggleTick, snappedId,
                    onContentCenter = { contentCenter = it },
                    onOptionCenter  = { id, c -> optionCenters[id] = c })
            }

            if (isDragging && contentCenter != Offset.Zero) {
                val endRoot    = snappedId?.let { optionCenters[it] } ?: fingerPosRoot
                val startLocal = contentCenter - gameAreaTopLeft
                val endLocal   = endRoot - gameAreaTopLeft
                val isSnapped  = snappedId != null
                Canvas(modifier = Modifier.fillMaxSize().zIndex(5f)) {
                    drawLine(colors.accent, startLocal, endLocal, 7.dp.toPx(), StrokeCap.Round)
                    drawCircle(colors.accent, 10.dp.toPx(), startLocal)
                    drawCircle(
                        color  = if (isSnapped) colors.correct else colors.accent.copy(alpha = 0.55f),
                        radius = if (isSnapped) 15.dp.toPx() else 10.dp.toPx(),
                        center = endLocal
                    )
                }
            }

            if (state.showHandHint && !isDragging) {
                val correctCenter = optionCenters[state.correctAnimalId]
                if (correctCenter != null && contentCenter != Offset.Zero) {
                    HandHintOverlay(contentCenter, correctCenter, gameAreaTopLeft)
                }
            }
        }

        MatchAttemptsRow(attemptsLeft = state.attemptsLeft)
        Spacer(Modifier.height(8.dp))
    }
}

// ── Test layout: letter CENTER, 4 animals in corners ─────────────────────────
@Composable
private fun TestLetterAnimalLayout(
    state           : MatchCardState.LetterAnimalCard,
    isCalmMode      : Boolean,
    wiggleTick      : Int,
    snappedId       : String?,
    onContentCenter : (Offset) -> Unit,
    onOptionCenter  : (String, Offset) -> Unit
) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val contentHalfPx = with(density) { (CONTENT_SIZE_TEST_DP / 2).toPx() }
        val optHalfPx     = with(density) { (OPT_SIZE_TEST_DP / 2).toPx() }
        val innerGapPx    = with(density) { OPT_GAP_DP.toPx() }
        val spreadPx      = with(density) { OPT_PAIR_SPREAD_DP.toPx() }
        val cx = constraints.maxWidth  / 2f
        val cy = constraints.maxHeight / 2f

        val dX = contentHalfPx + innerGapPx + optHalfPx + spreadPx
        val dY = contentHalfPx + innerGapPx + optHalfPx

        val signs = listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f)
        state.options.take(4).forEachIndexed { idx, option ->
            val (sx, sy) = signs[idx]
            val topLeftX = (cx + sx * dX - optHalfPx).roundToInt()
            val topLeftY = (cy + sy * dY - optHalfPx).roundToInt()
            val isCorrect = option.entity.id == state.correctAnimalId
            val wiggle    = wiggleOffset(if (isCorrect && state.showCorrectWiggle) wiggleTick else 0)

            Box(
                modifier = Modifier
                    .offset { IntOffset(topLeftX, topLeftY) }
                    .size(OPT_SIZE_TEST_DP)
                    .graphicsLayer { translationX = wiggle }
                    .onGloballyPositioned { coords ->
                        val p = coords.positionInRoot(); val s = coords.size
                        onOptionCenter(option.entity.id, Offset(p.x + s.width / 2f, p.y + s.height / 2f))
                    }
            ) {
                AnimalAnswerCard(option, isCorrect, option.entity.id == snappedId,
                    state.answerState, state.lastWrongId, isCalmMode)
            }
        }

        val contentTopLeftX = (cx - contentHalfPx).roundToInt()
        val contentTopLeftY = (cy - contentHalfPx).roundToInt()
        Box(
            modifier = Modifier
                .offset { IntOffset(contentTopLeftX, contentTopLeftY) }
                .size(CONTENT_SIZE_TEST_DP)
                .onGloballyPositioned { coords ->
                    val p = coords.positionInRoot(); val s = coords.size
                    onContentCenter(Offset(p.x + s.width / 2f, p.y + s.height / 2f))
                }
        ) { LetterContentCard(state.letter, state.letterImageAsset, state.answerState) }
    }
}

// ── Learning layout: letter TOP, single animal BOTTOM ────────────────────────
@Composable
private fun LearningLetterAnimalLayout(
    state           : MatchCardState.LetterAnimalCard,
    isCalmMode      : Boolean,
    wiggleTick      : Int,
    snappedId       : String?,
    onContentCenter : (Offset) -> Unit,
    onOptionCenter  : (String, Offset) -> Unit
) {
    val option = state.options.firstOrNull() ?: return
    val wiggle = wiggleOffset(if (state.showCorrectWiggle) wiggleTick else 0)

    Column(
        modifier            = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(LEARNING_TOP_PAD))

        Box(
            modifier = Modifier
                .size(CONTENT_CARD_LEARN_DP)
                .onGloballyPositioned { coords ->
                    val p = coords.positionInRoot(); val s = coords.size
                    onContentCenter(Offset(p.x + s.width / 2f, p.y + s.height / 2f))
                }
        ) { LetterContentCard(state.letter, state.letterImageAsset, state.answerState) }

        Spacer(Modifier.height(LEARNING_MODE_GAP_DP))

        Box(
            modifier = Modifier
                .size(OPT_CARD_LEARN_DP)
                .graphicsLayer { translationX = wiggle }
                .onGloballyPositioned { coords ->
                    val p = coords.positionInRoot(); val s = coords.size
                    onOptionCenter(option.entity.id, Offset(p.x + s.width / 2f, p.y + s.height / 2f))
                }
        ) {
            AnimalAnswerCard(option, isCorrect = true, isSnapped = option.entity.id == snappedId,
                state.answerState, state.lastWrongId, isCalmMode)
        }

        Spacer(Modifier.weight(1f))
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  Content cards
// ══════════════════════════════════════════════════════════════════════════════

// FIX: image and label were overlapping because AsyncImage used fillMaxHeight(1f)
// while the text was absolutely positioned over it. Now uses a Column so the
// image gets weight(1f) (fills all remaining space) and the label sits below it
// in its own row — they can never overlap regardless of font size or card size.
@Composable
private fun AnimalContentCard(
    animal      : ActivityContent,
    isCalmMode  : Boolean,
    answerState : AnswerState,
    modifier    : Modifier = Modifier
) {
    val colors  = LocalGameColorScheme.current
    val context = LocalContext.current
    val path    = AssetPathResolver.androidAssetUri(
        AssetPathResolver.animalImagePathFor(animal.contentId, isCalmMode))
    val borderColor by animateColorAsState(
        when (answerState) {
            AnswerState.Correct  -> colors.correct
            AnswerState.Revealed -> RevealGold
            else                 -> colors.accent
        }, tween(300), label = "acBorder"
    )
    Box(
        modifier = modifier.fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .border(3.dp, borderColor, RoundedCornerShape(20.dp))
            .background(colors.background)
    ) {
        Column(
            modifier            = Modifier.fillMaxSize().padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Image takes all remaining space above the label
            AsyncImage(
                model              = ImageRequest.Builder(context).data(path).crossfade(true).build(),
                contentDescription = animal.labelAr,
                modifier           = Modifier.weight(1f).fillMaxWidth(),
                contentScale       = ContentScale.Fit
            )
            // Label always sits in its own row — never overlaps the image
            Text(
                text       = animal.labelAr,
                fontSize   = 20.sp,
                fontWeight = FontWeight.Bold,
                color      = colors.accent,
                textAlign  = TextAlign.Center,
                maxLines   = 1,
                modifier   = Modifier.padding(bottom = 4.dp)
            )
        }
    }
}

@Composable
private fun LetterContentCard(
    letter           : ActivityContent,
    letterImageAsset : ImageAsset,
    answerState      : AnswerState,
    modifier         : Modifier = Modifier
) {
    val colors  = LocalGameColorScheme.current
    val context = LocalContext.current
    val borderColor by animateColorAsState(
        when (answerState) {
            AnswerState.Correct  -> colors.correct
            AnswerState.Revealed -> RevealGold
            else                 -> colors.accent
        }, tween(300), label = "lcBorder"
    )
    Box(
        modifier = modifier.fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .border(3.dp, borderColor, RoundedCornerShape(20.dp))
            .background(colors.background)
            .background(colors.accent.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center
    ) {
        when (letterImageAsset) {
            is ImageAsset.SvgDrawable -> {
                val resId = context.resources.getIdentifier(
                    letterImageAsset.drawableName, "drawable", context.packageName)
                if (resId != 0) Image(painterResource(resId), letter.labelAr,
                    Modifier.fillMaxSize().padding(14.dp).offset(x = SVG_LETTER_NUDGE_DP),
                    alignment    = Alignment.Center,
                    colorFilter = ColorFilter.tint(colors.accent),
                    contentScale = ContentScale.Fit)
                else Text(letter.labelAr, fontSize = 52.sp, fontWeight = FontWeight.Black,
                    color = colors.accent)
            }
            is ImageAsset.PngAsset -> AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(AssetPathResolver.androidAssetUri(letterImageAsset.path))
                    .crossfade(true).build(),
                contentDescription = letter.labelAr,
                modifier = Modifier.fillMaxSize().padding(14.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  Option cards
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun HabitatOptionCard(
    habitat     : Habitat,
    isCorrect   : Boolean,
    isSnapped   : Boolean,
    answerState : AnswerState,
    lastWrongId : String?,
    isCalmMode  : Boolean,
    modifier    : Modifier = Modifier
) {
    val colors       = LocalGameColorScheme.current
    val context      = LocalContext.current
    val imgFile      = if (isCalmMode) habitat.calmImage else habitat.activeImage
    val mood         = if (isCalmMode) "calm" else "active"
    val habitatPath  = "file:///android_asset/learning_content/visual/$mood/$imgFile"
    val habitatLabel = stringResource(habitat.labelResId)
    val isWrong      = habitat.id == lastWrongId && answerState == AnswerState.Wrong
    val showCorrect  = isCorrect && answerState in listOf(AnswerState.Correct, AnswerState.Revealed)

    val borderColor by animateColorAsState(
        when {
            isCorrect && answerState == AnswerState.Correct  -> colors.correct
            isCorrect && answerState == AnswerState.Revealed -> RevealGold
            isWrong                                          -> colors.wrong
            isSnapped                                        -> colors.accent
            else                                             -> colors.accent.copy(alpha = 0.28f)
        }, tween(250), label = "habBorder"
    )
    val borderWidth = if (isSnapped || showCorrect || isWrong) 3.dp else 2.dp

    Box(
        modifier = modifier.fillMaxSize()
            .clip(RoundedCornerShape(18.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(18.dp))
            .background(if (isSnapped && !showCorrect) colors.accent.copy(alpha = 0.07f)
            else Color.Transparent)
    ) {
        Image(rememberAsyncImagePainter(ImageRequest.Builder(context).data(habitatPath).build()),
            habitatLabel, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)

        if (showCorrect)
            Box(Modifier.fillMaxSize().background(
                if (answerState == AnswerState.Revealed) RevealGold.copy(alpha = 0.20f)
                else colors.correct.copy(alpha = 0.18f)))

        if (isWrong)
            Box(Modifier.fillMaxSize().background(colors.wrong.copy(alpha = 0.22f)))

        Box(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.55f),
                    RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp))
                .padding(vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) { Text(habitatLabel, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1) }

        OptionCornerBadge(isCorrect, isWrong, answerState)
    }
}

// FIX: animal image and name label were overlapping because the image used
// fillMaxHeight(0.9f) while the label was absolutely positioned at the bottom.
// Now the image and label live in a Column inside the Box so they are stacked
// properly — image takes weight(1f), label takes its natural height below it.
@Composable
private fun AnimalAnswerCard(
    option      : AnimalOption,
    isCorrect   : Boolean,
    isSnapped   : Boolean,
    answerState : AnswerState,
    lastWrongId : String?,
    isCalmMode  : Boolean,
    modifier    : Modifier = Modifier
) {
    val colors      = LocalGameColorScheme.current
    val context     = LocalContext.current
    val animalPath  = AssetPathResolver.androidAssetUri(
        AssetPathResolver.animalImagePathFor(option.entity.id, isCalmMode))
    val habitat     = ALL_HABITATS.firstOrNull { it.id == option.habitatId } ?: ALL_HABITATS.first()
    val mood        = if (isCalmMode) "calm" else "active"
    val habitatPath = "file:///android_asset/learning_content/visual/$mood/${
        if (isCalmMode) habitat.calmImage else habitat.activeImage}"
    val isWrong     = option.entity.id == lastWrongId && answerState == AnswerState.Wrong
    val showCorrect = isCorrect && answerState in listOf(AnswerState.Correct, AnswerState.Revealed)

    val borderColor by animateColorAsState(
        when {
            isCorrect && answerState == AnswerState.Correct  -> colors.correct
            isCorrect && answerState == AnswerState.Revealed -> RevealGold
            isWrong                                          -> colors.wrong
            isSnapped                                        -> colors.accent
            else                                             -> colors.accent.copy(alpha = 0.28f)
        }, tween(250), label = "anOptBorder"
    )
    val habitatAlpha by animateFloatAsState(if (showCorrect) 1f else 0f, tween(400), label = "habFade")
    val borderWidth = if (isSnapped || showCorrect || isWrong) 3.dp else 2.dp

    Box(
        modifier = modifier.fillMaxSize()
            .clip(RoundedCornerShape(18.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(18.dp))
            .background(if (isSnapped && !showCorrect) colors.accent.copy(alpha = 0.07f)
            else Color.Transparent)
    ) {
        // Background habitat (fades in on correct)
        Image(
            rememberAsyncImagePainter(ImageRequest.Builder(context).data(habitatPath).build()),
            null,
            Modifier.fillMaxSize().graphicsLayer { alpha = habitatAlpha },
            contentScale = ContentScale.Crop
        )

        if (showCorrect) Box(Modifier.fillMaxSize().background(
            if (answerState == AnswerState.Revealed) RevealGold.copy(alpha = 0.20f)
            else colors.correct.copy(alpha = 0.18f)))

        if (isWrong) Box(Modifier.fillMaxSize().background(colors.wrong.copy(alpha = 0.22f)))

        // Animal image + label in a Column so they never overlap
        Column(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model              = ImageRequest.Builder(context).data(animalPath).crossfade(true).build(),
                contentDescription = option.entity.labelAr,
                modifier           = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 1.dp, horizontal = 1.dp),
                contentScale       = ContentScale.Fit
            )
            // Label always in its own row at the bottom — no overlap possible
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (showCorrect) Color.Black.copy(alpha = 0.55f)
                        else colors.accent.copy(alpha = 0.10f),
                        RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp)
                    )
                    .padding(vertical = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text       = option.entity.labelAr,
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color      = if (showCorrect) Color.White else colors.accent,
                    maxLines   = 1
                )
            }
        }

        OptionCornerBadge(isCorrect, isWrong, answerState)
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  Shared composables
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun BoxScope.OptionCornerBadge(
    isCorrect: Boolean, isWrong: Boolean, answerState: AnswerState
) {
    val colors = LocalGameColorScheme.current
    if (answerState == AnswerState.Idle) return
    val showGreen = isCorrect && answerState == AnswerState.Correct
    val showGold  = isCorrect && answerState == AnswerState.Revealed
    if (!showGreen && !showGold && !isWrong) return
    val bg = when { showGreen -> colors.correct; showGold -> RevealGold; else -> colors.wrong }
    Box(
        Modifier.align(Alignment.TopEnd).padding(5.dp).size(24.dp).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center
    ) {
        Icon(if (isWrong) Icons.Default.Close else Icons.Default.Check, null,
            tint = Color.White, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun MatchAttemptsRow(attemptsLeft: Int) {
    val colors       = LocalGameColorScheme.current
    val attemptsUsed = 3 - attemptsLeft
    Row(
        modifier              = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        repeat(3) { i ->
            val isFilled = i < attemptsUsed
            val borderColor by animateColorAsState(
                if (isFilled) colors.wrong else colors.accent.copy(alpha = 0.5f),
                tween(300), label = "mDot$i"
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .size(ATTEMPT_DOT_SIZE)
                    .border(2.dp, borderColor, CircleShape)
                    .background(if (isFilled) colors.wrong else Color.Transparent, CircleShape)
            )
        }
    }
}

@Composable
private fun MatchPromptBanner(text: String) {
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
            Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TraceBadgeText,
                maxLines = 1,
                style = LocalTextStyle.current.copy(textDirection = TextDirection.Rtl))
            Spacer(Modifier.width(8.dp))
            Image(painterResource(R.drawable.ic_pointing_finger), null, Modifier.size(26.dp))
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  Hand drawing hint
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun HandHintOverlay(
    startRoot       : Offset,
    endRoot         : Offset,
    gameAreaTopLeft : Offset
) {
    val colors     = LocalGameColorScheme.current
    val density    = LocalDensity.current
    val handSizeDp = 52.dp
    val handHalfPx = with(density) { (handSizeDp / 2).toPx() }

    val infiniteTransition = rememberInfiniteTransition(label = "handHint")
    val progress by infiniteTransition.animateFloat(
        initialValue  = 0f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(
            animation          = tween(durationMillis = 1_700, easing = EaseInOutCubic),
            repeatMode         = RepeatMode.Restart,
            initialStartOffset = StartOffset(500)
        ),
        label = "handProgress"
    )

    val handRootX  = lerp(startRoot.x, endRoot.x, progress)
    val handRootY  = lerp(startRoot.y, endRoot.y, progress)
    val startLocal = startRoot - gameAreaTopLeft
    val handLocalX = handRootX - gameAreaTopLeft.x
    val handLocalY = handRootY - gameAreaTopLeft.y

    Box(modifier = Modifier.fillMaxSize().zIndex(6f)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawLine(
                color       = colors.accent.copy(alpha = 0.42f),
                start       = startLocal,
                end         = Offset(handLocalX, handLocalY),
                strokeWidth = 6.dp.toPx(),
                cap         = StrokeCap.Round
            )
            drawCircle(colors.accent.copy(alpha = 0.55f), 9.dp.toPx(), startLocal)
        }

        Image(
            painter            = painterResource(R.drawable.ic_pointing_finger),
            contentDescription = null,
            modifier           = Modifier
                .size(handSizeDp)
                .zIndex(7f)
                .offset {
                    IntOffset(
                        (handLocalX - handHalfPx).roundToInt(),
                        (handLocalY - handHalfPx).roundToInt()
                    )
                }
        )
    }
}
