package com.babybloom.presentation.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.babybloom.R
import com.babybloom.domain.model.ActivityContent
import com.babybloom.presentation.viewmodels.ALL_HABITATS
import com.babybloom.presentation.viewmodels.AnswerState
import com.babybloom.presentation.viewmodels.AnimalOption
import com.babybloom.presentation.viewmodels.Habitat
import com.babybloom.presentation.viewmodels.MatchCardState
import com.babybloom.presentation.viewmodels.MatchViewModel

// ── Theme color imports ───────────────────────────────────────────────────────
import com.babybloom.ui.theme.Card3
import com.babybloom.ui.theme.DarkPurple
import com.babybloom.ui.theme.PrimaryPurple
import com.babybloom.ui.theme.AccentPink
import com.babybloom.ui.theme.AccentTeal
import com.babybloom.ui.theme.ErrorRed
import com.babybloom.ui.theme.RevealGold       // Color(0xFFFFD700) — add to Color.kt
import com.babybloom.ui.theme.MatchQuestionBg  // alias of TextFieldBackground — add to Color.kt

// ── Local aliases ─────────────────────────────────────────────────────────────
private val CorrectGreen     = AccentTeal
private val WrongRed         = ErrorRed

// All option cards use warm peach (Card3)
private val OptionColors     = listOf(Card3, Card3, Card3, Card3)
private val OptionBorderIdle = DarkPurple.copy(alpha = 0.25f)

// ── Wiggle animation ──────────────────────────────────────────────────────────
@Composable
private fun wiggleOffset(trigger: Int): Float {
    val offset = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        repeat(4) {
            offset.animateTo( 9f, tween(55))
            offset.animateTo(-9f, tween(55))
        }
        offset.animateTo(0f, tween(55))
    }
    return offset.value
}

// ── Entry point ───────────────────────────────────────────────────────────────
@Composable
fun MatchScreen(
    contentItems: List<ActivityContent>,
    isCalmMode: Boolean,
    configJson: String = "{\"matchType\":\"ANIMAL_TO_HABITAT\"}",
    onCardResult: (contentId: String, isCorrect: Boolean, correct: Int, incorrect: Int, attempts: Int) -> Unit,
    onComplete: (elapsedMs: Long, correctCount: Int) -> Unit,
    viewModel: MatchViewModel = hiltViewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.loadActivity(contentItems, isCalmMode, configJson, onCardResult, onComplete)
    }

    val cardState  by viewModel.cardState.collectAsStateWithLifecycle()
    val wiggleTick by viewModel.wiggleTick.collectAsStateWithLifecycle()

    LaunchedEffect(cardState) {
        val done = cardState as? MatchCardState.Done ?: return@LaunchedEffect
        onComplete(done.elapsedMs, done.correctCount)
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val state = cardState) {
            is MatchCardState.Loading ->
                CircularProgressIndicator(color = PrimaryPurple)
            is MatchCardState.Done ->
                CircularProgressIndicator(color = AccentTeal)
            is MatchCardState.AnimalHabitatCard ->
                AnimalHabitatLayout(state, isCalmMode, wiggleTick) { viewModel.onAnswerSelected(it) }
            is MatchCardState.LetterAnimalCard ->
                LetterAnimalLayout(state, isCalmMode, wiggleTick) { viewModel.onAnswerSelected(it) }
        }
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
        modifier            = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(4.dp))
        TopRow(state.questionIndex, state.totalQuestions, state.attemptsLeft)
        PromptBanner(stringResource(R.string.match_prompt_where_lives))
        AnimalQuestionCard(state, isCalmMode)
        HabitatGrid(
            options     = state.options,
            correctId   = state.correctHabitatId,
            answerState = state.answerState,
            lastWrongId = state.lastWrongId,
            isCalmMode  = isCalmMode,
            wiggleTick  = wiggleTick,
            showWiggle  = state.showCorrectWiggle,
            onSelected  = onSelected
        )
    }
}

