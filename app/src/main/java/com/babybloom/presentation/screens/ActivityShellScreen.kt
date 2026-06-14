package com.babybloom.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babybloom.domain.model.ActivityLaunchStep
import com.babybloom.domain.model.SessionDecision
import com.babybloom.R
import com.babybloom.presentation.components.AttentionCameraOverlay
import com.babybloom.presentation.viewmodels.ActivityUiState
import com.babybloom.presentation.viewmodels.ActivityViewModel
import com.babybloom.ui.theme.GameActiveSwatch1
import com.babybloom.ui.theme.GameActiveBackground
import com.babybloom.ui.theme.ProgressActiveSwatch1
import com.babybloom.ui.theme.ProgressActiveSwatch2
import com.babybloom.ui.theme.ProgressActiveSwatch3
import com.babybloom.ui.theme.GameCalmBackground
import com.babybloom.ui.theme.GameCalmSwatch1
import com.babybloom.ui.theme.ProgressCalmSwatch1
import com.babybloom.ui.theme.ProgressCalmSwatch2
import com.babybloom.ui.theme.ProgressCalmSwatch3
import com.babybloom.ui.theme.LocalGameColorScheme
import com.babybloom.ui.theme.DarkPurple
import com.babybloom.ui.theme.TextSecondary
import com.babybloom.ui.theme.gameColorSchemeFor
import kotlinx.coroutines.delay

