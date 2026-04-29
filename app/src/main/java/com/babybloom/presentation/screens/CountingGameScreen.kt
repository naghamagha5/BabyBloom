package com.babybloom.presentation.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.babybloom.domain.model.ActivityContent
import com.babybloom.presentation.viewmodels.CountGameType
import com.babybloom.presentation.viewmodels.CountingGameUiState
import com.babybloom.presentation.viewmodels.CountingGameViewModel
import com.babybloom.ui.theme.DragAttemptDotFull
import com.babybloom.ui.theme.DragProgressIdle
import com.babybloom.ui.theme.LocalGameColorScheme
import kotlin.math.cos
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────────────────────
// CountingGameScreen.kt
//
// Card/border/button colors → LocalGameColorScheme (accent / background /
// correct / wrong), exactly like StoryScreen and the fixed DragGameScreen.
//
// Fixed semantic values kept:
//   • GoldColor    — "show correct hint" highlight (content feedback, not theme)
//   • CALM/ACTIVE shape colors — the *tint applied to shape SVGs*, which is
//     intentional content coloring, not a game-round theme color.
// ─────────────────────────────────────────────────────────────────────────────

// ── Shape tint palettes (content colors, not theme colors) ───────────────────
private val ACTIVE_SHAPE_COLORS = listOf(
    Color(0xFFFF8A65), Color(0xFF9C27B0), Color(0xFF2196F3),
    Color(0xFF4CAF50), Color(0xFFE91E63), Color(0xFFFFB300)
)
private val CALM_SHAPE_COLORS = listOf(
    Color(0xFF5BB89B), Color(0xFF9575CD), Color(0xFF4FC3F7),
    Color(0xFF80CBC4), Color(0xFFEF9A9A), Color(0xFFFFCC80)
)

// Gold hint — semantic "here is the answer" color, intentionally fixed
private val GoldColor = Color(0xFFFFD700)

// ── Row layout lookup ─────────────────────────────────────────────────────────
private fun rowLayout(count: Int): List<List<Int>> = when (count) {
    1    -> listOf(listOf(0))
    2    -> listOf(listOf(0, 1))
    else -> listOf(listOf(0, 1, 2))
}

// ── Fireworks ─────────────────────────────────────────────────────────────────

private data class FireworksBurst(
    val cxFrac        : Float,
    val cyFrac        : Float,
    val color         : Color,
    val particleCount : Int   = 14,
    val maxRadius     : Float = 280f
)

private val BURST_COLORS = listOf(
    Color(0xFFFFD700), Color(0xFFFF4081), Color(0xFF40C4FF),
    Color(0xFF69F0AE), Color(0xFFFF6D00), Color(0xFFEA80FC)
)
private val BURSTS = listOf(
    FireworksBurst(0.20f, 0.25f, BURST_COLORS[0]),
    FireworksBurst(0.80f, 0.22f, BURST_COLORS[1]),
    FireworksBurst(0.50f, 0.50f, BURST_COLORS[2], maxRadius = 320f),
    FireworksBurst(0.15f, 0.72f, BURST_COLORS[3]),
    FireworksBurst(0.82f, 0.70f, BURST_COLORS[4]),
    FireworksBurst(0.50f, 0.18f, BURST_COLORS[5], particleCount = 10)
)

// ── Entry point ───────────────────────────────────────────────────────────────

@Composable
fun CountingGameScreen(
    currentItem    : ActivityContent,
    isCalmMode     : Boolean,
    difficultyLevel: Int,
    activityId     : String,
    roundIndex     : Int,
    onComplete     : (isCorrect: Boolean, elapsedMs: Long, attempts: Int, touchComplexity: Float) -> Unit,
    viewModel      : CountingGameViewModel = hiltViewModel()
) {
    val colors = LocalGameColorScheme.current

    LaunchedEffect(currentItem.contentId) {
        viewModel.loadGame(currentItem, difficultyLevel, activityId, roundIndex)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        when (val s = uiState) {
            is CountingGameUiState.Loading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    // Spinner uses scheme accent — consistent with other games
                    CircularProgressIndicator(color = colors.accent)
                }
            is CountingGameUiState.Playing -> {
                CountingGameContent(
                    state            = s,
                    isCalmMode       = isCalmMode,
                    viewModel        = viewModel,
                    onAnswerSelected = { viewModel.onAnswerSelected(it, onComplete) }
                )
                // ── Unified celebration popup ─────────────────────────────
                // Replaces the old bespoke FireworksOverlay; GoodJobPopup is
                // the same confetti+card that TraceScreen uses.
                if (s.showCelebration) {
                    GoodJobPopup()
                }
            }
        }
    }
}

