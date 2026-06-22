package com.babybloom.presentation.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
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
import com.babybloom.ui.theme.LocalGameColorScheme
import com.babybloom.util.AssetPathResolver

// ── Layout tuning — adjust these to taste ────────────────────────────────────
private val TITLE_TO_BOX_SPACING    = 50.dp
private val BOX_TO_CHOICES_SPACING  = 50.dp
private val CHOICE_ROW_SPACING      = 30.dp
private val CHOICES_TO_DOTS_SPACING = 50.dp
private val ANSWER_BUTTON_HEIGHT    = 90.dp

// ── Image size scales ─────────────────────────────────────────────────────────
private const val ANIMAL_SIZE_SCALE = 1.45f
private const val SHAPE_SIZE_SCALE  = 1.0f

// ── Row layout ────────────────────────────────────────────────────────────────
private fun rowLayout(count: Int): List<List<Int>> =
    (0 until count.coerceIn(1, 10)).toList().chunked(3)

// ── Card height ───────────────────────────────────────────────────────────────
private fun cardHeightFor(count: Int): Dp {
    val rows = (count + 2) / 3
    return when (rows) {
        1    -> 120.dp
        2    -> 205.dp
        3    -> 270.dp
        else -> 325.dp
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
    isTest         : Boolean = false,
    onComplete     : (isCorrect: Boolean, elapsedMs: Long, attempts: Int) -> Unit,
    viewModel      : CountingGameViewModel = hiltViewModel()
) {
    val colors = LocalGameColorScheme.current

    // Pass onComplete into loadGame so the timer expiry path can also call it
    LaunchedEffect(currentItem.contentId, activityId, roundIndex, difficultyLevel, isTest) {
        viewModel.loadGame(currentItem, difficultyLevel, activityId, roundIndex, isTest, onComplete)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        when (val s = uiState) {
            is CountingGameUiState.Loading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.accent)
                }
            is CountingGameUiState.Playing -> {
                if (s.contentId != currentItem.contentId || s.roundIndex != roundIndex) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.accent)
                    }
                } else {
                    key(s.contentId, s.roundIndex) {
                        CountingGameContent(
                            state            = s,
                            isCalmMode       = isCalmMode,
                            viewModel        = viewModel,
                            onAnswerSelected = { viewModel.onAnswerSelected(it, onComplete) }
                        )
                        if (s.showCelebration) GoodJobPopup(coverage = -1f)
                    }
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
    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Title pinned to top — no top spacer ───────────────────────────
        Text(
            text      = state.subjectQuestionAr,
            style     = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize   = 26.sp
            ),
            textAlign = TextAlign.Center,
            color     = LocalGameColorScheme.current.accent
        )

        Spacer(Modifier.height(TITLE_TO_BOX_SPACING))

        // ── Objects card ──────────────────────────────────────────────────
        ObjectsCard(
            state      = state,
            isCalmMode = isCalmMode,
            viewModel  = viewModel,
            modifier   = Modifier
                .fillMaxWidth()
                .height(cardHeightFor(state.targetCount))
        )

        Spacer(Modifier.height(BOX_TO_CHOICES_SPACING))

        // ── Answer buttons — always tappable, animation no longer blocks ──
        // ── Answer buttons ────────────────────────────────────────────────────────
        if (!state.isTest) {
            // TEST MODE: one big correct-answer button, locked until counting finishes
            val enabled = !state.isAnimating && state.selectedAnswer == null
            AnswerButton(
                number     = state.targetCount,
                isSelected = state.selectedAnswer == state.targetCount,
                isCorrect  = if (state.selectedAnswer != null) state.isCorrect else null,
                isGoldHint = false,
                isShaking  = false,
                isEnabled  = enabled,
                onClick    = { onAnswerSelected(state.targetCount) },
                modifier   = Modifier
                    .fillMaxWidth()
                    .height(ANSWER_BUTTON_HEIGHT * 1.6f)          // visibly larger
            )
        } else {
            // NORMAL MODE: 2-column grid of shuffled choices
            val buttonsEnabled = state.selectedAnswer == null

            Column(
                modifier            = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(CHOICE_ROW_SPACING)
            ) {
                state.choices.chunked(2).forEach { row ->
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        row.forEach { choice ->
                            key(state.contentId, state.roundIndex, choice) {
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

        Spacer(Modifier.height(CHOICES_TO_DOTS_SPACING))


        // ── Attempts dots ─────────────────────────────────────────────────
        AttemptsRow(
            attempts    = state.attempts,
            maxAttempts = state.maxAttempts,
            lastCorrect = state.isCorrect == true
        )

        // ── Bottom elastic spacer — fills remaining space ─────────────────
        Spacer(Modifier.weight(1f))
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
            .background(colors.background)
            .background(colors.accent.copy(alpha = 0.08f))
            .border(2.dp, colors.accent, RoundedCornerShape(24.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        val rows    = rowLayout(state.targetCount)
        val numRows = rows.size
        val spacing = 8.dp

        val cellW     = (maxWidth  - spacing * 2) / 3
        val cellH     = (maxHeight - spacing * (numRows - 1)) / numRows
        val imageSize = minOf(cellW, cellH).coerceAtLeast(36.dp)

        Column(
            modifier            = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            rows.forEach { rowIndices ->
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally),
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
    val colors  = LocalGameColorScheme.current

    val isPulsing  = index == state.countingStep
    val pulseScale by animateFloatAsState(
        targetValue   = if (isPulsing) 1.35f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessLow
        ),
        label = "pulse_$index"
    )

    val typeScale = when (state.gameType) {
        CountGameType.ANIMAL -> ANIMAL_SIZE_SCALE
        CountGameType.SHAPE  -> SHAPE_SIZE_SCALE
    }

    Box(
        modifier         = Modifier
            .size(imageSize)
            .scale(pulseScale * typeScale),
        contentAlignment = Alignment.Center
    ) {
        when (state.gameType) {
            CountGameType.ANIMAL -> {
                val uri = AssetPathResolver.androidAssetUri(
                    AssetPathResolver.animalImagePathFor(state.subjectId, isCalmMode)
                )
                AsyncImage(
                    model              = ImageRequest.Builder(context).data(uri).build(),
                    contentDescription = state.subjectLabelAr,
                    modifier           = Modifier.fillMaxSize(),
                    contentScale       = ContentScale.Fit
                )
            }
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
                            colorFilter        = ColorFilter.tint(colors.accent)
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

    val animBg by animateColorAsState(
        targetValue = when {
            isGoldHint                       -> colors.hint
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

    val btnScale by animateFloatAsState(
        targetValue   = if ((isSelected && isCorrect == true) || isGoldHint) 1.08f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label         = "btnScale"
    )

    Box(
        modifier = modifier
            .height(ANSWER_BUTTON_HEIGHT)
            .graphicsLayer { scaleX = btnScale; scaleY = btnScale; translationX = shakeAnim.value }
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
private fun AttemptsRow(
    attempts   : Int,
    maxAttempts: Int,
    lastCorrect: Boolean        // true when the last recorded attempt was a correct answer
) {
    val colors = LocalGameColorScheme.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        repeat(maxAttempts) { i ->
            val filled        = i < attempts
            // Only the dot for the correct attempt is green; all wrong/timeout dots are red
            val isCorrectDot  = filled && i == attempts - 1 && lastCorrect
            val color by animateColorAsState(
                targetValue = when {
                    isCorrectDot -> colors.correct
                    filled       -> colors.wrong
                    else         -> colors.accent.copy(alpha = 0.25f)
                },
                animationSpec = tween(300),
                label         = "dot$i"
            )
            Box(Modifier.size(if (filled) 14.dp else 11.dp).background(color, CircleShape))
        }
    }
}
