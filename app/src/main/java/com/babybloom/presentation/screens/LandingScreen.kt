package com.babybloom.presentation.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.babybloom.R
import com.babybloom.di.SessionManager
import com.babybloom.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun LandingScreen(
    sessionManager: SessionManager,   // ← injected from NavGraph, no ViewModel needed
    onStartClick  : () -> Unit = {}
) {
    val configuration = LocalConfiguration.current
    val screenWidth   = configuration.screenWidthDp.dp
    val screenHeight  = configuration.screenHeightDp.dp

    // ── Used to call markLandingSeen() which is a suspend function ─────────
    val scope = rememberCoroutineScope()

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(GradientPinkDark, GradientPinkMedium, GradientPinkLight)
                    )
                )
        ) {

            // ── Brand name / slogan ────────────────────────────────────────
            Image(
                painter            = painterResource(id = R.drawable.ic_slogan),
                contentDescription = "Baby Bloom",
                contentScale       = ContentScale.Fit,
                modifier           = Modifier
                    .size(screenWidth * 0.90f)
                    .fillMaxWidth(1f)
                    .align(Alignment.TopCenter)
                    .padding(top = screenHeight * 0.001f)
            )

            // ── Brain illustration ─────────────────────────────────────────
            Image(
                painter            = painterResource(id = R.drawable.ic_brain),
                contentDescription = "Brain Character",
                contentScale       = ContentScale.Fit,
                modifier           = Modifier
                    .size(screenWidth * 8f)
                    .align(Alignment.Center)
                    .padding(top = screenHeight * 0.1f)
            )

            // ── Get Started button ─────────────────────────────────────────
            Button(
                onClick = {
                    scope.launch {
                        // ✅ Mark as seen BEFORE navigating
                        // Next time app opens, NavGraph reads hasSeenLanding = true
                        // and skips straight to Login — LandingScreen never shows again
                        sessionManager.markLandingSeen()
                    }
                    // Navigate immediately — don't wait for DataStore write
                    onStartClick()
                },
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(120.dp)
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 50.dp),
                shape  = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NavyDark,
                    contentColor   = Color.White
                )
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text(
                        text  = stringResource(R.string.btn_start),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize   = 25.sp
                        )
                    )
                    Image(
                        painter            = painterResource(id = R.drawable.arrow_left_circle),
                        contentDescription = null,
                        contentScale       = ContentScale.Fit,
                        modifier           = Modifier.size(35.dp)
                    )
                }
            }
        }
    }
}