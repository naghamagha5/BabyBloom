package com.babybloom.presentation.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.*
import com.babybloom.R
import com.babybloom.di.AppSoundSettings
import com.babybloom.ui.theme.TraceOverlayScrim
import com.babybloom.ui.theme.TracePopupBackground
import com.babybloom.ui.theme.TraceSecondaryText
import com.babybloom.ui.theme.TraceSuccessText
import com.babybloom.util.SoundEffect
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

// ─────────────────────────────────────────────────────────────────────────────
// Shared celebration popup. It owns complete.ogg so all activity celebrations
// use the same timing in normal sessions and assessment flow.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun GoodJobPopup(
    coverage: Float = -1f,
    label   : String = ""
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            GoodJobSoundEntryPoint::class.java
        ).appSoundSettings().playSoundEffect(SoundEffect.COMPLETE)
    }

    val infinite = rememberInfiniteTransition(label = "confetti_loop")
    val lottieProgress by infinite.animateFloat(
        initialValue   = 0f,
        targetValue    = 1f,
        animationSpec  = infiniteRepeatable(tween(2_000), RepeatMode.Restart),
        label          = "lottie_v"
    )

    // Pop-in scale
    var popped by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { popped = true }
    val scale by animateFloatAsState(
        targetValue   = if (popped) 1f else 0.55f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
        label         = "popup_scale"
    )
    val popAlpha by animateFloatAsState(
        targetValue   = if (popped) 1f else 0f,
        animationSpec = tween(350),
        label         = "popup_alpha"
    )

    val lottieComposition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.confetti)
    )

    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(TraceOverlayScrim),
        contentAlignment = Alignment.Center
    ) {
        // Confetti layer — behind the card
        LottieAnimation(
            composition = lottieComposition,
            progress    = { (lottieProgress % 1f).coerceIn(0f, 1f) },
            modifier    = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .graphicsLayer(scaleX = scale, scaleY = scale, alpha = popAlpha)
                .background(TracePopupBackground, RoundedCornerShape(28.dp))
                .padding(horizontal = 64.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = stringResource(R.string.trace_success_emoji), fontSize = 76.sp)

            Spacer(Modifier.height(12.dp))
            Text(
                text       = label.ifEmpty { "أحسنت!" },
                fontSize   = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                color      = TraceSuccessText,
                textAlign  = TextAlign.Center,
                style      = LocalTextStyle.current.copy(textDirection = TextDirection.Rtl)
            )
            // Only show coverage percentage when it's meaningful (< 100%)
            if (coverage >= 0f) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text      = "${(coverage * 100).toInt()}%",
                    style     = MaterialTheme.typography.titleLarge,
                    color     = TraceSecondaryText,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
private interface GoodJobSoundEntryPoint {
    fun appSoundSettings(): AppSoundSettings
}