@Composable
private fun AnimalQuestionCard(
    state: MatchCardState.AnimalHabitatCard,
    isCalmMode: Boolean
) {
    val context = LocalContext.current
    val mood    = if (isCalmMode) "calm" else "active"
    val path    = "file:///android_asset/learning_content/visual/$mood/${state.animal.contentId}.png"

    val borderColor by animateColorAsState(
        targetValue = when (state.answerState) {
            AnswerState.Correct  -> CorrectGreen
            AnswerState.Wrong    -> WrongRed
            AnswerState.Revealed -> RevealGold
            AnswerState.Idle     -> PrimaryPurple
        },
        animationSpec = tween(300), label = "qBorder"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .border(3.dp, borderColor, RoundedCornerShape(24.dp))
            .background(MatchQuestionBg)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model              = ImageRequest.Builder(context).data(path).build(),
                contentDescription = state.animal.labelAr,
                modifier           = Modifier.size(100.dp),
                contentScale       = ContentScale.Fit
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text       = state.animal.labelAr,
                fontSize   = 28.sp,
                fontWeight = FontWeight.Bold,
                color      = DarkPurple
            )
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
    lastWrongId: String?,
    isCalmMode: Boolean,
    wiggleTick: Int,
    showWiggle: Boolean,
    onSelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        options.chunked(2).forEachIndexed { rowIdx, row ->
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEachIndexed { colIdx, habitat ->
                    val colorIdx  = rowIdx * 2 + colIdx
                    val isCorrect = habitat.id == correctId
                    HabitatOptionCard(
                        habitat     = habitat,
                        isCorrect   = isCorrect,
                        answerState = answerState,
                        lastWrongId = lastWrongId,
                        isCalmMode  = isCalmMode,
                        colorIdx    = colorIdx,
                        wiggleTick  = if (isCorrect && showWiggle) wiggleTick else 0,
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
    lastWrongId: String?,
    isCalmMode: Boolean,
    colorIdx: Int,
    wiggleTick: Int,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context       = LocalContext.current
    val mood          = if (isCalmMode) "calm" else "active"
    val imgFile       = if (isCalmMode) habitat.calmImage else habitat.activeImage
    val habitatPath   = "file:///android_asset/learning_content/visual/$mood/$imgFile"
    val habitatLabel  = stringResource(habitat.labelResId)

    val showCorrectOverlay = isCorrect &&
            (answerState == AnswerState.Correct || answerState == AnswerState.Revealed)
    val isWrongTapped = habitat.id == lastWrongId && answerState == AnswerState.Wrong

    val borderColor by animateColorAsState(
        targetValue = when {
            isCorrect && answerState == AnswerState.Correct  -> CorrectGreen
            isCorrect && answerState == AnswerState.Revealed -> RevealGold
            isWrongTapped                                    -> WrongRed
            else                                             -> OptionBorderIdle
        },
        animationSpec = tween(300), label = "hBorder"
    )

    val shake   = wiggleOffset(wiggleTick)
    val enabled = answerState in listOf(AnswerState.Idle, AnswerState.Wrong)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .graphicsLayer { translationX = shake }
            .clip(RoundedCornerShape(20.dp))
            .border(3.dp, borderColor, RoundedCornerShape(20.dp))
            // FIX: base is always transparent — the image fills the card at all times
            .background(Color.Transparent)
            .clickable(enabled = enabled) { onSelected(habitat.id) }
    ) {
        // Layer 1: habitat photo — ALWAYS visible (alpha = 1f at all times)
        Image(
            painter            = rememberAsyncImagePainter(
                ImageRequest.Builder(context).data(habitatPath).build()
            ),
            contentDescription = habitatLabel,
            modifier           = Modifier.fillMaxSize(),
            contentScale       = ContentScale.Crop
        )

        // Layer 2: correct answer overlay tint
        if (showCorrectOverlay) {
            Box(
                modifier = Modifier.fillMaxSize().background(
                    if (answerState == AnswerState.Revealed) RevealGold.copy(alpha = 0.18f)
                    else CorrectGreen.copy(alpha = 0.12f)
                )
            )
        }

        // Layer 3: wrong tint — only on the tapped wrong card
        if (isWrongTapped) {
            Box(modifier = Modifier.fillMaxSize().background(WrongRed.copy(alpha = 0.22f)))
        }

        // Layer 4: label bar — always dark overlay since image is always showing
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
                )
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text       = habitatLabel,
                fontSize   = 16.sp,
                fontWeight = FontWeight.Bold,
                // FIX: always white since image is always behind the label
                color      = Color.White
            )
        }

        CornerBadge(isCorrect = isCorrect, isWrongTapped = isWrongTapped, answerState = answerState)
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
        modifier            = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(4.dp))
        TopRow(state.questionIndex, state.totalQuestions, state.attemptsLeft)
        PromptBanner(stringResource(R.string.match_prompt_choose_animal))
        LetterQuestionCard(state)
        AnimalGrid(
            options     = state.options,
            correctId   = state.correctAnimalId,
            answerState = state.answerState,
            lastWrongId = state.lastWrongId,
            isCalmMode  = isCalmMode,
            wiggleTick  = wiggleTick,
            showWiggle  = state.showCorrectWiggle,
            onSelected  = onSelected
        )
    }
}

