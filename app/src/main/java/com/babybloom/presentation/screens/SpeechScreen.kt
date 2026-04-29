package com.babybloom.presentation.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.babybloom.R
import com.babybloom.domain.model.ActivityContent
import com.babybloom.presentation.viewmodels.MicState
import com.babybloom.presentation.viewmodels.SpeechCardState
import com.babybloom.presentation.viewmodels.SpeechViewModel
import com.babybloom.ui.theme.LocalGameColorScheme

// ─────────────────────────────────────────────────────────────────────────────
// SpeechScreen.kt
//
// Card/border colors now come from LocalGameColorScheme (accent / background),
// exactly like StoryScreen and the rest of the fixed game screens.
//
// The success popup is replaced by the unified GoodJobPopup.
// SpeechViewModel plays no SFX — voice audio only — so no sound changes needed.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SpeechScreen(
    currentItem: ActivityContent,
    isCalmMode: Boolean,
    onComplete: (elapsedMs: Long, attempts: Int, confidence: Float?, isCorrect: Boolean) -> Unit,
    viewModel: SpeechViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val colors  = LocalGameColorScheme.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        permissionDenied = !granted
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(currentItem.contentId, hasPermission) {
        if (hasPermission) viewModel.loadCard(currentItem, isCalmMode, onComplete)
    }

    val cardState by viewModel.cardState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            permissionDenied -> PermissionDeniedBlock()
            !hasPermission   -> CircularProgressIndicator(color = colors.accent)
            else -> when (val state = cardState) {
                is SpeechCardState.Loading -> CircularProgressIndicator(color = colors.accent)
                is SpeechCardState.Offline -> OfflineBlock()
                is SpeechCardState.Card    -> {
                    SpeechCardLayout(state, isCalmMode)
                    // ── Unified celebration popup ──────────────────────────
                    if (state.showSuccess) {
                        GoodJobPopup()
                    }
                }
            }
        }
    }
}

// ── Listen and repeat banner ──────────────────────────────────────────────────
@Composable
private fun ListenAndRepeatBanner() {
    val colors = LocalGameColorScheme.current
    Box(
        modifier = Modifier
            .wrapContentWidth()
            .clip(RoundedCornerShape(50))
            .border(
                width = 1.5.dp,
                color = Color.Black.copy(alpha = 0.15f),
                shape = RoundedCornerShape(50)
            )
            .padding(horizontal = 20.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                listOf(12.dp, 18.dp, 14.dp).forEach { h ->
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(h)
                            .clip(RoundedCornerShape(50))
                            // Sound-wave bars use accent color from scheme
                            .background(colors.accent.copy(alpha = 0.7f))
                    )
                }
            }
            Text(
                text = stringResource(R.string.speech_listen_and_repeat),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            )
            Icon(
                imageVector = Icons.Default.VolumeUp,
                contentDescription = null,
                // Icon tint uses accent from scheme
                tint = colors.accent,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

// ── Main card layout ──────────────────────────────────────────────────────────
@Composable
private fun SpeechCardLayout(state: SpeechCardState.Card, isCalmMode: Boolean) {
    val context = LocalContext.current
    val colors  = LocalGameColorScheme.current
    val mood    = if (isCalmMode) "calm" else "active"

    // Card background and border from scheme — replaces hardcoded CalmCardColor/ActiveCardColor
    val cardColor  = colors.background
    val cardBorder = colors.accent

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            ListenAndRepeatBanner()

            // ── Image + label card ────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .border(3.dp, cardBorder, RoundedCornerShape(24.dp))
                    .background(cardColor)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.item.category == "ANIMAL") {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data("file:///android_asset/learning_content/visual/$mood/${state.item.contentId}.png")
                                .build(),
                            contentDescription = state.item.labelAr,
                            modifier = Modifier
                                .size(180.dp)
                                .align(Alignment.CenterHorizontally),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        ContentImage(
                            asset = state.imageAsset,
                            label = state.item.labelAr,
                            modifier = Modifier
                                .size(180.dp)
                                .align(Alignment.CenterHorizontally),
                            applyTint = state.item.category != "COLOR"
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = state.item.labelAr,
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp,
                            // Label uses accent color from scheme
                            color = colors.accent
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            MicIndicator(micState = state.micState)

            AttemptDots(attempts = state.attempts, total = 3)

            Spacer(Modifier.height(8.dp))
        }
        // GoodJobPopup is rendered in the parent SpeechScreen composable
        // so it sits above the entire layout — no inline popup here.
    }
}

// ── Mic indicator ─────────────────────────────────────────────────────────────
@Composable
private fun MicIndicator(micState: MicState) {
    val bgColor = when (micState) {
        MicState.Idle       -> MaterialTheme.colorScheme.surfaceVariant
        MicState.Listening  -> MaterialTheme.colorScheme.primary
        MicState.Processing -> MaterialTheme.colorScheme.secondary
    }
    val icon = when (micState) {
        MicState.Listening -> Icons.Default.Mic
        else               -> Icons.Default.MicOff
    }
    val iconTint = when (micState) {
        MicState.Listening -> MaterialTheme.colorScheme.onPrimary
        else               -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val label = when (micState) {
        MicState.Idle       -> stringResource(R.string.speech_mic_idle)
        MicState.Listening  -> stringResource(R.string.speech_mic_listening)
        MicState.Processing -> stringResource(R.string.speech_mic_processing)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(36.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 18.sp
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ── Attempt dots ──────────────────────────────────────────────────────────────
@Composable
private fun AttemptDots(attempts: Int, total: Int) {
    val colors = LocalGameColorScheme.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(total) { i ->
            val used = i < attempts
            Box(
                modifier = Modifier
                    .size(if (used) 14.dp else 10.dp)
                    .background(
                        // Used attempt dots → accent (progress); remaining → faded
                        color = if (used) colors.accent
                        else colors.accent.copy(alpha = 0.25f),
                        shape = CircleShape
                    )
            )
        }
    }
}

// ── Permission denied ─────────────────────────────────────────────────────────
@Composable
private fun PermissionDeniedBlock() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.MicOff,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.speech_permission_denied_title),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.speech_permission_denied_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}

// ── Offline block ─────────────────────────────────────────────────────────────
@Composable
private fun OfflineBlock() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.WifiOff,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.speech_offline_title),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.speech_offline_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}