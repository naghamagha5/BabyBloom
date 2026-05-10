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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
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
import com.babybloom.ui.theme.LocalGameColorScheme
import com.babybloom.ui.theme.RevealGold       // kept — semantic "revealed answer" color
import com.babybloom.ui.theme.TraceBadgeBorder // same banner as TraceScreen
import com.babybloom.ui.theme.TraceBadgeText   // same banner as TraceScreen
import com.babybloom.util.ImageAsset

// ─────────────────────────────────────────────────────────────────────────────
// MatchScreen.kt
//
// All game colors come from LocalGameColorScheme (accent / background /
// correct / wrong), matching every other game screen.
//
// RevealGold is the only fixed semantic color kept — it signals "you ran out
// of attempts and the answer is being shown to you", which is distinct from
// both correct (child got it right) and wrong (child got it wrong).
//
// The prompt banner matches TraceScreen's TraceInstructionBadge exactly.
// Attempt dots match the other games (wrong = used, faded accent = remaining).
// Animal option cards have no background tint (transparent, like habitat cards).
// ─────────────────────────────────────────────────────────────────────────────

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
    val colors = LocalGameColorScheme.current

    LaunchedEffect(
        configJson,
        isCalmMode,
        contentItems.joinToString("|") { it.contentId }
    ) {
        viewModel.loadActivity(contentItems, isCalmMode, configJson, onCardResult, onComplete)
    }

    val cardState  by viewModel.cardState.collectAsStateWithLifecycle()
    val wiggleTick by viewModel.wiggleTick.collectAsStateWithLifecycle()

    val showCelebration = when (val s = cardState) {
        is MatchCardState.AnimalHabitatCard -> s.showCelebration
        is MatchCardState.LetterAnimalCard  -> s.showCelebration
        else                                -> false
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val state = cardState) {
            is MatchCardState.Loading ->
                CircularProgressIndicator(color = colors.accent)
            is MatchCardState.Done ->
                CircularProgressIndicator(color = colors.correct)
            is MatchCardState.AnimalHabitatCard ->
                AnimalHabitatLayout(state, isCalmMode, wiggleTick) { viewModel.onAnswerSelected(it) }
            is MatchCardState.LetterAnimalCard ->
                LetterAnimalLayout(state, isCalmMode, wiggleTick) { viewModel.onAnswerSelected(it) }
        }

        if (showCelebration) {
            GoodJobPopup()
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
        MatchPromptBanner(stringResource(R.string.match_prompt_where_lives))
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
    val colors  = LocalGameColorScheme.current
    val context = LocalContext.current
    val mood    = if (isCalmMode) "calm" else "active"
    val path    = "file:///android_asset/learning_content/visual/$mood/${state.animal.contentId}.png"

    val borderColor by animateColorAsState(
        targetValue = when (state.answerState) {
            AnswerState.Correct  -> colors.correct
            AnswerState.Wrong    -> colors.wrong
            AnswerState.Revealed -> RevealGold
            AnswerState.Idle     -> colors.accent
        },
        animationSpec = tween(300), label = "qBorder"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .border(3.dp, borderColor, RoundedCornerShape(24.dp))
            .background(colors.background)
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
                color      = colors.accent
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
    val colors        = LocalGameColorScheme.current
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
            isCorrect && answerState == AnswerState.Correct  -> colors.correct
            isCorrect && answerState == AnswerState.Revealed -> RevealGold
            isWrongTapped                                    -> colors.wrong
            else                                             -> colors.accent.copy(alpha = 0.35f)
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
            .background(Color.Transparent)
            .clickable(enabled = enabled) { onSelected(habitat.id) }
    ) {
        // Layer 1: habitat photo — always visible
        Image(
            painter            = rememberAsyncImagePainter(
                ImageRequest.Builder(context).data(habitatPath).build()
            ),
            contentDescription = habitatLabel,
            modifier           = Modifier.fillMaxSize(),
            contentScale       = ContentScale.Crop
        )

        // Layer 2: correct overlay tint
        if (showCorrectOverlay) {
            Box(
                modifier = Modifier.fillMaxSize().background(
                    if (answerState == AnswerState.Revealed) RevealGold.copy(alpha = 0.18f)
                    else colors.correct.copy(alpha = 0.15f)
                )
            )
        }

        // Layer 3: wrong tint
        if (isWrongTapped) {
            Box(modifier = Modifier.fillMaxSize().background(colors.wrong.copy(alpha = 0.22f)))
        }

        // Layer 4: label bar
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
        MatchPromptBanner(stringResource(R.string.match_prompt_choose_animal))
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
    val colors  = LocalGameColorScheme.current
    val context = LocalContext.current

    val borderColor by animateColorAsState(
        targetValue = when (state.answerState) {
            AnswerState.Correct  -> colors.correct
            AnswerState.Wrong    -> colors.wrong
            AnswerState.Revealed -> RevealGold
            AnswerState.Idle     -> colors.accent
        },
        animationSpec = tween(300), label = "lBorder"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .border(3.dp, borderColor, RoundedCornerShape(24.dp))
            .background(colors.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Replicating CountingGameScreen pattern: use painterResource + ColorFilter.tint(accent)
            val asset = state.letterImageAsset
            var shown = false
            if (asset is ImageAsset.SvgDrawable) {
                val resId = context.resources.getIdentifier(
                    asset.drawableName, "drawable", context.packageName
                )
                if (resId != 0) {
                    Image(
                        painter            = painterResource(id = resId),
                        contentDescription = state.letter.labelAr,
                        modifier           = Modifier.size(120.dp),
                        colorFilter        = ColorFilter.tint(colors.accent),
                        contentScale       = ContentScale.Fit
                    )
                    shown = true
                }
            } else if (asset is ImageAsset.PngAsset) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data("file:///android_asset/${asset.path}")
                        .build(),
                    contentDescription = state.letter.labelAr,
                    modifier           = Modifier.size(120.dp),
                    contentScale       = ContentScale.Fit
                )
                shown = true
            }

            // Fallback if asset loading fails, show text label in accent
            if (!shown) {
                Text(
                    text       = state.letter.labelAr,
                    fontSize   = 64.sp,
                    fontWeight = FontWeight.Black,
                    color      = colors.accent
                )
            }
            
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
    val colors        = LocalGameColorScheme.current
    val context       = LocalContext.current
    val mood          = if (isCalmMode) "calm" else "active"
    val animalPath    = "file:///android_asset/learning_content/visual/$mood/${option.entity.id}.png"
    val habitat       = ALL_HABITATS.firstOrNull { it.id == option.habitatId } ?: ALL_HABITATS.first()
    val habitatFile   = if (isCalmMode) habitat.calmImage else habitat.activeImage
    val habitatPath   = "file:///android_asset/learning_content/visual/$mood/$habitatFile"
    val isWrongTapped = option.entity.id == lastWrongId && answerState == AnswerState.Wrong
    val showHabitatBg = isCorrect &&
            (answerState == AnswerState.Correct || answerState == AnswerState.Revealed)

    val borderColor by animateColorAsState(
        targetValue = when {
            isCorrect && answerState == AnswerState.Correct  -> colors.correct
            isCorrect && answerState == AnswerState.Revealed -> RevealGold
            isWrongTapped                                    -> colors.wrong
            else                                             -> colors.accent.copy(alpha = 0.35f)
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
            // No background tint — transparent like habitat cards
            .background(Color.Transparent)
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

        // Layer 2: correct overlay tint
        if (showHabitatBg) {
            Box(
                modifier = Modifier.fillMaxSize().background(
                    if (answerState == AnswerState.Revealed) RevealGold.copy(alpha = 0.18f)
                    else colors.correct.copy(alpha = 0.15f)
                )
            )
        }

        // Layer 3: wrong tint
        if (isWrongTapped) {
            Box(modifier = Modifier.fillMaxSize().background(colors.wrong.copy(alpha = 0.22f)))
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

        // Layer 5: label bar — dark when habitat visible, faint accent tint when idle
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    color = if (showHabitatBg)
                        Color.Black.copy(alpha = 0.55f)
                    else
                        colors.accent.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
                )
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text       = option.entity.labelAr,
                fontSize   = 15.sp,
                fontWeight = FontWeight.Bold,
                color      = if (showHabitatBg) Color.White else colors.accent
            )
        }

        CornerBadge(isCorrect = isCorrect, isWrongTapped = isWrongTapped, answerState = answerState)
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Shared composables
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Top row: question counter + attempt dots.
 * Dots follow the same pattern as all other games:
 *   used attempt → colors.wrong  (red = attempt spent)
 *   remaining    → colors.accent.copy(alpha = 0.25f)
 */
@Composable
private fun TopRow(questionIndex: Int, totalQuestions: Int, attemptsLeft: Int) {
    val colors = LocalGameColorScheme.current
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(
            text       = "${questionIndex + 1} / $totalQuestions",
            fontSize   = 18.sp,
            fontWeight = FontWeight.Bold,
            color      = colors.accent
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { i ->
                // i=0 is the first dot; dot is "used" when that attempt slot is gone
                val used = i >= attemptsLeft
                val color by animateColorAsState(
                    targetValue   = if (used) colors.wrong else colors.accent.copy(alpha = 0.25f),
                    animationSpec = tween(300),
                    label         = "matchDot$i"
                )
                Box(
                    modifier = Modifier
                        .size(if (used) 14.dp else 11.dp)
                        .clip(CircleShape)
                        .background(color)
                )
            }
        }
    }
}

/**
 * Prompt banner — identical structure to TraceInstructionBadge in TraceScreen.
 * Uses the same fixed TraceBadgeBorder / TraceBadgeText tokens.
 */
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
            Text(
                text       = text,
                fontSize   = 17.sp,
                fontWeight = FontWeight.Bold,
                color      = TraceBadgeText,
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

@Composable
private fun FeedbackIcon(answerState: AnswerState) {
    val colors = LocalGameColorScheme.current
    when (answerState) {
        AnswerState.Correct  -> Icon(Icons.Default.Check, null, tint = colors.correct, modifier = Modifier.size(32.dp))
        AnswerState.Wrong    -> Icon(Icons.Default.Close, null, tint = colors.wrong,   modifier = Modifier.size(32.dp))
        AnswerState.Revealed -> Icon(Icons.Default.Check, null, tint = RevealGold,     modifier = Modifier.size(32.dp))
        AnswerState.Idle     -> Spacer(Modifier.size(32.dp))
    }
}

@Composable
private fun BoxScope.CornerBadge(
    isCorrect: Boolean,
    isWrongTapped: Boolean,
    answerState: AnswerState
) {
    val colors = LocalGameColorScheme.current
    if (answerState == AnswerState.Idle) return
    val showGreen = isCorrect && answerState == AnswerState.Correct
    val showGold  = isCorrect && answerState == AnswerState.Revealed
    val showRed   = isWrongTapped
    if (!showGreen && !showGold && !showRed) return

    val bg = when { showGreen -> colors.correct; showGold -> RevealGold; else -> colors.wrong }

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
