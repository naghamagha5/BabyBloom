package com.babybloom.presentation.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.babybloom.R
import com.babybloom.domain.model.ActivityContent
import com.babybloom.presentation.viewmodels.ListenAnswerFeedback
import com.babybloom.presentation.viewmodels.ListenAndChooseGameState
import com.babybloom.presentation.viewmodels.ListenAndChooseGameViewModel
import com.babybloom.presentation.viewmodels.ListenChoiceOption
import com.babybloom.ui.theme.DragAttemptDotFull
import com.babybloom.ui.theme.LocalGameColorScheme
import com.babybloom.ui.theme.TraceBadgeBorder
import com.babybloom.ui.theme.TraceBadgeText
import com.babybloom.ui.theme.dragColorForContentId
import com.babybloom.util.AssetPathResolver
import com.babybloom.util.ImageAsset
import kotlinx.coroutines.delay

@Composable
fun ListenAndChooseGameScreen(
    currentItem: ActivityContent,
    isCalmMode: Boolean,
    isTest: Boolean,
    onComplete: (isCorrect: Boolean, encodedMs: Long) -> Unit,
    viewModel: ListenAndChooseGameViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(currentItem.contentId, isCalmMode, isTest) {
        viewModel.loadContent(currentItem, isCalmMode, isTest, onComplete)
    }

    DisposableEffect(currentItem.contentId) {
        onDispose { viewModel.stopContent(currentItem.contentId) }
    }

    if (state.isLoading || state.contentId != currentItem.contentId) {
        val colors = LocalGameColorScheme.current
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = colors.accent)
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ListenInstructionBadge(state.instructionText)
            Spacer(Modifier.height(60.dp))
            ListenSpeakerButton(
                enabled = state.canReplay,
                onClick = viewModel::onReplayClicked
            )
            Spacer(Modifier.height(20.dp))
            ListenChoices(state = state, onOptionClick = viewModel::onChoiceSelected)
            Spacer(Modifier.weight(1f))
            ListenAttemptsRow(state)
        }

        if (state.showCelebration) {
            GoodJobPopup(coverage = -1f)
        }
    }
}

