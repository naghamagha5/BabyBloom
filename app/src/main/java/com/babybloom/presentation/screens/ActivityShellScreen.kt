package com.babybloom.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babybloom.R
import com.babybloom.presentation.viewmodels.ActivityUiState
import com.babybloom.presentation.viewmodels.ActivityViewModel
import com.babybloom.ui.theme.LocalGameColorScheme
import com.babybloom.ui.theme.gameColorSchemeFor

// ─────────────────────────────────────────────────────────────────────────────
// ActivityShellScreen.kt
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ActivityShellScreen(
    activityId        : String,
    sessionId         : Long,
    childId           : Long,
    onActivityComplete: (score: Int, total: Int) -> Unit,
    onExit            : () -> Unit,
    viewModel         : ActivityViewModel = hiltViewModel()
) {
    LaunchedEffect(activityId) { viewModel.loadActivity(activityId, sessionId, childId) }
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
            GoodJobScreen(
                score      = state.score,
                total      = state.total,
                onFinished = { onActivityComplete(state.score, state.total) }
            )
        }

        // ── Playing ───────────────────────────────────────────────────────────
        is ActivityUiState.Playing -> {
            val settings    = state.sessionSettings
            val activity    = state.activityWithContent.activity
            val currentItem = state.activityWithContent.contentItems
                .getOrNull(state.currentIndex) ?: return

            val progress = if (state.activityWithContent.contentItems.isEmpty()) 0f
            else state.currentIndex.toFloat() / state.activityWithContent.contentItems.size

            // ── Build the color scheme for this round ─────────────────────
            // seed = currentIndex  →  accent rotates each round automatically
            val gameColors = remember(settings.isCalmMode, state.currentIndex) {
                gameColorSchemeFor(
                    isCalmMode = settings.isCalmMode,
                    seed       = state.currentIndex
                )
            }

            val backgroundRes = if (settings.isCalmMode)
                R.drawable.ic_game_background_calm
            else
                R.drawable.ic_game_background_active

            // ── Provide the scheme to the entire game sub-tree ────────────
            CompositionLocalProvider(LocalGameColorScheme provides gameColors) {

                Box(modifier = Modifier.fillMaxSize()) {

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
                                    imageVector        = Icons.Default.ArrowBack,
                                    contentDescription = "خروج",
                                    tint               = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Spacer(Modifier.width(12.dp))

                            // Progress bar – track matches background; fill = correct (green)
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
                                        .background(gameColors.correct)
                                )
                            }

                            Spacer(Modifier.width(12.dp))

                            // Timer – turns red when < 1 min
                            val minutes = (state.sessionRemainingMs / 60_000).toInt()
                            val seconds = ((state.sessionRemainingMs % 60_000) / 1_000).toInt()
                            Text(
                                text  = "%02d:%02d".format(minutes, seconds),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (state.sessionRemainingMs < 60_000)
                                    gameColors.wrong          // reuse semantic wrong = red
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
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

                            if (state.showOfflineSpeechBanner) {
                                OfflineSpeechBanner()
                                Spacer(Modifier.height(8.dp))
                            }

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

                                "SPEECH" -> SpeechScreen(
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
                                    }
                                )

                                "MATCH" -> MatchScreen(
                                    contentItems = state.activityWithContent.contentItems,
                                    isCalmMode   = settings.isCalmMode,
                                    configJson   = activity.configJson,
                                    onCardResult = { contentId, isCorrect, _, _, attempts ->
                                        viewModel.onAnswerSubmitted(
                                            isCorrect      = isCorrect,
                                            contentId      = contentId,
                                            responseTimeMs = System.currentTimeMillis(),
                                            attempts       = attempts
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
                                            touchComplexity = result.touchComplexity,
                                            scoreOverride   = result.coverage
                                        )
                                        viewModel.saveTraceInteractionEvent(
                                            contentId       = currentItem.contentId,
                                            touchComplexity = result.touchComplexity,
                                            avgStrokeLength = result.avgStrokeLength,
                                            correctionCount = result.correctionCount
                                        )
                                    }
                                )

                                "COUNT" -> CountingGameScreen(
                                    currentItem     = currentItem,
                                    isCalmMode      = settings.isCalmMode,
                                    difficultyLevel = activity.difficultyLevel,
                                    activityId      = activity.id,
                                    roundIndex      = state.currentIndex,
                                    onComplete      = { isCorrect, elapsedMs, attempts, touchComplexity ->
                                        viewModel.onAnswerSubmitted(
                                            isCorrect       = isCorrect,
                                            contentId       = currentItem.contentId,
                                            responseTimeMs  = elapsedMs,
                                            attempts        = attempts,
                                            touchComplexity = touchComplexity
                                        )
                                    }
                                )

                                "DRAG" -> DragGameScreen(
                                    currentItem = currentItem,
                                    isCalmMode  = settings.isCalmMode,
                                    onComplete  = { isCorrect, encodedMs ->
                                        val attempts     = (encodedMs / 100_000L).toInt()
                                            .coerceAtLeast(1)
                                        val responseTime = encodedMs % 100_000L
                                        viewModel.onAnswerSubmitted(
                                            isCorrect      = isCorrect,
                                            contentId      = currentItem.contentId,
                                            responseTimeMs = responseTime,
                                            attempts       = attempts
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
fun OfflineSpeechBanner() {
    Box(
        modifier         = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text  = "وضع الكلام يحتاج إنترنت",
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}