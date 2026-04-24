package com.babybloom.presentation.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.babybloom.domain.model.ActivityContent
import com.babybloom.presentation.viewmodels.AnswerState
import com.babybloom.presentation.viewmodels.AnimalOption
import com.babybloom.presentation.viewmodels.Habitat
import com.babybloom.presentation.viewmodels.MatchCardState
import com.babybloom.presentation.viewmodels.MatchViewModel
import com.babybloom.presentation.viewmodels.NumberOption

// ── Palette ───────────────────────────────────────────────────────────────────
private val ActiveCardColor  = Color(0xFFFFF0B3)
private val CalmCardColor    = Color(0xFFE8F4F0)
private val ActiveCardBorder = Color(0xFFFFB347)
private val CalmCardBorder   = Color(0xFFB2CFCA)
private val CorrectGreen     = Color(0xFF4CAF50)
private val WrongRed         = Color(0xFFE53935)
private val GlowYellow       = Color(0xFFFFE033)

// ── Wiggle animation ──────────────────────────────────────────────────────────
@Composable
private fun wiggleOffset(trigger: Int): Float {
    val offset = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        repeat(4) {
            offset.animateTo( 8f, tween(60))
            offset.animateTo(-8f, tween(60))
        }
        offset.animateTo(0f, tween(60))
    }
    return offset.value
}

// ── Pulsing glow animation ────────────────────────────────────────────────────
@Composable
private fun glowAlpha(isGlowing: Boolean): Float {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(isGlowing) {
        if (isGlowing) {
            alpha.animateTo(1f, tween(200))
        } else {
            alpha.animateTo(0f, tween(200))
        }
    }
    return alpha.value
}

// ── Entry point ───────────────────────────────────────────────────────────────
@Composable
fun MatchScreen(
    contentItems: List<ActivityContent>,
    isCalmMode: Boolean,
    configJson: String = "{\"matchType\":\"ANIMAL_TO_HABITAT\"}",
    onComplete: (elapsedMs: Long) -> Unit,
    viewModel: MatchViewModel = hiltViewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.loadActivity(contentItems, isCalmMode, configJson, onComplete)
    }

    val cardState  by viewModel.cardState.collectAsStateWithLifecycle()
    val wiggleTick by viewModel.wiggleTick.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (val state = cardState) {
            is MatchCardState.Loading ->
                CircularProgressIndicator()

            is MatchCardState.AnimalHabitatCard ->
                AnimalHabitatLayout(state, isCalmMode, wiggleTick) { viewModel.onAnswerSelected(it) }

            is MatchCardState.LetterAnimalCard ->
                LetterAnimalLayout(state, isCalmMode, wiggleTick) { viewModel.onAnswerSelected(it) }

            is MatchCardState.CountNumberCard ->
                CountNumberLayout(state, isCalmMode, wiggleTick) { viewModel.onAnswerSelected(it) }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// GAME 3 — Count → Number
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun CountNumberLayout(
    state: MatchCardState.CountNumberCard,
    isCalmMode: Boolean,
    wiggleTick: Int,
    onSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(4.dp))
        QuestionProgress(state.questionIndex + 1, state.totalQuestions)
        PromptBanner("عُدَّ وَاخْتَر العَدَد")

        // Animal grid question card
        CountAnimalGrid(
            animalId          = state.animalId,
            count             = state.count,
            isCalmMode        = isCalmMode,
            countingGlowIndex = state.countingGlowIndex,
            answerState       = state.answerState
        )

        // Number picker options
        NumberOptionGrid(
            options     = state.options,
            correctNum  = state.correctNumber,
            answerState = state.answerState,
            wiggleTick  = if (state.showCorrectWiggle) wiggleTick else 0,
            onSelected  = onSelected
        )
    }
}

