package com.babybloom.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.babybloom.data.local.entity.LearningContentEntity
import com.babybloom.domain.model.ActivityContent
import com.babybloom.presentation.viewmodels.StoryCardState
import com.babybloom.presentation.viewmodels.StoryViewModel
import com.babybloom.util.ImageAsset

// ── Mood-aware card colors ────────────────────────────────────────────────────
private val ActiveCardColor = Color(0xFFFFF0B3)   // warm yellow for active mode
private val CalmCardColor   = Color(0xFFE8F4F0)   // muted mint for calm mode
private val ActiveCardBorder = Color(0xFFFFB347)  // orange border for active
private val CalmCardBorder   = Color(0xFFB2CFCA)  // muted teal border for calm

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

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (val state = cardState) {
            is StoryCardState.Intro      -> IntroScreen()           // ✅
            is StoryCardState.Loading    -> CircularProgressIndicator()
            is StoryCardState.LetterCard -> LetterCardLayout(state, isCalmMode)
            is StoryCardState.NumberCard -> NumberCardLayout(state, isCalmMode)
            is StoryCardState.SimpleCard -> SimpleCardLayout(state, isCalmMode)
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
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = "اسْتَمِع جَيِّداً",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            )
            // Sound wave dots
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                listOf(12.dp, 18.dp, 14.dp).forEach { h ->
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(h)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                    )
                }
            }
        }
    }
}

// ── Animal card with mood-aware background ────────────────────────────────────
@Composable
private fun AnimalCard(
    animal: LearningContentEntity,
    isCalmMode: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cardColor  = if (isCalmMode) CalmCardColor  else ActiveCardColor
    val cardBorder = if (isCalmMode) CalmCardBorder else ActiveCardBorder
    val mood = if (isCalmMode) "calm" else "active"
    val fileName = animal.id

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .border(3.dp, cardBorder, RoundedCornerShape(24.dp))
            .background(cardColor)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data("file:///android_asset/learning_content/visual/$mood/$fileName.png")
                    .build(),
                contentDescription = animal.labelAr,
                modifier = Modifier.size(160.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = animal.labelAr,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp
                ),
                textAlign = TextAlign.Center
            )
        }
    }
}

// ── Letter Card ───────────────────────────────────────────────────────────────
@Composable
private fun LetterCardLayout(
    state: StoryCardState.LetterCard,
    isCalmMode: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        // Listen banner
        ListenBanner()

        // Letter SVG — large and centered
        ContentImage(
            asset = state.letterImageAsset,
            label = state.letter.labelAr,
            modifier = Modifier.size(120.dp)
        )

        // Animal card below
        AnimalCard(
            animal = state.animal,
            isCalmMode = isCalmMode,
            modifier = Modifier.fillMaxWidth()
        )

        // Repeat progress dots
        RepeatDots(repeatsDone = state.repeatsDone, total = 3)

        Spacer(Modifier.height(8.dp))
    }
}

// ── Number Card ───────────────────────────────────────────────────────────────
@Composable
private fun NumberCardLayout(
    state: StoryCardState.NumberCard,
    isCalmMode: Boolean
) {
    val context    = LocalContext.current
    val cardColor  = if (isCalmMode) CalmCardColor  else ActiveCardColor
    val cardBorder = if (isCalmMode) CalmCardBorder else ActiveCardBorder
    val mood       = if (isCalmMode) "calm" else "active"

    // Scale size and columns based on animal count
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

        // Number SVG — outside the box, same position as letter SVG
        ContentImage(
            asset = state.numberImageAsset,
            label = state.number.labelAr,
            modifier = Modifier.size(120.dp)
        )

        // Box: animals centered + number label at bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .border(3.dp, cardBorder, RoundedCornerShape(24.dp))
                .background(cardColor)
                .padding(16.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Centered rows of animals
                rows.forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        row.forEach { animal ->
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data("file:///android_asset/learning_content/visual/$mood/${animal.id}.png")
                                    .build(),
                                contentDescription = animal.labelAr,
                                modifier = Modifier
                                    .size(animalSize)
                                    .padding(4.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = state.number.labelAr,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp
                    ),
                    textAlign = TextAlign.Center
                )
            }
        }

        RepeatDots(repeatsDone = state.repeatsDone, total = 3)
        Spacer(Modifier.height(8.dp))
    }
}

// ── Simple Card — ANIMAL, SHAPE, COLOR ───────────────────────────────────────
@Composable
private fun SimpleCardLayout(
    state: StoryCardState.SimpleCard,
    isCalmMode: Boolean
) {
    val cardColor  = if (isCalmMode) CalmCardColor  else ActiveCardColor
    val cardBorder = if (isCalmMode) CalmCardBorder else ActiveCardBorder

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        ListenBanner()

        // Content in mood card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .border(3.dp, cardBorder, RoundedCornerShape(24.dp))
                .background(cardColor)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ContentImage(
                    asset = state.imageAsset,
                    label = state.item.labelAr,
                    modifier = Modifier.size(180.dp),
                    applyTint = state.item.category != "COLOR"  // ✅ colors keep their own fill
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = state.item.labelAr,
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp
                    ),
                    textAlign = TextAlign.Center
                )
            }
        }

        RepeatDots(repeatsDone = state.repeatsDone, total = 3)
    }
}

// ── Shared image composable ───────────────────────────────────────────────────
@Composable
fun ContentImage(
    asset: ImageAsset,
    label: String,
    modifier: Modifier = Modifier,
    applyTint: Boolean = true   // ✅ new param — false for COLOR category
) {
    val context = LocalContext.current
    when (asset) {
        is ImageAsset.PngAsset -> {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data("file:///android_asset/${asset.path}")
                    .build(),
                contentDescription = label,
                modifier = modifier,
                contentScale = ContentScale.Fit
            )
        }
        is ImageAsset.SvgDrawable -> {
            val drawableId = context.resources.getIdentifier(
                asset.drawableName, "drawable", context.packageName
            )
            if (drawableId != 0) {
                androidx.compose.foundation.Image(
                    painter = painterResource(id = drawableId),
                    contentDescription = label,
                    modifier = modifier,
                    // ✅ FIX 2: only tint when caller allows it
                    colorFilter = if (applyTint)
                        ColorFilter.tint(colorResource(id = asset.tintColor))
                    else null
                )
            } else {
                Box(
                    modifier = modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleLarge,
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
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(total) { i ->
            Box(
                modifier = Modifier
                    .size(if (i < repeatsDone) 14.dp else 10.dp)
                    .background(
                        color = if (i < repeatsDone)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape
                    )
            )
        }
    }
}
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