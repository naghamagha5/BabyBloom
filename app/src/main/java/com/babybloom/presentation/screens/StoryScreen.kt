package com.babybloom.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.babybloom.R
import com.babybloom.data.local.entity.LearningContentEntity
import com.babybloom.domain.model.ActivityContent
import com.babybloom.presentation.viewmodels.StoryCardState
import com.babybloom.presentation.viewmodels.StoryViewModel
import com.babybloom.ui.theme.LocalGameColorScheme
import com.babybloom.util.ImageAsset

// ─────────────────────────────────────────────────────────────────────────────
// StoryScreen.kt
// All card/border/text/asset colors come from LocalGameColorScheme, which the
// ActivityShellScreen provides and rotates each round via currentIndex seed.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun StoryScreen(
    currentItem: ActivityContent,
    isCalmMode: Boolean,
    onComplete: (elapsedMs: Long) -> Unit,
    viewModel: StoryViewModel = hiltViewModel()
) {
    LaunchedEffect(currentItem.contentId) {
        viewModel.loadCard(currentItem, isCalmMode, onComplete)
    }

    val cardState by viewModel.cardState.collectAsStateWithLifecycle()

    // ── Single read of the scheme; every child composable receives it via
    //    LocalGameColorScheme.current without any extra prop threading. ─────────
    val colors = LocalGameColorScheme.current

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (val state = cardState) {
            is StoryCardState.Intro      -> IntroScreen()
            is StoryCardState.Loading    -> CircularProgressIndicator(color = colors.accent)
            is StoryCardState.LetterCard -> LetterCardLayout(state)
            is StoryCardState.NumberCard -> NumberCardLayout(state)
            is StoryCardState.SimpleCard -> SimpleCardLayout(state)
        }
    }
}

// ── "Listen carefully" banner ─────────────────────────────────────────────────
@Composable
private fun ListenBanner() {
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
            Text(
                text = "اسْتَمِع جَيِّداً",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            )
            Icon(
                painter            = painterResource(id = R.drawable.ic_sound),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            // Sound wave dots
//            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
//                listOf(12.dp, 18.dp, 14.dp).forEach { h ->
//                    Box(
//                        modifier = Modifier
//                            .width(3.dp)
//                            .height(h)
//                            .clip(RoundedCornerShape(50))
//                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
//                    )
//                }
//            }
        }
    }
}

// ── Animal card ───────────────────────────────────────────────────────────────
@Composable
private fun AnimalCard(
    animal: LearningContentEntity,
    modifier: Modifier = Modifier
) {
    val colors  = LocalGameColorScheme.current
    val context = LocalContext.current
    val mood    = if (colors.isCalmMode) "calm" else "active"

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .border(3.dp, colors.accent, RoundedCornerShape(24.dp))  // accent border
            .background(colors.accent.copy(alpha = 0.2f))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data("file:///android_asset/learning_content/visual/$mood/${animal.id}.png")
                    .build(),
                contentDescription = animal.labelAr,
                modifier           = Modifier.size(160.dp),
                contentScale       = ContentScale.Fit
                // No colorFilter — PNG assets render as-is; tinting is for SVG drawables only
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text  = animal.labelAr,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Black,
                    fontSize   = 28.sp
                ),
                color     = colors.accent,        // label matches accent
                textAlign = TextAlign.Center
            )
        }
    }
}

// ── Letter Card ───────────────────────────────────────────────────────────────
@Composable
private fun LetterCardLayout(state: StoryCardState.LetterCard) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        ListenBanner()

        // Letter SVG — tinted with accent
        ContentImage(
            asset     = state.letterImageAsset,
            label     = state.letter.labelAr,
            modifier  = Modifier.size(120.dp),
            applyTint = true
        )

        AnimalCard(
            animal   = state.animal,
            modifier = Modifier.fillMaxWidth()
        )

        RepeatDots(repeatsDone = state.repeatsDone, total = 3)
        Spacer(Modifier.height(8.dp))
    }
}

