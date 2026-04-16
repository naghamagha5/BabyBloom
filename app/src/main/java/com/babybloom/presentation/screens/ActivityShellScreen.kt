package com.babybloom.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

            Box(modifier = Modifier.fillMaxSize()) {

                ActivityScaffold(
                    title = activity.title,
                    progress = progress,
                    remainingMs = state.sessionRemainingMs,
                    onBackRequest = { viewModel.requestExit() }
                ) {
                    if (state.showOfflineSpeechBanner) {
                        OfflineSpeechBanner()
                    }

                    // ── Game router ───────────────────────────────────────
                    // imageAsset resolution happens inside each game screen.
                    // Replace each GamePlaceholder with the real screen once built.
                    when (activity.activityType) {
                        "MATCH" -> GamePlaceholder(
                            label = "MATCH Game — coming soon"
                        )
                        "TRACE" -> GamePlaceholder(
                            label = "TRACE Game — coming soon"
                        )
                        "SPEECH" -> GamePlaceholder(
                            label = "SPEECH Game — coming soon"
                        )
                        "STORY" -> GamePlaceholder(
                            label = "STORY — coming soon"
                        )
                        "COUNT" -> GamePlaceholder(
                            label = "COUNT Game — coming soon"
                        )
                        "DRAG" -> GamePlaceholder(
                            label = "DRAG Game — coming soon"
                        )
                        else -> GamePlaceholder(
                            label = "Unknown activity type: ${activity.activityType}"
                        )
                    }
                }

                // ── Parent lock overlay ───────────────────────────────────
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
        Button(onClick = onExit) {
            Text("خروج")
        }
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
        Text(
            text = "أحسنت!",
            style = MaterialTheme.typography.headlineLarge
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "درجتك: $score من $total",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onFinish) {
            Text("تم")
        }
    }
}

// ── Scaffold ──────────────────────────────────────────────────────────────────
@Composable
private fun ActivityScaffold(
    title: String,
    progress: Float,
    remainingMs: Long,
    onBackRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackRequest) {
                Icon(Icons.Default.ArrowBack, contentDescription = "خروج")
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            val minutes = (remainingMs / 60_000).toInt()
            val seconds = ((remainingMs % 60_000) / 1_000).toInt()
            Text(
                text = "%02d:%02d".format(minutes, seconds),
                style = MaterialTheme.typography.labelLarge,
                color = if (remainingMs < 60_000)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.onSurface
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(16.dp))
        content()
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