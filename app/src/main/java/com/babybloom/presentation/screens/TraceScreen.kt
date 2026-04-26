package com.babybloom.presentation.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airbnb.lottie.compose.*
import com.babybloom.R
import com.babybloom.domain.model.ActivityContent
import com.babybloom.presentation.viewmodels.TraceResult
import com.babybloom.presentation.viewmodels.TraceState
import com.babybloom.presentation.viewmodels.TraceUiState
import com.babybloom.presentation.viewmodels.TraceViewModel
import com.babybloom.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.*

// ─────────────────────────────────────────────────────────────────────────────
// Content type
// ─────────────────────────────────────────────────────────────────────────────

private enum class TraceContentType { LETTER, NUMBER, SHAPE }

private fun contentTypeOf(contentId: String) = when {
    contentId.startsWith("letter_") -> TraceContentType.LETTER
    contentId.startsWith("number_") -> TraceContentType.NUMBER
    else                             -> TraceContentType.SHAPE
}

@Composable
private fun instructionText(type: TraceContentType): String = when (type) {
    TraceContentType.LETTER -> stringResource(R.string.trace_instruction_letter)
    TraceContentType.NUMBER -> stringResource(R.string.trace_instruction_number)
    TraceContentType.SHAPE  -> stringResource(R.string.trace_instruction_shape)
}