// ── Main content ──────────────────────────────────────────────────────────────

@Composable
private fun CountingGameContent(
    state           : CountingGameUiState.Playing,
    isCalmMode      : Boolean,
    viewModel       : CountingGameViewModel,
    onAnswerSelected: (Int) -> Unit
) {
    val colors = LocalGameColorScheme.current

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text      = state.subjectQuestionAr,
            style     = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize   = 26.sp
            ),
            textAlign = TextAlign.Center,
            // Accent color for the question text — matches the round's scheme
            color     = colors.accent
        )

        AttemptsRow(attempts = state.attempts, maxAttempts = state.maxAttempts)

        ObjectsCard(
            state      = state,
            isCalmMode = isCalmMode,
            viewModel  = viewModel,
            modifier   = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 6.dp)
        )

        val buttonsEnabled = !state.isAnimating && state.selectedAnswer == null

        Column(
            modifier            = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            state.choices.chunked(2).forEach { row ->
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    row.forEach { choice ->
                        AnswerButton(
                            number     = choice,
                            isSelected = state.selectedAnswer == choice,
                            isCorrect  = if (state.selectedAnswer == choice) state.isCorrect else null,
                            isGoldHint = state.showCorrectHint && choice == state.targetCount,
                            isShaking  = state.wrongAnswerIndex == choice,
                            isEnabled  = buttonsEnabled,
                            onClick    = { onAnswerSelected(choice) },
                            modifier   = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

// ── Objects card ──────────────────────────────────────────────────────────────

@Composable
private fun ObjectsCard(
    state     : CountingGameUiState.Playing,
    isCalmMode: Boolean,
    viewModel : CountingGameViewModel,
    modifier  : Modifier = Modifier
) {
    val colors = LocalGameColorScheme.current

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            // background and accent border from scheme — replaces hardcoded calm/active palette
            .background(colors.background)
            .border(2.dp, colors.accent, RoundedCornerShape(24.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        val rows    = rowLayout(state.targetCount)
        val numRows = rows.size
        val maxCols = rows.maxOf { it.size }
        val spacing = 10.dp

        val availW        = maxWidth  - spacing * (maxCols - 1)
        val availH        = maxHeight - spacing * (numRows - 1)
        val widthPerCell  = availW  / maxCols
        val heightPerCell = availH  / numRows
        val imageSize: Dp = minOf(widthPerCell, heightPerCell).coerceAtLeast(40.dp)

        Column(
            modifier            = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            rows.forEach { rowIndices ->
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    rowIndices.forEach { itemIndex ->
                        AnimalOrShapeItem(
                            index      = itemIndex,
                            state      = state,
                            isCalmMode = isCalmMode,
                            imageSize  = imageSize,
                            viewModel  = viewModel
                        )
                    }
                }
            }
        }
    }
}

// ── Single animal or shape item ───────────────────────────────────────────────

@Composable
private fun AnimalOrShapeItem(
    index     : Int,
    state     : CountingGameUiState.Playing,
    isCalmMode: Boolean,
    imageSize : Dp,
    viewModel : CountingGameViewModel
) {
    val context = LocalContext.current
    val mood    = if (isCalmMode) "calm" else "active"

    val isPulsing = index == state.countingStep
    val animScale by animateFloatAsState(
        targetValue   = if (isPulsing) 1.40f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessLow
        ),
        label = "scale_$index"
    )

    Box(
        modifier         = Modifier.size(imageSize).scale(animScale),
        contentAlignment = Alignment.Center
    ) {
        when (state.gameType) {
            CountGameType.ANIMAL -> AsyncImage(
                model = ImageRequest.Builder(context)
                    .data("file:///android_asset/learning_content/visual/$mood/${state.subjectId}.png")
                    .build(),
                contentDescription = state.subjectLabelAr,
                modifier           = Modifier.fillMaxSize(),
                contentScale       = ContentScale.Fit
            )
            CountGameType.SHAPE -> {
                val shape = viewModel.getShapeInfo(state.subjectId)
                if (shape != null) {
                    val resId = context.resources.getIdentifier(
                        shape.drawableName, "drawable", context.packageName
                    )
                    if (resId != 0) {
                        // Shape SVG tint colors are content colors (visual distinction
                        // between shape instances), not game-theme colors — kept as-is.
                        Image(
                            painter            = painterResource(id = resId),
                            contentDescription = shape.labelAr,
                            modifier           = Modifier.fillMaxSize(),
                            colorFilter        = ColorFilter.tint(
                                if (isCalmMode)
                                    CALM_SHAPE_COLORS[index % CALM_SHAPE_COLORS.size]
                                else
                                    ACTIVE_SHAPE_COLORS[index % ACTIVE_SHAPE_COLORS.size]
                            )
                        )
                    }
                }
            }
        }
    }
}

// ── Answer button ─────────────────────────────────────────────────────────────

@Composable
private fun AnswerButton(
    number    : Int,
    isSelected: Boolean,
    isCorrect : Boolean?,
    isGoldHint: Boolean,
    isShaking : Boolean,
    isEnabled : Boolean,
    onClick   : () -> Unit,
    modifier  : Modifier = Modifier
) {
    val colors  = LocalGameColorScheme.current
    val context = LocalContext.current

    // Button background:
    //   gold hint     → GoldColor (semantic "here is the answer")
    //   correct       → colors.correct (scheme green)
    //   wrong         → colors.wrong   (scheme red)
    //   idle          → colors.background with accent border
    val animBg by animateColorAsState(
        targetValue = when {
            isGoldHint                       -> GoldColor
            isSelected && isCorrect == true  -> colors.correct
            isSelected && isCorrect == false -> colors.wrong
            else                             -> colors.background
        },
        animationSpec = tween(280), label = "bg"
    )
    val animBorder by animateColorAsState(
        targetValue = when {
            isGoldHint || (isSelected && isCorrect == true)  -> colors.correct
            isSelected && isCorrect == false                 -> colors.wrong
            else                                             -> colors.accent
        },
        animationSpec = tween(280), label = "border"
    )

    // Icon tint: white on colored states, accent on idle
    val iconTint = when {
        isGoldHint || (isSelected && isCorrect != null) -> Color.White
        else                                            -> colors.accent
    }

    val shakeAnim = remember { Animatable(0f) }
    LaunchedEffect(isShaking) {
        if (isShaking) {
            repeat(6) {
                shakeAnim.animateTo(
                    if (it % 2 == 0) 14f else -14f,
                    tween(70, easing = LinearEasing)
                )
            }
            shakeAnim.animateTo(0f, tween(60))
        } else shakeAnim.snapTo(0f)
    }

    val scale by animateFloatAsState(
        targetValue   = if ((isSelected && isCorrect == true) || isGoldHint) 1.08f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label         = "btnScale"
    )

    Box(
        modifier = modifier
            .height(72.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; translationX = shakeAnim.value }
            .clip(RoundedCornerShape(18.dp))
            .background(animBg)
            .border(2.dp, animBorder, RoundedCornerShape(18.dp))
            .clickable(
                enabled           = isEnabled,
                indication        = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick           = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        val drawableId = context.resources.getIdentifier(
            "number_$number", "drawable", context.packageName
        )
        if (drawableId != 0) {
            Image(
                painter            = painterResource(id = drawableId),
                contentDescription = number.toString(),
                modifier           = Modifier
                    .fillMaxHeight(0.72f)
                    .wrapContentWidth(),
                colorFilter        = ColorFilter.tint(iconTint),
                contentScale       = ContentScale.Fit
            )
        } else {
            Text(
                text      = number.toString(),
                style     = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize   = 30.sp
                ),
                color     = iconTint,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ── Attempts row ──────────────────────────────────────────────────────────────

@Composable
private fun AttemptsRow(attempts: Int, maxAttempts: Int) {
    val colors = LocalGameColorScheme.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        repeat(maxAttempts) { i ->
            val used  = i < attempts
            // Used dots → wrong color (red = attempt spent); idle → faded accent
            val color by animateColorAsState(
                targetValue   = if (used) colors.wrong else colors.accent.copy(alpha = 0.25f),
                animationSpec = tween(300),
                label         = "dot$i"
            )
            Box(Modifier.size(if (used) 14.dp else 11.dp).background(color, CircleShape))
        }
    }
}