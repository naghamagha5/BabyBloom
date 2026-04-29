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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.*
import com.babybloom.R
import com.babybloom.ui.theme.TraceOverlayScrim
import com.babybloom.ui.theme.TracePopupBackground
import com.babybloom.ui.theme.TraceSecondaryText
import com.babybloom.ui.theme.TraceSuccessText

// ─────────────────────────────────────────────────────────────────────────────
// GoodJobPopup
//
// Unified "أحسنت" celebration shown whenever a child gets a correct answer in
// ANY game (Story, Match, Count, Drag, Speech).
//
// Identical to TraceSuccessPopup — confetti Lottie + pop-in card — so the
// experience is consistent across all activities.
//
// Usage:
//   if (showCelebration) {
//       GoodJobPopup(coverage = scorePercent)  // coverage is 0f..1f
//   }
//
// The caller is responsible for:
//   • deciding when to show it (set a boolean in your state)
//   • playing SoundEffect.COMPLETE via AppSoundSettings BEFORE showing it
//     (the ActivityViewModel already does this via onAnswerSubmitted for
//      games that go through the shell; for mid-game celebrations the game
//      ViewModel should call it directly)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun GoodJobPopup(
    coverage: Float = 1f,   // 0f..1f — shown as percentage; pass 1f if not applicable
    label   : String = ""   // optional override; defaults to "أحسنت"
) {
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
            Text(
                text     = "🌟",
                fontSize = 76.sp
            )
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
            if (coverage < 1f) {
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