// ─────────────────────────────────────────────────────────────────────────────
// Entry point
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun TraceScreen(
    currentItem: ActivityContent,
    isCalmMode:  Boolean,
    onComplete:  (TraceResult) -> Unit,
    viewModel:   TraceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(currentItem.contentId) { viewModel.loadItem(currentItem, isCalmMode) }
    LaunchedEffect(uiState) {
        if (uiState is TraceUiState.ItemComplete)
            onComplete((uiState as TraceUiState.ItemComplete).result)
    }

    val context = LocalContext.current
    val traceDrawableId = remember(currentItem.contentId) {
        context.resources.getIdentifier(
            "${currentItem.contentId}_trace", "drawable", context.packageName
        ).takeIf { it != 0 }
    }
    val letterDrawableId = remember(currentItem.contentId) {
        context.resources.getIdentifier(
            currentItem.contentId, "drawable", context.packageName
        ).takeIf { it != 0 }
    }
    val contentType = remember(currentItem.contentId) { contentTypeOf(currentItem.contentId) }

    // Accent / covered colours from Color.kt — no hardcoding
    val accentColor  = if (isCalmMode) TraceCalmAccent  else TraceActiveAccent
    val coveredColor = if (isCalmMode) TraceCalmCovered else TraceActiveCovered

    Box(Modifier.fillMaxSize()) {
        when (val s = uiState) {

            is TraceUiState.Idle   -> TraceLoadingContent()
            is TraceUiState.NoPath -> TraceNoPathFallback(s.contentId)

            is TraceUiState.Tracing -> TraceGameScreen(
                state           = s.state,
                traceDrawableId = traceDrawableId,
                accentColor     = accentColor,
                coveredColor    = coveredColor,
                contentType     = contentType,
                onDragStart     = { cs, p -> viewModel.onDragStart(cs, p) },
                onDrag          = { cs, p -> viewModel.onDrag(cs, p) },
                onDragEnd       = { viewModel.onDragEnd() }
            )

            is TraceUiState.RevealContent -> TraceRevealScreen(
                state            = s.state,
                letterDrawableId = letterDrawableId,
                accentColor      = accentColor,
                contentType      = contentType
            )

            is TraceUiState.ShowSuccess -> {
                // Same colored-letter background + popup on top
                TraceRevealScreen(
                    state            = s.state,
                    letterDrawableId = letterDrawableId,
                    accentColor      = accentColor,
                    contentType      = contentType
                )
                TraceSuccessPopup(coverage = s.finalScore)
            }

            is TraceUiState.ShowEncouraging -> {
                // Show trace SVG with all circles colored so the child sees the letter
                TraceGameScreen(
                    state = s.state.copy(
                        coloredIndices = s.state.pathData.circles.indices.toSet(),
                        showHandHint   = false
                    ),
                    traceDrawableId = traceDrawableId,
                    accentColor     = accentColor,
                    coveredColor    = coveredColor,
                    contentType     = contentType,
                    onDragStart     = { _, _ -> },
                    onDrag          = { _, _ -> },
                    onDragEnd       = {}
                )
                TraceEncouragingPopup(s.attemptsDone, s.isLastAttempt)
            }

            is TraceUiState.ItemComplete -> { /* parent navigates */ }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Active tracing screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TraceGameScreen(
    state:           TraceState,
    traceDrawableId: Int?,
    accentColor:     Color,
    coveredColor:    Color,
    contentType:     TraceContentType,
    onDragStart:     (canvasSize: Offset, pos: Offset) -> Unit,
    onDrag:          (canvasSize: Offset, pos: Offset) -> Unit,
    onDragEnd:       () -> Unit
) {
    val animCoverage by animateFloatAsState(
        state.coverage,
        spring(stiffness = Spring.StiffnessMediumLow),
        label = "cov"
    )
    val infinite   = rememberInfiniteTransition(label = "inf")
    val pulseScale by infinite.animateFloat(
        0.70f, 1.55f,
        infiniteRepeatable(tween(800, easing = EaseInOut), RepeatMode.Reverse),
        "pulse"
    )
    val hintAlpha by animateFloatAsState(
        if (state.showHandHint) 1f else 0f, tween(300), label = "ha"
    )

    // Tracks the actual pixel size of the rendered SVG (after ContentScale.Fit)
    var svgRenderedSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(4.dp))
        TraceInstructionBadge(text = instructionText(contentType), accentColor = accentColor)
        Spacer(Modifier.height(8.dp))
        TraceProgressBar(progress = animCoverage, accentColor = accentColor)
        Spacer(Modifier.height(10.dp))

        // ── SVG area — sits on the transparent surface background ─────────────
        //
        // ContentScale.Fit preserves the SVG's natural aspect ratio and centers it.
        // For narrow letters (e.g. alef 18×101) this means a narrow centered strip.
        // For wide shapes (e.g. circle 100×100) this fills the box evenly.
        //
        // The Canvas is sized to EXACTLY the rendered SVG pixels so dot circle
        // coordinates (normalised 0..1 × rendered size) land precisely on the
        // white circles drawn in the SVG.

        Box(
            modifier         = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 32.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            // SVG: uniform TraceSvgGray tint — letter body and logic_* paths
            // are both non-transparent, so SrcIn turns everything the same gray.
            // The Canvas will repaint white holes (uncovered) or coveredColor (touched).
            if (traceDrawableId != null) {
                Image(
                    painter            = painterResource(traceDrawableId),
                    contentDescription = null,
                    contentScale       = ContentScale.Fit,
                    colorFilter        = ColorFilter.tint(TraceSvgGray, BlendMode.SrcIn),
                    modifier           = Modifier
                        .fillMaxSize()
                        .onSizeChanged { containerSize ->
                            // Replicate the ContentScale.Fit sizing math to get the actual
                            // pixel rect the SVG occupies inside the container.
                            val vpRatio    = state.pathData.viewportRatio
                            val cW         = containerSize.width.toFloat()
                            val cH         = containerSize.height.toFloat()
                            val cR         = cW / cH
                            val (rW, rH)   = if (vpRatio > cR) {
                                cW to (cW / vpRatio)
                            } else {
                                (cH * vpRatio) to cH
                            }
                            svgRenderedSize = IntSize(rW.toInt(), rH.toInt())
                        }
                )
            }

            // Canvas — same pixel size as the rendered SVG
            if (svgRenderedSize != IntSize.Zero) {
                val svgW = with(density) { svgRenderedSize.width.toDp() }
                val svgH = with(density) { svgRenderedSize.height.toDp() }

                Canvas(
                    modifier = Modifier
                        .size(svgW, svgH)
                        .pointerInput(svgRenderedSize) {
                            detectDragGestures(
                                onDragStart = { p ->
                                    onDragStart(
                                        Offset(
                                            svgRenderedSize.width.toFloat(),
                                            svgRenderedSize.height.toFloat()
                                        ),
                                        p
                                    )
                                },
                                onDrag = { ch, _ ->
                                    onDrag(
                                        Offset(
                                            svgRenderedSize.width.toFloat(),
                                            svgRenderedSize.height.toFloat()
                                        ),
                                        ch.position
                                    )
                                },
                                onDragEnd    = onDragEnd,
                                onDragCancel = onDragEnd
                            )
                        }
                ) {
                    // 1. Dot circles (white holes + colored fills)
                    drawDotCircles(state, coveredColor, accentColor)

                    // 2. Start-point pulse before first touch
                    val hintPts = state.pathData.hintPath
                    if (state.coloredIndices.isEmpty() && hintPts.isNotEmpty()) {
                        drawStartPulse(
                            center = Offset(
                                hintPts.first().x * size.width,
                                hintPts.first().y * size.height
                            ),
                            scale = pulseScale
                        )
                    }

                    // 3. Animated hand-hint orb along the logic path
                    if (hintAlpha > 0f) {
                        val strokes = state.pathData.orderedHintStrokes
                        val si      = state.handHintIndex / 10_000
                        val pi      = state.handHintIndex % 10_000
                        val stroke  = strokes.getOrNull(si)
                        val pt      = stroke?.getOrNull(pi)
                        if (pt != null) {
                            val pos  = Offset(pt.x * size.width, pt.y * size.height)
                            val prev = stroke.getOrNull(pi - 1)
                                ?.let { p -> Offset(p.x * size.width, p.y * size.height) }
                            drawHandHint(pos, prev, hintAlpha)
                        }
                    }
                }
            }
        }

        // Label below the SVG — uses accentColor (no hardcoding)
        Text(
            text       = state.item.labelAr,
            fontSize   = 50.sp,
            fontWeight = FontWeight.ExtraBold,
            color      = accentColor,
            textAlign  = TextAlign.Center,
            modifier   = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reveal screen — colored letter after success (no popup)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TraceRevealScreen(
    state:            TraceState,
    letterDrawableId: Int?,
    accentColor:      Color,
    contentType:      TraceContentType
) {
    var popped by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(60L); popped = true }
    val scale by animateFloatAsState(
        if (popped) 1f else 0.55f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "sc"
    )
    val revealAlpha by animateFloatAsState(
        if (popped) 1f else 0f, tween(350), label = "al"
    )

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(4.dp))
        TraceInstructionBadge(text = instructionText(contentType), accentColor = accentColor)
        Spacer(Modifier.height(8.dp))
        TraceProgressBar(progress = 1f, accentColor = accentColor)

        Spacer(Modifier.weight(0.3f))

        // Centered square card with pop-in animation
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxWidth(0.85f)
                .border(6.dp, accentColor, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(TraceCardBackground)
                .graphicsLayer(scaleX = scale, scaleY = scale, alpha = revealAlpha),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier            = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (letterDrawableId != null) {
                    Image(
                        painter            = painterResource(letterDrawableId),
                        contentDescription = state.item.labelAr,
                        contentScale       = ContentScale.Fit,
                        colorFilter        = ColorFilter.tint(accentColor, BlendMode.SrcIn),
                        modifier           = Modifier
                            .fillMaxWidth(0.6f)
                            .aspectRatio(1f)
                    )
                } else {
                    Text(
                        state.item.labelAr,
                        fontSize   = 100.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color      = accentColor,
                        textAlign  = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    text       = state.item.labelAr,
                    fontSize   = 48.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = accentColor,
                    textAlign  = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.weight(0.7f))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared sub-composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TraceInstructionBadge(text: String, accentColor: Color) {
    Box(
        modifier = Modifier
            .border(1.5.dp, TraceBadgeBorder, RoundedCornerShape(50))
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Text on the right, hand icon on the left (Arabic RTL reads right-to-left)
            Text(
                text       = text,
                fontSize   = 17.sp,
                fontWeight = FontWeight.Bold,
                color      = TraceBadgeText,
                maxLines   = 1,
                style      = LocalTextStyle.current.copy(textDirection = TextDirection.Rtl)
            )
            Spacer(Modifier.width(8.dp))
            Image(
                painter            = painterResource(R.drawable.front_hand),
                contentDescription = null,
                modifier           = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
private fun TraceProgressBar(progress: Float, accentColor: Color) {
    Row(
        modifier          = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(50))
                    .background(if (progress >= 0.8f) TraceStartPulse else accentColor)
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text  = "${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = TraceSecondaryText
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Popups
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun TraceSuccessPopup(coverage: Float) {
    val infinite = rememberInfiniteTransition(label = "lp")
    val lottieProgress by infinite.animateFloat(
        0f, 1f, infiniteRepeatable(tween(2_000), RepeatMode.Restart), "lv"
    )
    val scale by animateFloatAsState(
        1f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
        label = "sc"
    )
    val popAlpha by animateFloatAsState(1f, tween(350), label = "al")

    val lottieComposition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.confetti)
    )

    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(TraceOverlayScrim),
        contentAlignment = Alignment.Center
    ) {
        // Lottie confetti layer (behind the card)
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
                text     = stringResource(R.string.trace_success_emoji),
                fontSize = 76.sp
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text       = stringResource(R.string.trace_success_title),
                fontSize   = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                color      = TraceSuccessText,
                textAlign  = TextAlign.Center,
                style      = LocalTextStyle.current.copy(textDirection = TextDirection.Rtl)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text      = stringResource(R.string.trace_coverage_format, (coverage * 100).toInt()),
                style     = MaterialTheme.typography.titleLarge,
                color     = TraceSecondaryText,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun TraceEncouragingPopup(attemptsDone: Int, isLastAttempt: Boolean) {
    val scale by animateFloatAsState(
        1f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
        label = "es"
    )
    val popAlpha by animateFloatAsState(1f, tween(350), label = "ea")

    val remaining     = TraceViewModel.MAX_ATTEMPTS - attemptsDone
    val remainingText = if (remaining == 1)
        stringResource(R.string.trace_remaining_one)
    else
        stringResource(R.string.trace_remaining_format, remaining)

    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(TraceOverlayScrim),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .graphicsLayer(scaleX = scale, scaleY = scale, alpha = popAlpha)
                .background(TracePopupBackground, RoundedCornerShape(28.dp))
                .padding(horizontal = 48.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text     = if (isLastAttempt) stringResource(R.string.trace_last_attempt_emoji)
                else               stringResource(R.string.trace_try_again_emoji),
                fontSize = 56.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text       = if (isLastAttempt) stringResource(R.string.trace_last_attempt_title)
                else               stringResource(R.string.trace_try_again_title),
                fontSize   = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color      = TraceWarningText,
                textAlign  = TextAlign.Center,
                style      = LocalTextStyle.current.copy(textDirection = TextDirection.Rtl)
            )
            if (!isLastAttempt) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text      = remainingText,
                    style     = LocalTextStyle.current.copy(textDirection = TextDirection.Rtl),
                    color     = TraceSecondaryText,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Canvas draw extensions
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Draws dot circles on top of the uniformly-gray SVG.
 *
 * Layer order (bottom → top):
 *   SVG tinted [TraceSvgGray]   — letter body + logic paths all same gray
 *   White circles               — restores the "empty hole" look for uncovered dots
 *   [coveredColor] circles      — satisfying fill-in when the child touches
 *   [borderColor] ring          — thin accent ring makes dots pop against gray
 */
private fun DrawScope.drawDotCircles(
    state:       TraceState,
    coveredColor: Color,
    borderColor:  Color
) {
    val strokeWidth = 7f

    state.pathData.circles.forEachIndexed { i, circle ->
        val cx      = circle.center.x * size.width
        val cy      = circle.center.y * size.height
        val r       = circle.radius   * size.width
        val center  = Offset(cx, cy)
        val touched = i in state.coloredIndices

        // Accent border ring (same for covered and uncovered)
        drawCircle(
            color  = borderColor,
            radius = r + strokeWidth / 2,
            center = center,
            style  = Stroke(width = strokeWidth)
        )

        if (touched) {
            // Filled radial-gradient circle
            drawCircle(
                brush = Brush.radialGradient(
                    0.0f to coveredColor,
                    0.7f to coveredColor.copy(alpha = 0.95f),
                    1.0f to coveredColor.copy(alpha = 0.75f),
                    center = center,
                    radius = r * 1.05f
                ),
                radius = r * 1.05f,
                center = center
            )
            // Inner white shimmer
            drawCircle(Color.White.copy(alpha = 0.30f), r * 0.38f, center)
        } else {
            // White fill restores the "empty hole" over the gray SVG
            drawCircle(Color.White, r, center)
        }
    }
}

/** Pulsing [TraceStartPulse] ring + white dot — marks the path start point. */
private fun DrawScope.drawStartPulse(center: Offset, scale: Float) {
    drawCircle(TraceStartPulse.copy(alpha = 0.18f), 24.dp.toPx() * scale, center)
    drawCircle(TraceStartPulse.copy(alpha = 0.65f), 14.dp.toPx(), center)
    drawCircle(Color.White.copy(alpha = 0.90f),      6.dp.toPx(), center)
}

/**
 * Animated hand-hint orb.
 * All colours come from Color.kt: [HintOrbColor], [HintFingerColor].
 * [alpha] lets it fade in/out smoothly on drag start/end.
 */
private fun DrawScope.drawHandHint(position: Offset, prev: Offset?, alpha: Float) {
    if (alpha <= 0f) return

    // Outer glow ring
    drawCircle(
        Brush.radialGradient(
            listOf(HintOrbColor.copy(alpha = 0.28f * alpha), Color.Transparent),
            center = position, radius = 36.dp.toPx()
        ),
        36.dp.toPx(), position
    )
    // Central orb
    drawCircle(
        Brush.radialGradient(
            listOf(Color.White.copy(alpha = alpha), HintOrbColor.copy(alpha = alpha)),
            center = position, radius = 18.dp.toPx()
        ),
        18.dp.toPx(), position
    )
    // Direction chevron
    prev?.let { p ->
        val dx  = position.x - p.x; val dy = position.y - p.y
        val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val nx  = dx / len; val ny = dy / len
        val tip   = Offset(position.x + nx * 30.dp.toPx(), position.y + ny * 30.dp.toPx())
        val left  = Offset(position.x - ny *  9.dp.toPx(), position.y + nx *  9.dp.toPx())
        val right = Offset(position.x + ny *  9.dp.toPx(), position.y - nx *  9.dp.toPx())
        drawPath(
            Path().apply {
                moveTo(left.x, left.y)
                lineTo(tip.x, tip.y)
                lineTo(right.x, right.y)
            },
            HintOrbColor.copy(alpha = 0.85f * alpha),
            style = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
    // Finger stub below the orb
    val fw = 10.dp.toPx(); val fh = 17.dp.toPx()
    drawRoundRect(
        HintFingerColor.copy(alpha = alpha),
        Offset(position.x - fw / 2f, position.y + 16.dp.toPx()),
        Size(fw, fh), CornerRadius(fw / 2f)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Fallbacks
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TraceLoadingContent() {
    Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun TraceNoPathFallback(contentId: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text      = stringResource(R.string.trace_no_path_title),
                style     = MaterialTheme.typography.bodyLarge,
                color     = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text      = stringResource(R.string.trace_no_path_needed, contentId),
                style     = MaterialTheme.typography.bodySmall,
                color     = TraceSecondaryText,
                textAlign = TextAlign.Center
            )
        }
    }
}