// ── Animal display grid (question) ────────────────────────────────────────────
@Composable
private fun CountAnimalGrid(
    animalId: String,
    count: Int,
    isCalmMode: Boolean,
    countingGlowIndex: Int,
    answerState: AnswerState
) {
    val context   = LocalContext.current
    val mood      = if (isCalmMode) "calm" else "active"
    val cardColor = if (isCalmMode) CalmCardColor else ActiveCardColor
    val border    = if (isCalmMode) CalmCardBorder else ActiveCardBorder

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .border(3.dp, border, RoundedCornerShape(24.dp))
            .background(cardColor)
            .padding(16.dp)
    ) {
        // Wrap animals in rows of 5
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val rows = (1..count).chunked(5)
            rows.forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    row.forEachIndexed { rowIndex, globalIndex ->
                        val realIndex = (rows.indexOf(row) * 5) + rowIndex
                        val isGlowing = realIndex == countingGlowIndex
                        val glow = glowAlpha(isGlowing)

                        val path = "file:///android_asset/learning_content/visual/$mood/$animalId.png"

                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .graphicsLayer {
                                    // scale up slightly when glowing
                                    scaleX = if (isGlowing) 1.15f else 1f
                                    scaleY = if (isGlowing) 1.15f else 1f
                                }
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(path).build(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                            // glow overlay
                            if (glow > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            GlowYellow.copy(alpha = glow * 0.4f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .border(
                                            2.dp,
                                            GlowYellow.copy(alpha = glow),
                                            RoundedCornerShape(8.dp)
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Number option grid (answers) ──────────────────────────────────────────────
@Composable
private fun NumberOptionGrid(
    options: List<NumberOption>,
    correctNum: Int,
    answerState: AnswerState,
    wiggleTick: Int,
    onSelected: (String) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        options.chunked(2).forEach { row ->
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { option ->
                    NumberOptionCard(
                        option      = option,
                        isCorrect   = option.value == correctNum,
                        answerState = answerState,
                        wiggleTick  = if (option.value == correctNum) wiggleTick else 0,
                        onSelected  = onSelected,
                        modifier    = Modifier.weight(1f)
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun NumberOptionCard(
    option: NumberOption,
    isCorrect: Boolean,
    answerState: AnswerState,
    wiggleTick: Int,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resId   = context.resources.getIdentifier(
        "number_${option.value}", "drawable", context.packageName
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            answerState == AnswerState.Idle -> ActiveCardBorder
            isCorrect                       -> CorrectGreen
            else                            -> ActiveCardBorder
        },
        animationSpec = tween(300), label = "numBorder"
    )

    val bgColor by animateColorAsState(
        targetValue = when {
            answerState != AnswerState.Idle && isCorrect -> CorrectGreen.copy(alpha = 0.15f)
            else -> ActiveCardColor
        },
        animationSpec = tween(300), label = "numBg"
    )

    val shake = wiggleOffset(wiggleTick)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .graphicsLayer { translationX = shake }
            .clip(RoundedCornerShape(20.dp))
            .border(3.dp, borderColor, RoundedCornerShape(20.dp))
            .background(bgColor)
            .clickable(enabled = answerState == AnswerState.Idle) {
                onSelected(option.value.toString())
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (resId != 0) {
                Image(
                    painter            = painterResource(id = resId),
                    contentDescription = option.labelAr,
                    modifier           = Modifier.size(64.dp),
                    contentScale       = ContentScale.Fit
                )
            }
            Text(
                text       = option.labelAr,
                fontSize   = 28.sp,
                fontWeight = FontWeight.Black
            )
        }

        CornerBadge(isCorrect = isCorrect, answerState = answerState)
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// GAME 1 — Animal → Habitat
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun AnimalHabitatLayout(
    state: MatchCardState.AnimalHabitatCard,
    isCalmMode: Boolean,
    wiggleTick: Int,
    onSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(4.dp))
        QuestionProgress(state.questionIndex + 1, state.totalQuestions)
        PromptBanner("أَيْنَ يَعِيش؟")
        AnimalQuestionCard(state, isCalmMode)
        HabitatGrid(
            options     = state.options,
            correctId   = state.correctHabitatId,
            answerState = state.answerState,
            isCalmMode  = isCalmMode,
            wiggleTick  = if (state.showCorrectWiggle) wiggleTick else 0,
            onSelected  = onSelected
        )
    }
}

@Composable
private fun AnimalQuestionCard(
    state: MatchCardState.AnimalHabitatCard,
    isCalmMode: Boolean
) {
    val context    = LocalContext.current
    val mood       = if (isCalmMode) "calm" else "active"
    val assetPath  = "file:///android_asset/learning_content/visual/$mood/${state.animal.contentId}.png"
    val cardColor  = if (isCalmMode) CalmCardColor  else ActiveCardColor
    val cardBorder = if (isCalmMode) CalmCardBorder else ActiveCardBorder

    val borderColor by animateColorAsState(
        targetValue = when (state.answerState) {
            AnswerState.Correct -> CorrectGreen
            AnswerState.Wrong   -> WrongRed
            AnswerState.Idle    -> cardBorder
        },
        animationSpec = tween(300), label = "animalBorder"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .border(3.dp, borderColor, RoundedCornerShape(24.dp))
            .background(cardColor)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model        = ImageRequest.Builder(context).data(assetPath).build(),
                contentDescription = state.animal.labelAr,
                modifier     = Modifier.size(100.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.width(12.dp))
            Text(state.animal.labelAr, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            FeedbackIcon(state.answerState)
        }
    }
}

@Composable
private fun HabitatGrid(
    options: List<Habitat>,
    correctId: String,
    answerState: AnswerState,
    isCalmMode: Boolean,
    wiggleTick: Int,
    onSelected: (String) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        options.chunked(2).forEach { row ->
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { habitat ->
                    HabitatOptionCard(
                        habitat     = habitat,
                        isCorrect   = habitat.id == correctId,
                        answerState = answerState,
                        isCalmMode  = isCalmMode,
                        wiggleTick  = if (habitat.id == correctId) wiggleTick else 0,
                        onSelected  = onSelected,
                        modifier    = Modifier.weight(1f)
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HabitatOptionCard(
    habitat: Habitat,
    isCorrect: Boolean,
    answerState: AnswerState,
    isCalmMode: Boolean,
    wiggleTick: Int,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context     = LocalContext.current
    val mood        = if (isCalmMode) "calm" else "active"
    val imgFile     = if (isCalmMode) habitat.calmImage else habitat.activeImage
    val habitatPath = "file:///android_asset/learning_content/visual/$mood/$imgFile"
    val baseBorder  = if (isCalmMode) CalmCardBorder else ActiveCardBorder

    val borderColor by animateColorAsState(
        targetValue = when {
            answerState == AnswerState.Idle -> baseBorder
            isCorrect                       -> CorrectGreen
            else                            -> baseBorder
        },
        animationSpec = tween(300), label = "habBorder"
    )

    val shake = wiggleOffset(wiggleTick)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .graphicsLayer { translationX = shake }
            .clip(RoundedCornerShape(20.dp))
            .border(3.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable(enabled = answerState == AnswerState.Idle) { onSelected(habitat.id) }
    ) {
        Image(
            painter = rememberAsyncImagePainter(
                ImageRequest.Builder(context).data(habitatPath).build()
            ),
            contentDescription = habitat.labelAr,
            modifier     = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        if (answerState != AnswerState.Idle) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isCorrect) CorrectGreen.copy(alpha = 0.3f)
                        else Color.Transparent
                    )
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Color.Black.copy(alpha = 0.45f),
                    RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
                )
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(habitat.labelAr, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        CornerBadge(isCorrect = isCorrect, answerState = answerState)
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// GAME 2 — Letter → Animal
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun LetterAnimalLayout(
    state: MatchCardState.LetterAnimalCard,
    isCalmMode: Boolean,
    wiggleTick: Int,
    onSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(4.dp))
        QuestionProgress(state.questionIndex + 1, state.totalQuestions)
        PromptBanner("اخْتَر الحَيَوَان الَّذِي يَبْدَأ بِالحَرْف")
        LetterQuestionCard(state, isCalmMode)
        AnimalGrid(
            options     = state.options,
            correctId   = state.correctAnimalId,
            answerState = state.answerState,
            isCalmMode  = isCalmMode,
            wiggleTick  = if (state.showCorrectWiggle) wiggleTick else 0,
            onSelected  = onSelected
        )
    }
}

@Composable
private fun LetterQuestionCard(
    state: MatchCardState.LetterAnimalCard,
    isCalmMode: Boolean
) {
    val context    = LocalContext.current
    val cardColor  = if (isCalmMode) CalmCardColor  else ActiveCardColor
    val cardBorder = if (isCalmMode) CalmCardBorder else ActiveCardBorder

    val borderColor by animateColorAsState(
        targetValue = when (state.answerState) {
            AnswerState.Correct -> CorrectGreen
            AnswerState.Wrong   -> WrongRed
            AnswerState.Idle    -> cardBorder
        },
        animationSpec = tween(300), label = "letterBorder"
    )

    val resId = context.resources.getIdentifier(
        state.letter.contentId, "drawable", context.packageName
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .border(3.dp, borderColor, RoundedCornerShape(24.dp))
            .background(cardColor)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (resId != 0) {
                Image(
                    painter            = painterResource(id = resId),
                    contentDescription = state.letter.labelAr,
                    modifier           = Modifier.size(90.dp),
                    contentScale       = ContentScale.Fit
                )
            }
            Spacer(Modifier.width(20.dp))
            Text(state.letter.labelAr, fontSize = 48.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(12.dp))
            FeedbackIcon(state.answerState)
        }
    }
}

@Composable
private fun AnimalGrid(
    options: List<AnimalOption>,
    correctId: String,
    answerState: AnswerState,
    isCalmMode: Boolean,
    wiggleTick: Int,
    onSelected: (String) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        options.chunked(2).forEach { row ->
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { option ->
                    AnimalOptionCard(
                        option      = option,
                        isCorrect   = option.entity.id == correctId,
                        answerState = answerState,
                        isCalmMode  = isCalmMode,
                        wiggleTick  = if (option.entity.id == correctId) wiggleTick else 0,
                        onSelected  = onSelected,
                        modifier    = Modifier.weight(1f)
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AnimalOptionCard(
    option: AnimalOption,
    isCorrect: Boolean,
    answerState: AnswerState,
    isCalmMode: Boolean,
    wiggleTick: Int,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context    = LocalContext.current
    val mood       = if (isCalmMode) "calm" else "active"
    val animalPath = "file:///android_asset/learning_content/visual/$mood/${option.entity.id}.png"
    val cardBg     = if (isCalmMode) CalmCardColor  else ActiveCardColor
    val baseBorder = if (isCalmMode) CalmCardBorder else ActiveCardBorder

    val borderColor by animateColorAsState(
        targetValue = when {
            answerState == AnswerState.Idle -> baseBorder
            isCorrect                       -> CorrectGreen
            else                            -> baseBorder
        },
        animationSpec = tween(300), label = "animalCardBorder"
    )

    val shake = wiggleOffset(wiggleTick)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .graphicsLayer { translationX = shake }
            .clip(RoundedCornerShape(20.dp))
            .border(3.dp, borderColor, RoundedCornerShape(20.dp))
            .background(cardBg)
            .clickable(enabled = answerState == AnswerState.Idle) {
                onSelected(option.entity.id)
            }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(animalPath).build(),
            contentDescription = option.entity.labelAr,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .align(Alignment.TopCenter)
                .padding(10.dp),
            contentScale = ContentScale.Fit
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Color.Black.copy(alpha = 0.45f),
                    RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
                )
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text       = option.entity.labelAr,
                fontSize   = 15.sp,
                fontWeight = FontWeight.Bold,
                color      = Color.White
            )
        }

        CornerBadge(isCorrect = isCorrect, answerState = answerState)
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Shared small composables
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun PromptBanner(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(1.5.dp, Color.Black.copy(alpha = 0.15f), RoundedCornerShape(50))
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun QuestionProgress(current: Int, total: Int) {
    Text(
        text       = "$current / $total",
        fontSize   = 18.sp,
        fontWeight = FontWeight.Bold,
        color      = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun FeedbackIcon(answerState: AnswerState) {
    when (answerState) {
        AnswerState.Correct -> Icon(Icons.Default.Check, null, tint = CorrectGreen, modifier = Modifier.size(32.dp))
        AnswerState.Wrong   -> Icon(Icons.Default.Close, null, tint = WrongRed,    modifier = Modifier.size(32.dp))
        AnswerState.Idle    -> Spacer(Modifier.size(32.dp))
    }
}

@Composable
private fun BoxScope.CornerBadge(isCorrect: Boolean, answerState: AnswerState) {
    if (answerState == AnswerState.Idle) return
    if (!isCorrect && answerState == AnswerState.Correct) return
    val show = isCorrect || (!isCorrect && answerState == AnswerState.Wrong)
    if (!show) return

    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(6.dp)
            .size(26.dp)
            .clip(RoundedCornerShape(50))
            .background(if (isCorrect) CorrectGreen else WrongRed),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector        = if (isCorrect) Icons.Default.Check else Icons.Default.Close,
            contentDescription = null,
            tint               = Color.White,
            modifier           = Modifier.size(16.dp)
        )
    }
}