// ─────────────────────────────────────────────────────────────────────────────
// ActivityShellScreen.kt
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ActivityShellScreen(
    activityId        : String,
    sessionId         : Long,
    childId           : Long,
    contentId         : String? = null,
    queue             : List<ActivityLaunchStep> = emptyList(),
    stepIndex         : Int = 0,
    isAssessment      : Boolean = false,
    isTest            : Boolean = false,
    assessmentCurrent : Int = 0,
    assessmentTotal   : Int = 0,
    onActivityComplete: (score: Int, total: Int, sessionId: Long, decision: SessionDecision?) -> Unit,
    onExit            : () -> Unit,
    viewModel         : ActivityViewModel = hiltViewModel()
) {
    LaunchedEffect(activityId, contentId, stepIndex) {
        viewModel.loadActivity(
            activityId = activityId,
            sessionId = sessionId,
            childId = childId,
            contentId = contentId,
            queue = queue,
            stepIndex = stepIndex,
            isAssessment = isAssessment,
            isTest = isTest
        )
    }
    BackHandler { viewModel.requestExit() }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {

        is ActivityUiState.Loading -> ActivityLoadingScreen()

        is ActivityUiState.Error -> ActivityErrorScreen(
            message = state.message,
            onExit  = onExit
        )

        // ── Completed ─────────────────────────────────────────────────────────
        is ActivityUiState.Completed -> {
            val isCurrentCompletion = state.activityId == activityId &&
                    state.contentId == contentId &&
                    state.stepIndex == stepIndex
            if (!isCurrentCompletion) {
                ActivityTransitionScreen()
                return
            }

            val isFinalNormalSession = state.decision == null ||
                    state.decision is SessionDecision.SessionComplete

            if (isAssessment || !isFinalNormalSession) {
                LaunchedEffect(state.sessionId, state.activityId, state.contentId, state.stepIndex) {
                    onActivityComplete(
                        state.score,
                        state.total,
                        state.sessionId,
                        state.decision
                    )
                }
                ActivityTransitionScreen()
            } else {
                GoodJobScreen(
                    score      = state.score,
                    total      = state.total,
                    onFinished = {
                        viewModel.stopSoundSession()
                        onActivityComplete(
                            state.score,
                            state.total,
                            state.sessionId,
                            state.decision
                        )
                    }
                )
            }
        }

        // ── Playing ───────────────────────────────────────────────────────────
        is ActivityUiState.Playing -> {
            val settings    = state.sessionSettings
            val activity    = state.activityWithContent.activity
            val currentItem = state.activityWithContent.contentItems
                .getOrNull(state.currentIndex) ?: return

            val isCurrentActivity = activity.id == activityId &&
                    state.stepIndex == stepIndex &&
                    (contentId == null || currentItem.contentId == contentId)
            if (!isCurrentActivity) {
                ActivityTransitionScreen()
                return
            }

            val progress = if (isAssessment && assessmentTotal > 0) {
                assessmentCurrent.toFloat() / assessmentTotal.toFloat()
            } else {
                val durationMs = settings.sessionDurationMs.coerceAtLeast(1L)
                val elapsedMs = durationMs - state.sessionRemainingMs
                elapsedMs.toFloat() / durationMs.toFloat()
            }

            // ── Build the color scheme for this round ─────────────────────
            // seed = currentIndex  →  accent rotates each round automatically
            val colorSeed = state.stepIndex + state.currentIndex
            val gameColors = remember(settings.isCalmMode, colorSeed) {
                gameColorSchemeFor(
                    isCalmMode = settings.isCalmMode,
                    seed       = colorSeed
                )
            }
            val progressBrush = remember(settings.isCalmMode) {
                if (settings.isCalmMode) {
                    Brush.horizontalGradient(
                        colors = listOf(
                            ProgressActiveSwatch1,
                            ProgressActiveSwatch2,
                            ProgressActiveSwatch3
                        )
                    )
                } else {
                    Brush.horizontalGradient(
                        colors = listOf(
                            ProgressCalmSwatch1,
                            ProgressCalmSwatch2,
                            ProgressCalmSwatch3
                        )
                    )
                }
            }

            val backgroundRes = if (settings.isCalmMode)
                R.drawable.ic_game_background_calm
            else
                R.drawable.ic_game_background_active

            // ── Provide the scheme to the entire game sub-tree ────────────
            CompositionLocalProvider(LocalGameColorScheme provides gameColors) {

                Box(modifier = Modifier.fillMaxSize()) {
                    AttentionCameraOverlay(
                        onSample = viewModel::onAttentionSample,
                        analyzeImage = viewModel::analyzeAttention,
                        sampleIntervalMs = if (settings.isAssessment) 750L else 2_000L
                    )

                    // ── Full-screen background ────────────────────────────
                    Image(
                        painter            = painterResource(id = backgroundRes),
                        contentDescription = null,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize()
                    )

                    // ── Top bar ───────────────────────────────────────────
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier          = Modifier.fillMaxWidth()
                        ) {
                            // Back button – same color as card background so it blends quietly
                            IconButton(
                                onClick  = { viewModel.requestExit() },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(gameColors.background)
                            ) {
                                Icon(
                                    imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "خروج",
                                    tint               = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Spacer(Modifier.width(12.dp))

                            // Progress bar shows elapsed session time in normal sessions.
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(gameColors.background.copy(alpha = 0.6f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                                        .clip(RoundedCornerShape(50))
                                        .background(progressBrush)
                                )
                            }

                            Spacer(Modifier.width(12.dp))

                            if (isAssessment) {
                                AssessmentStepBadge(
                                    current = assessmentCurrent,
                                    total = assessmentTotal
                                )
                            } else {
                                val minutes = (state.sessionRemainingMs / 60_000).toInt()
                                val seconds = ((state.sessionRemainingMs % 60_000) / 1_000).toInt()
                                Text(
                                    text  = "%02d:%02d".format(minutes, seconds),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (state.sessionRemainingMs < 60_000)
                                        gameColors.wrong
                                    else
                                        MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // ── Content card (bottom 88%) ─────────────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.88f)
                            .align(Alignment.BottomCenter)
                            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                            // Card background comes from the semantic palette
                            .background(gameColors.background)
                            .padding(16.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {

                            // ── Game router ───────────────────────────────
                            // Every game screen can now call:
                            //   val colors = LocalGameColorScheme.current
                            // to get accent / background / correct / wrong.

                            when (activity.activityType) {

                                "STORY" -> StoryScreen(
                                    currentItem = currentItem,
                                    isCalmMode  = settings.isCalmMode,
                                    onComplete  = { elapsedMs ->
                                        viewModel.onAnswerSubmitted(
                                            isCorrect      = true,
                                            contentId      = currentItem.contentId,
                                            responseTimeMs = elapsedMs
                                        )
                                    }
                                )

                                "SPEECH" -> {
                                    if (state.showOfflineSpeechBanner) {
                                        LaunchedEffect(currentItem.contentId, state.stepIndex) {
                                            delay(3_000)
                                            viewModel.skipSpeechActivityOffline()
                                        }
                                        OfflineSpeechFallbackContent(isCalmMode = settings.isCalmMode)
                                    } else {
                                        SpeechScreen(
                                            currentItem = currentItem,
                                            isCalmMode  = settings.isCalmMode,
                                            onComplete  = { elapsedMs, attempts, confidence, isCorrect ->
                                                viewModel.onAnswerSubmitted(
                                                    isCorrect        = isCorrect,
                                                    contentId        = currentItem.contentId,
                                                    responseTimeMs   = elapsedMs,
                                                    attempts         = attempts,
                                                    speechConfidence = confidence
                                                )
                                            },
                                            onOffline = viewModel::onSpeechOfflineDetected
                                        )
                                    }
                                }

                                "MATCH" -> MatchScreen(
                                    contentItems = state.activityWithContent.contentItems,
                                    isCalmMode   = settings.isCalmMode,
                                    isTest       = settings.isTest,
                                    isAssessment = settings.isAssessment,
                                    configJson   = activity.configJson,
                                    onCardResult = { contentId, isCorrect, _, _, attempts, touchQualityScore, elapsedMs ->
                                        viewModel.onAnswerSubmitted(
                                            isCorrect      = isCorrect,
                                            contentId      = contentId,
                                            responseTimeMs = elapsedMs,
                                            attempts       = attempts,
                                            touchQualityScore = touchQualityScore
                                        )
                                    },
                                    onComplete = { _, _ -> }
                                )

                                "TRACE" -> TraceScreen(
                                    currentItem = currentItem,
                                    isCalmMode  = settings.isCalmMode,
                                    onComplete  = { result ->
                                        viewModel.onAnswerSubmitted(
                                            isCorrect       = result.isSuccess,
                                            contentId       = currentItem.contentId,
                                            responseTimeMs  = result.elapsedMs,
                                            attempts        = result.attempts,
                                            touchQualityScore = result.touchQualityScore,
                                            scoreOverride   = result.coverage
                                        )
                                        viewModel.saveTraceInteractionEvent(
                                            contentId       = currentItem.contentId,
                                            touchQualityScore = result.touchQualityScore,
                                            averageMovementDistance = result.averageMovementDistance,
                                            correctionCount = result.correctionCount
                                        )
                                    }
                                )

                                "COUNT" -> CountingGameScreen(
                                    currentItem     = currentItem,
                                    isCalmMode      = settings.isCalmMode,
                                    difficultyLevel = activity.difficultyLevel,
                                    isTest       = settings.isTest,
                                    activityId      = activity.id,
                                    roundIndex      = state.currentIndex,
                                    onComplete      = { isCorrect, elapsedMs, attempts ->
                                        viewModel.onAnswerSubmitted(
                                            isCorrect       = isCorrect,
                                            contentId       = currentItem.contentId,
                                            responseTimeMs  = elapsedMs,
                                            attempts        = attempts
                                        )
                                    }
                                )

                                "DRAG" -> DragGameScreen(
                                    currentItem  = currentItem,
                                    isCalmMode   = settings.isCalmMode,
                                    isTest       = settings.isTest,
                                    onComplete   = { isCorrect, encodedMs, touchQualityScore ->
                                        val attempts     = (encodedMs / 100_000L).toInt()
                                            .coerceAtLeast(1)
                                        val responseTime = encodedMs % 100_000L
                                        viewModel.onAnswerSubmitted(
                                            isCorrect       = isCorrect,
                                            contentId       = currentItem.contentId,
                                            responseTimeMs  = responseTime,
                                            attempts        = attempts,
                                            touchQualityScore = touchQualityScore
                                        )
                                    }
                                )

                                "LISTEN_AND_CHOOSE" -> ListenAndChooseGameScreen(
                                    currentItem = currentItem,
                                    isCalmMode = settings.isCalmMode,
                                    isTest = settings.isTest,
                                    onComplete = { isCorrect, encodedMs ->
                                        val attempts = (encodedMs / 100_000L).toInt()
                                            .coerceAtLeast(1)
                                        val responseTime = encodedMs % 100_000L
                                        viewModel.onAnswerSubmitted(
                                            isCorrect = isCorrect,
                                            contentId = currentItem.contentId,
                                            responseTimeMs = responseTime,
                                            attempts = attempts
                                        )
                                    }
                                )

                                else -> GamePlaceholder("Unknown: ${activity.activityType}")
                            }
                        }
                    }

                    // ── Parent lock overlay ───────────────────────────────
                    if (state.showParentLock) {
                        ParentLockScreen(
                            onUnlocked = {
                                viewModel.pauseNormalSessionForExit()
                                viewModel.dismissParentLock()
                                onExit()
                            },
                            onDismiss = { viewModel.dismissParentLock() }
                        )
                    }
                }
            } // end CompositionLocalProvider
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// How to consume GameColorScheme inside any game screen
// ─────────────────────────────────────────────────────────────────────────────
//
//  @Composable
//  fun MatchScreen(...) {
//      val colors = LocalGameColorScheme.current
//
//      // accent  → card borders, icon tints, highlighted labels
//      // background → this screen's card/surface background
//      // correct → green feedback
//      // wrong   → red feedback
//
//      Box(modifier = Modifier.background(colors.background)) { ... }
//      Text(color = colors.correct) { ... }
//  }
//
// ─────────────────────────────────────────────────────────────────────────────

// ── Shared helpers ────────────────────────────────────────────────────────────

@Composable
private fun GamePlaceholder(label: String) {
    Box(
        modifier         = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text  = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
fun ActivityLoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ActivityTransitionScreen() {
    Box(Modifier.fillMaxSize())
}

@Composable
fun ActivityErrorScreen(message: String, onExit: () -> Unit) {
    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text  = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onExit) { Text("خروج") }
    }
}

@Composable
private fun OfflineSpeechFallbackContent(isCalmMode: Boolean) {
    val backgroundColor = if (isCalmMode) GameCalmBackground else GameActiveBackground
    val accentColor = if (isCalmMode) GameCalmSwatch1 else GameActiveSwatch1

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.WifiOff,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(48.dp)
                )
            }

            Text(
                text = stringResource(R.string.session_speech_fallback_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = DarkPurple,
                textAlign = TextAlign.Center
            )

            Text(
                text = stringResource(R.string.session_speech_fallback_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 16.sp,
                color = TextSecondary,
                lineHeight = 26.sp,
                textAlign = TextAlign.Center
            )

            OfflineCountdownBar(
                durationMs = 3_000L,
                accentColor = accentColor
            )
        }
    }
}

@Composable
private fun OfflineCountdownBar(durationMs: Long, accentColor: Color) {
    var progress by remember(durationMs) { mutableStateOf(1f) }

    LaunchedEffect(durationMs) {
        val steps = 60
        val stepDelay = (durationMs / steps).coerceAtLeast(1L)
        repeat(steps) { index ->
            progress = 1f - ((index + 1).toFloat() / steps.toFloat())
            delay(stepDelay)
        }
        progress = 0f
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.session_speech_fallback_countdown),
                style = MaterialTheme.typography.bodySmall,
                fontSize = 13.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(accentColor.copy(alpha = 0.2f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(50))
                        .background(accentColor)
                )
            }
        }
    }
}

@Composable
private fun AssessmentStepBadge(
    current: Int,
    total: Int
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Text(
            text = "$current / $total",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