// ── Number Card ───────────────────────────────────────────────────────────────
@Composable
private fun NumberCardLayout(state: StoryCardState.NumberCard) {
    val colors  = LocalGameColorScheme.current
    val context = LocalContext.current
    val mood    = if (colors.isCalmMode) "calm" else "active"

    val (animalSize, columns) = when (state.animals.size) {
        1       -> 160.dp to 1
        2       -> 130.dp to 2
        in 3..4 -> 100.dp to 2
        in 5..6 -> 90.dp  to 3
        else    -> 80.dp  to 3
    }
    val rows = state.animals.chunked(columns)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        ListenBanner()

        // Number SVG — tinted with accent
        ContentImage(
            asset     = state.numberImageAsset,
            label     = state.number.labelAr,
            modifier  = Modifier.size(120.dp),
            applyTint = true
        )

        // Card: animals + number label
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .border(3.dp, colors.accent, RoundedCornerShape(24.dp))  // accent border
                .background(colors.accent.copy(alpha = 0.2f))
                .padding(16.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier            = Modifier.fillMaxWidth()
            ) {
                rows.forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier              = Modifier.fillMaxWidth()
                    ) {
                        row.forEach { animal ->
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data("file:///android_asset/learning_content/visual/$mood/${animal.id}.png")
                                    .build(),
                                contentDescription = animal.labelAr,
                                modifier           = Modifier
                                    .size(animalSize)
                                    .padding(4.dp),
                                contentScale       = ContentScale.Fit
                                // PNG assets — no tint
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text  = state.number.labelAr,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Black,
                        fontSize   = 28.sp
                    ),
                    color     = colors.accent,
                    textAlign = TextAlign.Center
                )
            }
        }

        RepeatDots(repeatsDone = state.repeatsDone, total = 3)
        Spacer(Modifier.height(8.dp))
    }
}

// ── Simple Card — ANIMAL · SHAPE · COLOR ─────────────────────────────────────
@Composable
private fun SimpleCardLayout(state: StoryCardState.SimpleCard) {
    val colors = LocalGameColorScheme.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        ListenBanner()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .border(3.dp, colors.accent, RoundedCornerShape(24.dp))  // accent border
                .background(colors.accent.copy(alpha = 0.2f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ContentImage(
                    asset      = state.imageAsset,
                    label      = state.item.labelAr,
                    modifier   = Modifier.size(180.dp),
                    // COLOR category SVGs represent the actual color — never tint them
                    // everything else (shapes, animals as SVG) gets accent tint
                    applyTint  = state.item.category != "COLOR"
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text  = state.item.labelAr,
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Black,
                        fontSize   = 32.sp
                    ),
                    // COLOR category: keep text neutral so it doesn't clash with the
                    // color the child is learning; everything else uses accent
                    color     = if (state.item.category == "COLOR")
                        MaterialTheme.colorScheme.onSurface
                    else
                        colors.accent,
                    textAlign = TextAlign.Center
                )
            }
        }

        RepeatDots(repeatsDone = state.repeatsDone, total = 3)
    }
}

// ── Shared image composable ───────────────────────────────────────────────────
/**
 * Renders a PNG asset or an SVG drawable.
 *
 * @param applyTint  When true the SVG is tinted with [LocalGameColorScheme].accent.
 *                   Pass false for COLOR-category items whose SVG *is* the color.
 */
@Composable
fun ContentImage(
    asset     : ImageAsset,
    label     : String,
    modifier  : Modifier = Modifier,
    applyTint : Boolean  = true
) {
    val colors  = LocalGameColorScheme.current
    val context = LocalContext.current

    when (asset) {
        is ImageAsset.PngAsset -> {
            // PNG assets carry their own colours — never tint
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data("file:///android_asset/${asset.path}")
                    .build(),
                contentDescription = label,
                modifier           = modifier,
                contentScale       = ContentScale.Fit
            )
        }

        is ImageAsset.SvgDrawable -> {
            val drawableId = context.resources.getIdentifier(
                asset.drawableName, "drawable", context.packageName
            )
            if (drawableId != 0) {
                androidx.compose.foundation.Image(
                    painter            = painterResource(id = drawableId),
                    contentDescription = label,
                    modifier           = modifier,
                    // Tint SVG with the shell-provided accent; skip for COLOR category
                    colorFilter        = if (applyTint)
                        ColorFilter.tint(colors.accent)
                    else
                        null
                )
            } else {
                // Fallback: labelled box in accent color
                Box(
                    modifier = modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text      = label,
                        style     = MaterialTheme.typography.titleLarge,
                        color     = colors.accent,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// ── Repeat progress dots ──────────────────────────────────────────────────────
@Composable
private fun RepeatDots(repeatsDone: Int, total: Int) {
    val colors = LocalGameColorScheme.current

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        repeat(total) { i ->
            val filled = i < repeatsDone
            Box(
                modifier = Modifier
                    .size(if (filled) 14.dp else 10.dp)
                    .background(
                        // Filled dots = correct (green) so the child sees progress
                        // clearly; empty dots = a faded version of accent
                        color = if (filled)
                            colors.correct
                        else
                            colors.accent.copy(alpha = 0.25f),
                        shape = CircleShape
                    )
            )
        }
    }
}

// ── Intro screen ──────────────────────────────────────────────────────────────
@Composable
private fun IntroScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // Animated sound wave rings
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                )
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                )
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Text(
                text = "استمع جيدًا وكرّر ورائي",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp
                ),
                textAlign = TextAlign.Center
            )

            // Sound wave dots
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(16.dp, 26.dp, 20.dp, 26.dp, 16.dp).forEach { h ->
                    Box(
                        modifier = Modifier
                            .width(5.dp)
                            .height(h)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                    )
                }
            }
        }
    }
}