@Composable
private fun LetterQuestionCard(state: MatchCardState.LetterAnimalCard) {
    val context = LocalContext.current
    val resId   = context.resources.getIdentifier(
        state.letter.contentId, "drawable", context.packageName
    )

    val borderColor by animateColorAsState(
        targetValue = when (state.answerState) {
            AnswerState.Correct  -> CorrectGreen
            AnswerState.Wrong    -> WrongRed
            AnswerState.Revealed -> RevealGold
            AnswerState.Idle     -> PrimaryPurple
        },
        animationSpec = tween(300), label = "lBorder"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .border(3.dp, borderColor, RoundedCornerShape(24.dp))
            .background(MatchQuestionBg)
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
            Text(
                text       = state.letter.labelAr,
                fontSize   = 52.sp,
                fontWeight = FontWeight.Black,
                color      = DarkPurple
            )
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
    lastWrongId: String?,
    isCalmMode: Boolean,
    wiggleTick: Int,
    showWiggle: Boolean,
    onSelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        options.chunked(2).forEachIndexed { rowIdx, row ->
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEachIndexed { colIdx, option ->
                    val colorIdx  = rowIdx * 2 + colIdx
                    val isCorrect = option.entity.id == correctId
                    AnimalOptionCard(
                        option      = option,
                        isCorrect   = isCorrect,
                        answerState = answerState,
                        lastWrongId = lastWrongId,
                        isCalmMode  = isCalmMode,
                        colorIdx    = colorIdx,
                        wiggleTick  = if (isCorrect && showWiggle) wiggleTick else 0,
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
    lastWrongId: String?,
    isCalmMode: Boolean,
    colorIdx: Int,
    wiggleTick: Int,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context       = LocalContext.current
    val mood          = if (isCalmMode) "calm" else "active"
    val animalPath    = "file:///android_asset/learning_content/visual/$mood/${option.entity.id}.png"
    val habitat       = ALL_HABITATS.firstOrNull { it.id == option.habitatId } ?: ALL_HABITATS.first()
    val habitatFile   = if (isCalmMode) habitat.calmImage else habitat.activeImage
    val habitatPath   = "file:///android_asset/learning_content/visual/$mood/$habitatFile"
    val baseColor     = OptionColors[colorIdx % 4]
    val isWrongTapped = option.entity.id == lastWrongId && answerState == AnswerState.Wrong
    val showHabitatBg = isCorrect &&
            (answerState == AnswerState.Correct || answerState == AnswerState.Revealed)

    val borderColor by animateColorAsState(
        targetValue = when {
            isCorrect && answerState == AnswerState.Correct  -> CorrectGreen
            isCorrect && answerState == AnswerState.Revealed -> RevealGold
            isWrongTapped                                    -> WrongRed
            else                                             -> OptionBorderIdle
        },
        animationSpec = tween(300), label = "aBorder"
    )

    val habitatAlpha by animateFloatAsState(
        targetValue   = if (showHabitatBg) 1f else 0f,
        animationSpec = tween(400), label = "habFade"
    )

    val shake   = wiggleOffset(wiggleTick)
    val enabled = answerState in listOf(AnswerState.Idle, AnswerState.Wrong)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .graphicsLayer { translationX = shake }
            .clip(RoundedCornerShape(20.dp))
            .border(3.dp, borderColor, RoundedCornerShape(20.dp))
            .background(baseColor)
            .clickable(enabled = enabled) { onSelected(option.entity.id) }
    ) {
        // Layer 1: habitat bg — fades in on correct answer
        Image(
            painter            = rememberAsyncImagePainter(
                ImageRequest.Builder(context).data(habitatPath).build()
            ),
            contentDescription = null,
            modifier           = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = habitatAlpha },
            contentScale       = ContentScale.Crop
        )

        // Layer 2: light tint over photo (only when photo is visible)
        if (showHabitatBg) {
            Box(
                modifier = Modifier.fillMaxSize().background(
                    if (answerState == AnswerState.Revealed) RevealGold.copy(alpha = 0.18f)
                    else CorrectGreen.copy(alpha = 0.12f)
                )
            )
        }

        // Layer 3: wrong tint — only on the tapped wrong card
        if (isWrongTapped) {
            Box(modifier = Modifier.fillMaxSize().background(WrongRed.copy(alpha = 0.22f)))
        }

        // Layer 4: animal image
        AsyncImage(
            model              = ImageRequest.Builder(context).data(animalPath).build(),
            contentDescription = option.entity.labelAr,
            modifier           = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.72f)
                .align(Alignment.TopCenter)
                .padding(8.dp),
            contentScale       = ContentScale.Fit
        )

        // Layer 5: label bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    color = if (showHabitatBg) Color.Black.copy(alpha = 0.55f)
                    else DarkPurple.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
                )
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text       = option.entity.labelAr,
                fontSize   = 15.sp,
                fontWeight = FontWeight.Bold,
                color      = if (showHabitatBg) Color.White else DarkPurple
            )
        }

        // Layer 6: corner badge
        CornerBadge(isCorrect = isCorrect, isWrongTapped = isWrongTapped, answerState = answerState)
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Shared composables
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun TopRow(questionIndex: Int, totalQuestions: Int, attemptsLeft: Int) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(
            text       = "${questionIndex + 1} / $totalQuestions",
            fontSize   = 18.sp,
            fontWeight = FontWeight.Bold,
            color      = DarkPurple
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { i ->
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(if (i < attemptsLeft) AccentPink else DarkPurple.copy(alpha = 0.15f))
                )
            }
        }
    }
}

