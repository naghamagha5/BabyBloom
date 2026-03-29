package com.babybloom.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.babybloom.R
import com.babybloom.domain.model.ParsedInsight
import com.babybloom.ui.theme.*

// ─── AI Insights Tab ──────────────────────────────────────────────────────────
@Composable
fun ChildAiInsightsTab(
    parsedInsight: ParsedInsight?,
    isLoading: Boolean,
    onRefresh: () -> Unit
) {
    val scrollState    = rememberScrollState()
    val buttonGradient = Brush.horizontalGradient(
        colors = listOf(GradientPurpleMedium, GradientPurpleDark)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AiPageHeader()

        when {
            isLoading -> AiLoadingCard()
            else      -> AiInsightContent(parsed = parsedInsight)
        }

        Box(
            modifier         = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(brush = buttonGradient, shape = RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier    = Modifier.size(22.dp),
                    color       = White,
                    strokeWidth = 2.dp
                )
            } else {
                TextButton(
                    onClick  = onRefresh,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            painter            = painterResource(R.drawable.ic_refresh),
                            contentDescription = null,
                            tint               = White,
                            modifier           = Modifier.size(18.dp)
                        )
                        Text(
                            text  = stringResource(R.string.ai_refresh_button),
                            style = MaterialTheme.typography.labelLarge.copy(color = White)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

// ─── Page Header ──────────────────────────────────────────────────────────────
@Composable
private fun AiPageHeader() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier          = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter            = painterResource(R.drawable.ic_brain_ai),
                contentDescription = null,
                tint               = Color.Unspecified,
                modifier           = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text  = stringResource(R.string.ai_insights_title),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color      = NavyDark
                )
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text     = stringResource(R.string.ai_insights_subtitle),
            style    = MaterialTheme.typography.bodyMedium.copy(color = LinkColor),
            modifier = Modifier.padding(start = 30.dp)
        )
    }
}

// ─── Loading State ────────────────────────────────────────────────────────────
@Composable
private fun AiLoadingCard() {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(color = ProgressPurple, strokeWidth = 3.dp)
            Text(
                text      = stringResource(R.string.ai_loading),
                style     = MaterialTheme.typography.bodyMedium,
                color     = TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─── Empty State ──────────────────────────────────────────────────────────────
@Composable
private fun AiEmptyCard() {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                painter            = painterResource(R.drawable.ic_brain_ai),
                contentDescription = null,
                tint               = Color.Unspecified,
                modifier           = Modifier.size(48.dp)
            )
            Text(
                text      = stringResource(R.string.ai_empty_title),
                style     = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color      = NavyDark
                ),
                textAlign = TextAlign.Center
            )
            Text(
                text      = stringResource(R.string.ai_empty_subtitle),
                style     = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─── All Sections ─────────────────────────────────────────────────────────────
@Composable
private fun AiInsightContent(parsed: ParsedInsight?) {
    if (parsed == null) {
        AiEmptyCard()
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SummarySection(parsed = parsed)
        QuickTipsSection(parsed = parsed)
        DetailedGuidanceSection(parsed = parsed)
    }
}

// ─── Section 1: ملخّص الرؤى الرئيسية ─────────────────────────────────────────
@Composable
private fun SummarySection(parsed: ParsedInsight) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AiSectionTitle(title = stringResource(R.string.ai_learning_summary))
        AiInsightCard(
            iconRes     = R.drawable.ic_ai_learning_style,
            title       = stringResource(R.string.ai_learning_style),
            description = parsed.learningStyle
        )
        AiInsightCard(
            iconRes     = R.drawable.ic_ai_strengths,
            title       = stringResource(R.string.ai_strengths),
            description = parsed.strengths
        )
        AiInsightCard(
            iconRes     = R.drawable.ic_ai_development,
            title       = stringResource(R.string.ai_development),
            description = parsed.development
        )
    }
}

// ─── Section 2: نصائح سريعة ───────────────────────────────────────────────────
@Composable
private fun QuickTipsSection(parsed: ParsedInsight) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier          = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter            = painterResource(R.drawable.ic_ai_sparkle),
                contentDescription = null,
                tint               = Color.Unspecified,
                modifier           = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text  = stringResource(R.string.ai_quick_tips),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color      = NavyDark
                )
            )
        }
        AiInsightCard(
            iconRes     = R.drawable.ic_ai_tip_sessions,
            title       = parsed.tip1Title,
            description = parsed.tip1Body
        )
        AiInsightCard(
            iconRes     = R.drawable.ic_ai_tip_preferences,
            title       = parsed.tip2Title,
            description = parsed.tip2Body
        )
    }
}

// ─── Section 3: إرشادات تفصيليه ───────────────────────────────────────────────
@Composable
private fun DetailedGuidanceSection(parsed: ParsedInsight) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier          = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter            = painterResource(R.drawable.ic_ai_bulb),
                contentDescription = null,
                tint               = Color.Unspecified,
                modifier           = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text  = stringResource(R.string.ai_detailed_guidance),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color      = NavyDark
                )
            )
        }

        Card(
            modifier  = Modifier.fillMaxWidth(),
            shape     = RoundedCornerShape(16.dp),
            colors    = CardDefaults.cardColors(containerColor = White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier            = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text  = parsed.guidanceIntro,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NavyDark
                )
                GuidanceSubCard(
                    iconRes  = R.drawable.ic_check,
                    iconTint = BadgeGreen,
                    title    = stringResource(R.string.ai_recommended),
                    bgColor  = BadgeGreen.copy(alpha = 0.10f),
                    bullets  = parsed.recommended
                )
                GuidanceSubCard(
                    iconRes  = R.drawable.ic_close,
                    iconTint = DangerRed,
                    title    = stringResource(R.string.ai_avoid),
                    bgColor  = DangerRedLight,
                    bullets  = parsed.avoid
                )
            }
        }
    }
}

// ─── Reusable: AiInsightCard ──────────────────────────────────────────────────
@Composable
private fun AiInsightCard(
    iconRes: Int,
    title: String,
    description: String
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                painter            = painterResource(iconRes),
                contentDescription = null,
                tint               = Color.Unspecified,
                modifier           = Modifier.size(48.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color      = NavyDark
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text  = description,
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
            }
        }
    }
}

// ─── Reusable: GuidanceSubCard ────────────────────────────────────────────────
@Composable
private fun GuidanceSubCard(
    iconRes: Int,
    iconTint: Color,
    title: String,
    bgColor: Color,
    bullets: List<String>
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter            = painterResource(iconRes),
                    contentDescription = null,
                    tint               = iconTint,
                    modifier           = Modifier
                        .size(16.dp)
                        .graphicsLayer { scaleX = -1f }
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text     = title,
                    style    = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color      = NavyDark
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
            bullets.forEach { bullet ->
                Row(
                    modifier          = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 5.dp)
                            .size(5.dp)
                            .background(iconTint, RoundedCornerShape(50))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text     = bullet,
                        style    = MaterialTheme.typography.bodySmall.copy(color = NavyDark),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

// ─── Section Title ────────────────────────────────────────────────────────────
@Composable
private fun AiSectionTitle(title: String) {
    Text(
        text  = title,
        style = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.Bold,
            color      = NavyDark
        )
    )
}