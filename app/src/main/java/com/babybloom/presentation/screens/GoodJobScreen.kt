package com.babybloom.presentation.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.*
import com.babybloom.R
import com.babybloom.ui.theme.DarkPurple
import com.babybloom.ui.theme.NavyDark
import kotlinx.coroutines.delay

// ── Duration the screen stays visible before auto-dismissing ──────────────────
private const val GOOD_JOB_DISPLAY_MS = 5_000L

@Composable
fun GoodJobScreen(
    score     : Int,
    total     : Int,
    onFinished: () -> Unit
) {
    // ── Auto-dismiss after 5 seconds ─────────────────────────────────────────
    LaunchedEffect(Unit) {
        delay(GOOD_JOB_DISPLAY_MS)
        onFinished()
    }

    // ── Pop-in scale for the whole composition ────────────────────────────────
    val popIn = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) {
        popIn.animateTo(
            targetValue   = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness    = Spring.StiffnessMediumLow
            )
        )
    }

    // ── Continuous gentle float for the animals ───────────────────────────────
    val infinite      = rememberInfiniteTransition(label = "float")

    // ── Bunting swing ─────────────────────────────────────────────────────────
    val buntingSwing by infinite.animateFloat(
        initialValue  = -3f,
        targetValue   =  3f,
        animationSpec = infiniteRepeatable(
            animation  = tween(2_200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "buntingSwing"
    )

    // ── Text pulse ────────────────────────────────────────────────────────────
    val textPulse by infinite.animateFloat(
        initialValue  = 1f,
        targetValue   = 1.1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(900, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "textPulse"
    )

    // ── Lottie confetti animation ─────────────────────────────────────────────
    val lottieProgress by infinite.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2000), RepeatMode.Restart),
        "confetti"
    )

    Box(
        modifier         = Modifier
            .fillMaxSize()
            .graphicsLayer { scaleX = popIn.value; scaleY = popIn.value },
        contentAlignment = Alignment.Center
    ) {

        // Layer 1 — background (pastel blobs + confetti)
        Image(
            painter            = painterResource(R.drawable.ic_game_background_active),
            contentDescription = null,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier.fillMaxSize()
        )

        // Layer 2 — Lottie Confetti Animation (full screen)
        val composition by rememberLottieComposition(
            LottieCompositionSpec.RawRes(R.raw.confetti)
        )

        LottieAnimation(
            composition = composition,
            progress = { (lottieProgress % 1.0f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxSize()
        )

        // Layer 3 — bunting flags at the top
        Image(
            painter            = painterResource(R.drawable.ic_good_job_bunting),
            contentDescription = null,
            contentScale       = ContentScale.FillWidth,
            modifier           = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .graphicsLayer { rotationZ = buntingSwing }
        )

        // Layer 4 — "أحسنت يا بطل!" text (below bunting, RTL)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 140.dp), // Adjust this value to position below bunting
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.good_job_text),
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Black,
                    color = DarkPurple,
                    fontSize = 42.sp,
                    lineHeight = 52.sp,
                    textDirection = TextDirection.Rtl
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = textPulse
                        scaleY = textPulse
                    }
            )

            // Optional: Show score
            Text(
                text = "$score / $total",
                style = TextStyle(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = NavyDark,
                    textAlign = TextAlign.Center,
                    textDirection = TextDirection.Rtl
                ),
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // Layer 5 — smiling animals group at the bottom
        Image(
            painter            = painterResource(R.drawable.good_job_animals),
            contentDescription = null,
            contentScale       = ContentScale.FillWidth,
            modifier           = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        )
    }
}
