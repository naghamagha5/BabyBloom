package com.babybloom.presentation.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babybloom.R
import com.babybloom.presentation.viewmodels.AssessmentUiState
import com.babybloom.presentation.viewmodels.AssessmentViewModel
import com.babybloom.ui.theme.DarkPurple
import kotlinx.coroutines.delay

@Composable
fun AssessmentScreen(
    childId: Long,
    onAssessmentComplete: () -> Unit,
    onExitAssessment: () -> Unit,
    viewModel: AssessmentViewModel = hiltViewModel()
) {
    LaunchedEffect(childId) { viewModel.startAssessment(childId) }

    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val currentState = state) {
        is AssessmentUiState.Loading -> AssessmentLoadingContent()
        is AssessmentUiState.Intro -> AssessmentIntroContent(
            childName = currentState.childName,
            isCalmMode = currentState.isCalmMode,
            showSpeechInternetDialog = currentState.showSpeechInternetDialog,
            onStart = viewModel::beginActivities,
            onDismissSpeechInternetDialog = viewModel::dismissSpeechInternetDialog,
            onExitAssessment = onExitAssessment
        )
        is AssessmentUiState.Playing -> AssessmentPlayingContent(
            state = currentState,
            childId = childId,
            onComplete = viewModel::onActivityComplete,
            onExitAssessment = onExitAssessment
        )
        is AssessmentUiState.Bootstrapping -> AssessmentBootstrappingContent()
        is AssessmentUiState.Complete -> {
            GoodJobScreen(
                score = currentState.correctCount,
                total = currentState.totalCount,
                onFinished = onAssessmentComplete
            )
        }
        is AssessmentUiState.Error -> AssessmentErrorContent(message = currentState.message)
    }
}

@Composable
private fun AssessmentIntroContent(
    childName: String,
    isCalmMode: Boolean,
    showSpeechInternetDialog: Boolean,
    onStart: () -> Unit,
    onDismissSpeechInternetDialog: () -> Unit,
    onExitAssessment: () -> Unit
) {
    val backgroundRes = if (isCalmMode) R.drawable.ic_welcome_calm else R.drawable.ic_welcome_active
    val animalsRes = if (isCalmMode) R.drawable.ic_calm_animals else R.drawable.ic_active_animals
    val decorationRes = if (isCalmMode) R.drawable.ic_calm_decoration else R.drawable.ic_active_decoration
    var isDialogVisible by remember(showSpeechInternetDialog) { mutableStateOf(showSpeechInternetDialog) }

    LaunchedEffect(showSpeechInternetDialog) {
        isDialogVisible = showSpeechInternetDialog
        if (showSpeechInternetDialog) return@LaunchedEffect
        delay(10_000)
        onStart()
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(backgroundRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Image(
                painter = painterResource(decorationRes),
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            )

            Image(
                painter = painterResource(animalsRes),
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .statusBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Spacer(modifier = Modifier.height(60.dp))

                Text(
                    text = stringResource(R.string.assessment_intro_greeting, childName),
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Black,
                        color = DarkPurple,
                        fontSize = 42.sp,
                        lineHeight = 52.sp
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = stringResource(R.string.assessment_intro_subtitle),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = DarkPurple.copy(alpha = 0.85f),
                        fontSize = 28.sp,
                        lineHeight = 40.sp
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(28.dp))
            }

            if (isDialogVisible) {
                SessionInternetDialog(
                    message = stringResource(R.string.session_internet_assessment_message),
                    onRetry = onStart,
                    onExit = {
                        isDialogVisible = false
                        onDismissSpeechInternetDialog()
                        onExitAssessment()
                    }
                )
            }
        }
    }
}

@Composable
private fun AssessmentPlayingContent(
    state: AssessmentUiState.Playing,
    childId: Long,
    onComplete: (score: Int, total: Int) -> Unit,
    onExitAssessment: () -> Unit
) {
    ActivityShellScreen(
        activityId = state.currentActivityId,
        sessionId = state.sessionId,
        childId = childId,
        contentId = state.currentContentId,
        stepIndex = state.currentIndex,
        isAssessment = true,
        isTest = state.isTest,
        assessmentCurrent = state.currentIndex + 1,
        assessmentTotal = state.totalCount,
        onActivityComplete = { score, total, _, _ -> onComplete(score, total) },
        onExit = onExitAssessment
    )
}

@Composable
private fun AssessmentLoadingContent() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun AssessmentBootstrappingContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.assessment_bootstrapping_message))
        }
    }
}

@Composable
private fun AssessmentErrorContent(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
    }
}