@Composable
private fun PromptBanner(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(2.dp, PrimaryPurple.copy(alpha = 0.35f), RoundedCornerShape(50))
            .background(PrimaryPurple.copy(alpha = 0.08f))
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PrimaryPurple)
    }
}

@Composable
private fun FeedbackIcon(answerState: AnswerState) {
    when (answerState) {
        AnswerState.Correct  -> Icon(Icons.Default.Check, null, tint = CorrectGreen, modifier = Modifier.size(32.dp))
        AnswerState.Wrong    -> Icon(Icons.Default.Close, null, tint = WrongRed,     modifier = Modifier.size(32.dp))
        AnswerState.Revealed -> Icon(Icons.Default.Check, null, tint = RevealGold,   modifier = Modifier.size(32.dp))
        AnswerState.Idle     -> Spacer(Modifier.size(32.dp))
    }
}

@Composable
private fun BoxScope.CornerBadge(
    isCorrect: Boolean,
    isWrongTapped: Boolean,
    answerState: AnswerState
) {
    if (answerState == AnswerState.Idle) return
    val showGreen = isCorrect && answerState == AnswerState.Correct
    val showGold  = isCorrect && answerState == AnswerState.Revealed
    val showRed   = isWrongTapped
    if (!showGreen && !showGold && !showRed) return

    val bg = when { showGreen -> CorrectGreen; showGold -> RevealGold; else -> WrongRed }

    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(6.dp)
            .size(26.dp)
            .clip(RoundedCornerShape(50))
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector        = if (showRed) Icons.Default.Close else Icons.Default.Check,
            contentDescription = null,
            tint               = Color.White,
            modifier           = Modifier.size(16.dp)
        )
    }
}