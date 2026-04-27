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

@Composable
fun ActivityShellScreen(
    activityId: String,
    sessionId: Long,
    childId: Long,
    onActivityComplete: (score: Int, total: Int) -> Unit,
    onExit: () -> Unit,
    viewModel: ActivityViewModel = hiltViewModel()
) {
    LaunchedEffect(activityId) {
        viewModel.loadActivity(activityId, sessionId, childId)
    }

    BackHandler {
        viewModel.requestExit()
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        is ActivityUiState.Loading -> ActivityLoadingScreen()

        is ActivityUiState.Error -> ActivityErrorScreen(
            message = state.message,
            onExit = onExit
        )

        is ActivityUiState.Completed -> {
            LaunchedEffect(Unit) { onActivityComplete(state.score, state.total) }
            ActivityCompletedScreen(
                score = state.score,
                total = state.total,
                onFinish = { onActivityComplete(state.score, state.total) }
            )
        }

        is ActivityUiState.Playing -> {
            val settings = state.sessionSettings
            val activity = state.activityWithContent.activity
            val currentItem = state.activityWithContent.contentItems
                .getOrNull(state.currentIndex) ?: return

            val progress = if (state.activityWithContent.contentItems.isEmpty()) 0f
            else state.currentIndex.toFloat() / state.activityWithContent.contentItems.size

            val backgroundRes = if (settings.isCalmMode)
                R.drawable.ic_game_background_calm
            else
                R.drawable.ic_game_background_active

            // ── Root box: background image fills entire screen ────────────
            Box(modifier = Modifier.fillMaxSize()) {

                Image(
                    painter = painterResource(id = backgroundRes),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // ── Top bar: back button + progress bar ───────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Circular back button
                        IconButton(
                            onClick = { viewModel.requestExit() },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "خروج",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        // Rounded pill progress bar
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(10.dp)
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                                    .clip(RoundedCornerShape(50))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }

                        // Session timer
                        Spacer(Modifier.width(12.dp))
                        val minutes = (state.sessionRemainingMs / 60_000).toInt()
                        val seconds = ((state.sessionRemainingMs % 60_000) / 1_000).toInt()
                        Text(
                            text = "%02d:%02d".format(minutes, seconds),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (state.sessionRemainingMs < 60_000)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // ── Big content card ──────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.88f)
                        .align(Alignment.BottomCenter)
                        .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {

                        if (state.showOfflineSpeechBanner) {
                            OfflineSpeechBanner()
                            Spacer(Modifier.height(8.dp))
                        }

                        // ── Game router ───────────────────────────────────
                        when (activity.activityType) {
                            "STORY" -> StoryScreen(
                                currentItem = currentItem,
                                isCalmMode = settings.isCalmMode,
                                onComplete = { elapsedMs ->
                                    viewModel.onAnswerSubmitted(
                                        isCorrect = true,
                                        contentId = currentItem.contentId,
                                        responseTimeMs = elapsedMs
                                    )
                                }
                            )
                            "SPEECH" -> SpeechScreen(
                                currentItem = currentItem,
                                isCalmMode = settings.isCalmMode,
                                onComplete = { elapsedMs, attempts, confidence, isCorrect ->
                                    viewModel.onAnswerSubmitted(
                                        isCorrect      = isCorrect,
                                        contentId      = currentItem.contentId,
                                        responseTimeMs = elapsedMs,
                                        attempts       = attempts,
                                        speechConfidence = confidence
                                    )
                                }
                            )
                            "MATCH"  -> GamePlaceholder("MATCH Game — coming soon")
                            "TRACE"  -> GamePlaceholder("TRACE Game — coming soon")
                            "COUNT"  -> GamePlaceholder("COUNT Game — coming soon")
                            "DRAG"   -> GamePlaceholder("DRAG Game — coming soon")
                            else     -> GamePlaceholder("Unknown: ${activity.activityType}")
                        }
                    }
                }

                // ── Parent lock overlay — on top of everything ────────────
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
        }
    }
}

// ── Placeholder ───────────────────────────────────────────────────────────────
@Composable
private fun GamePlaceholder(label: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

// ── Loading ───────────────────────────────────────────────────────────────────
@Composable
fun ActivityLoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

// ── Error ─────────────────────────────────────────────────────────────────────
@Composable
fun ActivityErrorScreen(message: String, onExit: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onExit) { Text("خروج") }
    }
}

// ── Completed ─────────────────────────────────────────────────────────────────
@Composable
fun ActivityCompletedScreen(score: Int, total: Int, onFinish: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("أحسنت!", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text("درجتك: $score من $total", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onFinish) { Text("تم") }
    }
}

// ── Offline Speech Banner ─────────────────────────────────────────────────────
@Composable
private fun OfflineSpeechBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "وضع الكلام يحتاج إنترنت",
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}