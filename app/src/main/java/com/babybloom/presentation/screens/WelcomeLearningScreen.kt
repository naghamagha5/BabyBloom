package com.babybloom.presentation.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
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
import androidx.compose.material3.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.babybloom.R
import com.babybloom.presentation.viewmodels.WelcomeLearningViewModel
import com.babybloom.ui.theme.DarkPurple
import com.babybloom.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@Composable
fun WelcomeLearningScreen(
    onNavigateToActivity: (activityId: String, sessionId: Long, childId: Long) -> Unit,
    viewModel: WelcomeLearningViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isCalmMode = uiState.isCalmMode

    LaunchedEffect(uiState.childId) {
        if (uiState.childId == 0L) return@LaunchedEffect
        delay(3_000)
        onNavigateToActivity("story_letters_d1", 0L, uiState.childId)
    }

    val backgroundRes = if (isCalmMode) R.drawable.ic_welcome_calm
    else R.drawable.ic_welcome_active
    val animalsRes    = if (isCalmMode) R.drawable.ic_calm_animals
    else R.drawable.ic_active_animals
    val decorationRes = if (isCalmMode) R.drawable.ic_calm_decoration
    else R.drawable.ic_active_decoration

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

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(
                            R.string.welcome_learning_greeting,
                            uiState.childName
                        ),
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
                        text = stringResource(R.string.welcome_learning_subtitle),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = DarkPurple.copy(alpha = 0.85f),
                            fontSize = 32.sp,
                            lineHeight = 44.sp
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}