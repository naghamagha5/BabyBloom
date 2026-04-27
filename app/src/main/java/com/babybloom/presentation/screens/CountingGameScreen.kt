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
import com.babybloom.ui.theme.DarkPurple
import kotlin.math.cos
import kotlin.math.sin

// ── Palette ───────────────────────────────────────────────────────────────────
private val ActiveCardBg     = Color(0xFFFFF8E8)
private val ActiveCardBorder = Color(0xFFFFB347)
private val CalmCardBg       = Color(0xFFE8F4F0)
private val CalmCardBorder   = Color(0xFFB2CFCA)
private val CorrectGreen     = Color(0xFF4CAF50)
private val CorrectDark      = Color(0xFF388E3C)
private val WrongRed         = Color(0xFFF44336)
private val WrongDark        = Color(0xFFD32F2F)
private val GoldColor        = Color(0xFFFFD700)

private val ACTIVE_SHAPE_COLORS = listOf(
    Color(0xFFFF8A65), Color(0xFF9C27B0), Color(0xFF2196F3),
    Color(0xFF4CAF50), Color(0xFFE91E63), Color(0xFFFFB300)
)
private val CALM_SHAPE_COLORS = listOf(
    Color(0xFF5BB89B), Color(0xFF9575CD), Color(0xFF4FC3F7),
    Color(0xFF80CBC4), Color(0xFFEF9A9A), Color(0xFFFFCC80)
)

// ── Row layout lookup ─────────────────────────────────────────────────────────
private fun rowLayout(count: Int): List<List<Int>> = when (count) {
    1    -> listOf(listOf(0))
    2    -> listOf(listOf(0, 1))
    else -> listOf(listOf(0, 1, 2))   // max 3 for level 1
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

@Composable
private fun FireworksOverlay() {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(1400, easing = LinearEasing))
    }
    val p = progress.value
    Canvas(modifier = Modifier.fillMaxSize()) {
        BURSTS.forEach { b ->
            val cx     = b.cxFrac * size.width
            val cy     = b.cyFrac * size.height
            val expand = (p / 0.6f).coerceIn(0f, 1f)
            val alpha  = if (p > 0.6f) 1f - (p - 0.6f) / 0.4f else 1f
            val radius = expand * b.maxRadius
            repeat(b.particleCount) { i ->
                val rad = Math.toRadians((360f / b.particleCount * i).toDouble())
                val px  = cx + (radius * cos(rad)).toFloat()
                val py  = cy + (radius * sin(rad)).toFloat()
                drawCircle(
                    color  = b.color.copy(alpha = alpha.coerceIn(0f, 1f)),
                    radius = 10f + (1f - expand) * 8f,
                    center = Offset(px, py)
                )
                if (expand < 0.7f) {
                    val trailR = radius * 0.3f
                    drawLine(
                        color       = b.color.copy(alpha = (alpha * 0.5f).coerceIn(0f, 1f)),
                        start       = Offset(cx + (trailR * cos(rad)).toFloat(), cy + (trailR * sin(rad)).toFloat()),
                        end         = Offset(px, py),
                        strokeWidth = 3f
                    )
                }
            }
            val flash = (1f - expand) * 40f + 5f
            if (flash > 0f) drawCircle(
                color  = Color.White.copy(alpha = (alpha * (1f - expand)).coerceIn(0f, 1f)),
                radius = flash,
                center = Offset(cx, cy)
            )
        }
    }
}

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
    LaunchedEffect(currentItem.contentId) {
        viewModel.loadGame(currentItem, difficultyLevel, activityId, roundIndex)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        when (val s = uiState) {
            is CountingGameUiState.Loading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            is CountingGameUiState.Playing -> {
                CountingGameContent(
                    state            = s,
                    isCalmMode       = isCalmMode,
                    viewModel        = viewModel,
                    onAnswerSelected = { viewModel.onAnswerSelected(it, onComplete) }
                )
                if (s.showCelebration) FireworksOverlay()
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
            color     = DarkPurple
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
    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(if (isCalmMode) CalmCardBg else ActiveCardBg)
            .border(2.dp, if (isCalmMode) CalmCardBorder else ActiveCardBorder, RoundedCornerShape(24.dp))
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

    // Bounce in ALL 3 rounds
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

// ── Answer button with Arabic number drawable ─────────────────────────────────

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
    val context = LocalContext.current

    val animBg by animateColorAsState(
        targetValue = when {
            isGoldHint                       -> GoldColor
            isSelected && isCorrect == true  -> CorrectGreen
            isSelected && isCorrect == false -> WrongRed
            else                             -> Color(0xFFF5F2FF)
        },
        animationSpec = tween(280), label = "bg"
    )
    val animBorder by animateColorAsState(
        targetValue = when {
            isGoldHint || (isSelected && isCorrect == true)  -> CorrectDark
            isSelected && isCorrect == false                 -> WrongDark
            else                                             -> Color(0xFFDDD5F3)
        },
        animationSpec = tween(280), label = "border"
    )

    // Tint: white when selected/gold, purple otherwise
    val iconTint = when {
        isGoldHint || (isSelected && isCorrect != null) -> Color.White
        else                                            -> DarkPurple
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
        // ── Arabic number drawable ────────────────────────────────────────
        // Uses number_1.xml, number_2.xml … number_6.xml from res/drawable
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
            // Fallback to text if drawable not found
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
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        repeat(maxAttempts) { i ->
            val used  = i < attempts
            val color by animateColorAsState(
                targetValue   = if (used) WrongRed else MaterialTheme.colorScheme.outlineVariant,
                animationSpec = tween(300), label = "dot$i"
            )
            Box(Modifier.size(if (used) 14.dp else 11.dp).background(color, CircleShape))
        }
    }
}