@Composable
private fun ListenInstructionBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(26.dp))
            .border(1.5.dp, TraceBadgeBorder, RoundedCornerShape(26.dp))
            .background(Color.White.copy(alpha = 0.92f))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = text,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TraceBadgeText,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Rtl)
            )
            Spacer(Modifier.size(8.dp))
            Image(
                painter = painterResource(R.drawable.front_hand),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun ListenSpeakerButton(enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalGameColorScheme.current
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = if (enabled) colors.accent.copy(alpha = 0.12f) else colors.accent.copy(alpha = 0.06f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.listen_choose_replay_label),
                color = if (enabled) colors.accent else colors.accent.copy(alpha = 0.45f),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Rtl)
            )
            Spacer(Modifier.size(8.dp))
            Icon(
                painter = painterResource(R.drawable.ic_sound),
                contentDescription = stringResource(R.string.listen_choose_replay_label),
                tint = if (enabled) colors.accent else colors.accent.copy(alpha = 0.45f),
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
private fun ListenChoices(
    state: ListenAndChooseGameState,
    onOptionClick: (String) -> Unit
) {
    var showTapHint by remember(state.contentId, state.attemptsUsed, state.isTest) { mutableStateOf(false) }

    LaunchedEffect(
        state.contentId,
        state.attemptsUsed,
        state.isAudioLocked,
        state.isAnswered,
        state.isTest,
        state.answerFeedback
    ) {
        showTapHint = false
        if (!state.isTest &&
            !state.isAnswered &&
            !state.isAudioLocked &&
            state.answerFeedback == ListenAnswerFeedback.IDLE
        ) {
            showTapHint = true
            delay(3_000)
            showTapHint = false
        }
    }

    if (state.isTest) {
        val firstRow = state.options.take(2)
        val secondRow = state.options.drop(2).take(2)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ChoiceRow(firstRow, state, !state.isAnswered && !state.isAudioLocked, onOptionClick)
            if (secondRow.isNotEmpty()) {
                ChoiceRow(secondRow, state, !state.isAnswered && !state.isAudioLocked, onOptionClick)
            }
        }
    } else {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            state.options.firstOrNull()?.let { option ->
                Box(modifier = Modifier.fillMaxWidth(0.52f)) {
                    ListenChoiceCard(
                        option = option,
                        state = state,
                        enabled = !state.isAnswered && !state.isAudioLocked,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onOptionClick
                    )
                    if (!state.isTest &&
                        !state.isAnswered &&
                        state.answerFeedback == ListenAnswerFeedback.IDLE &&
                        showTapHint
                    ) {
                        HandTapHint(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 10.dp, bottom = 10.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(
    options: List<ListenChoiceOption>,
    state: ListenAndChooseGameState,
    enabled: Boolean,
    onOptionClick: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        options.forEach { option ->
            ListenChoiceCard(
                option = option,
                state = state,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = onOptionClick
            )
        }
        repeat((2 - options.size).coerceAtLeast(0)) {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun ListenChoiceCard(
    option: ListenChoiceOption,
    state: ListenAndChooseGameState,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: (String) -> Unit
) {
    val colors = LocalGameColorScheme.current
    val isSelectedWrong = state.selectedOptionId == option.id &&
        state.answerFeedback == ListenAnswerFeedback.WRONG
    val isCorrectReveal = state.revealedCorrectId == option.id &&
        state.answerFeedback in listOf(ListenAnswerFeedback.CORRECT, ListenAnswerFeedback.WRONG)
    val borderColor = when {
        isCorrectReveal -> colors.correct
        isSelectedWrong -> colors.wrong
        else -> colors.accent.copy(alpha = 0.2f)
    }
    val baseBg = colors.background
    val overlayBg = colors.accent.copy(alpha = 0.08f)
    val cardBg = when {
        isCorrectReveal -> colors.correct.copy(alpha = 0.18f)
        isSelectedWrong -> colors.wrong.copy(alpha = 0.18f)
        else -> baseBg
    }
    val shakeAnim = remember { Animatable(0f) }
    LaunchedEffect(isSelectedWrong) {
        if (isSelectedWrong) {
            repeat(6) {
                shakeAnim.animateTo(
                    if (it % 2 == 0) 12f else -12f,
                    animationSpec = tween(55, easing = LinearEasing)
                )
            }
            shakeAnim.animateTo(0f, animationSpec = tween(60))
        } else {
            shakeAnim.snapTo(0f)
        }
    }
    val cardScale by animateFloatAsState(
        targetValue = if (isCorrectReveal) 1.03f else 1f,
        animationSpec = spring(),
        label = "listen_choice_scale"
    )
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(24.dp))
            .graphicsLayer {
                translationX = shakeAnim.value
                scaleX = cardScale
                scaleY = cardScale
            }
            .clickable(enabled = enabled) { onClick(option.id) },
        shape = RoundedCornerShape(24.dp),
        color = cardBg,
        tonalElevation = 0.dp,
        shadowElevation = 4.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(overlayBg)
                .border(2.dp, borderColor, RoundedCornerShape(24.dp))
                .padding(18.dp),
            contentAlignment = Alignment.Center
        ) {
            ListenChoiceImage(
                asset = option.imageAsset,
                contentId = option.id,
                label = option.labelAr,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun HandTapHint(modifier: Modifier = Modifier) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.hand_tap)
    )
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = Int.MAX_VALUE
    )
    Box(
        modifier = modifier.size(58.dp),
        contentAlignment = Alignment.Center
    ) {
        if (composition != null) {
            LottieAnimation(
                composition = composition,
                progress = { progress },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Image(
                painter = painterResource(R.drawable.front_hand),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun ListenAttemptsRow(state: ListenAndChooseGameState) {
    val colors = LocalGameColorScheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(3) { index ->
            val isFilled = index < state.attemptsUsed
            Box(
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .size(12.dp)
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

@Composable
private fun ListenChoiceImage(
    asset: ImageAsset,
    contentId: String,
    label: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = LocalGameColorScheme.current
    when (asset) {
        is ImageAsset.PngAsset -> {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(AssetPathResolver.androidAssetUri(asset.path))
                    .crossfade(true)
                    .build(),
                contentDescription = label,
                modifier = modifier,
                contentScale = ContentScale.Fit
            )
        }

        is ImageAsset.SvgDrawable -> {
            val tintColor = if (contentId.startsWith("color_", ignoreCase = true)) {
                dragColorForContentId(contentId)
            } else {
                colors.accent
            }
            val drawableId = context.resources.getIdentifier(
                asset.drawableName,
                "drawable",
                context.packageName
            )
            if (drawableId != 0) {
                Image(
                    painter = painterResource(drawableId),
                    contentDescription = label,
                    modifier = modifier,
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(tintColor)
                )
            } else {
                Box(
                    modifier = modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(tintColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = tintColor,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Rtl)
                    )
                }
            }
        }
    }
}
