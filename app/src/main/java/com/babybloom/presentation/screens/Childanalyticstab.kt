package com.babybloom.presentation.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.babybloom.R
import com.babybloom.domain.model.ChildProfile
import com.babybloom.domain.model.RecentActivity
import com.babybloom.domain.model.WeeklyChartData
import com.babybloom.ui.theme.*

// ─── Analytics Tab ────────────────────────────────────────────────────────────
@Composable
fun ChildAnalyticsTab(
    childProfile: ChildProfile?,
    recentActivities: List<RecentActivity>,
    weeklyChartData: WeeklyChartData
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ProgressOverTimeCard(weeklyChartData = weeklyChartData)
        SkillBreakdownCard(childProfile = childProfile)
        RecentActivitiesCard(recentActivities = recentActivities)
        LearningPerformanceCard(childProfile = childProfile)
        Spacer(modifier = Modifier.height(8.dp))
    }
}

// ─── Progress Over Time ───────────────────────────────────────────────────────
@Composable
private fun ProgressOverTimeCard(weeklyChartData: WeeklyChartData) {
    AnalyticsCard(title = stringResource(R.string.analytics_progress_over_time)) {
        RealLineChart(chartData = weeklyChartData)
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ChartLegendItem(color = ChartColorLanguage,    label = stringResource(R.string.analytics_skill_language))
            ChartLegendItem(color = ChartColorNumeracy,    label = stringResource(R.string.analytics_skill_numeracy))
            ChartLegendItem(color = ChartColorMotor,       label = stringResource(R.string.analytics_skill_motor))
            ChartLegendItem(color = ChartColorInteractive, label = stringResource(R.string.analytics_skill_interactive))
        }
    }
}

@Composable
private fun RealLineChart(chartData: WeeklyChartData) {
    val seriesData = listOf(
        ChartColorLanguage    to chartData.languageScores,
        ChartColorNumeracy    to chartData.numeracyScores,
        ChartColorMotor       to chartData.motorScores,
        ChartColorInteractive to chartData.attentionScores
    )
    val yLabels = listOf("100", "75", "50", "25", "0")

    val hasData = seriesData.any { (_, values) -> values.any { it > 0f } }

    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier            = Modifier.height(160.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                yLabels.forEach { label ->
                    Text(
                        text  = label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(160.dp)
                    .background(GradientPurpleLight.copy(alpha = 0.30f), RoundedCornerShape(8.dp))
            ) {
                val minVal   = 0f
                val maxVal   = 100f
                val stepX    = size.width / 5f
                val scaleY   = size.height / (maxVal - minVal)
                val gridVals = listOf(0f, 25f, 50f, 75f, 100f)

                gridVals.forEach { v ->
                    val y = size.height - (v - minVal) * scaleY
                    drawLine(
                        color       = BorderGray.copy(alpha = 0.55f),
                        start       = Offset(0f, y),
                        end         = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                if (!hasData) return@Canvas

                seriesData.forEach { (lineColor, values) ->
                    if (values.all { it == 0f }) return@forEach

                    val path = Path()
                    values.forEachIndexed { i, v ->
                        val x = i * stepX
                        val y = size.height - (v - minVal) * scaleY
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path  = path,
                        color = lineColor,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )
                    values.forEachIndexed { i, v ->
                        if (v == 0f) return@forEachIndexed
                        val x = i * stepX
                        val y = size.height - (v - minVal) * scaleY
                        drawCircle(color = lineColor,   radius = 4.dp.toPx(), center = Offset(x, y))
                        drawCircle(color = Color.White, radius = 2.dp.toPx(), center = Offset(x, y))
                    }
                }
            }
        }

        Row(modifier = Modifier.padding(start = 28.dp)) {
            for (i in 1..6) {
                Text(
                    text      = stringResource(R.string.analytics_period_label, i),
                    style     = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color     = TextSecondary,
                    modifier  = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ChartLegendItem(color: Color, label: String) {
    Spacer(modifier = Modifier.width(3.dp))
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Spacer(modifier = Modifier.width(0.dp))
        Box(modifier = Modifier.size(8.dp).background(color, RoundedCornerShape(2.dp)))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

// ─── Skill Breakdown ──────────────────────────────────────────────────────────
@Composable
private fun SkillBreakdownCard(childProfile: ChildProfile?) {
    AnalyticsCard(title = stringResource(R.string.analytics_skill_breakdown)) {
        SkillProgressRow(
            iconRes       = R.drawable.ic_skill_language,
            label         = stringResource(R.string.analytics_skill_language),
            progress      = childProfile?.languageLevel?.div(3f) ?: 0f,
            progressColor = ChartColorLanguage
        )
        Spacer(modifier = Modifier.height(12.dp))
        SkillProgressRow(
            iconRes       = R.drawable.ic_skill_numeracy,
            label         = stringResource(R.string.analytics_skill_numeracy),
            progress      = childProfile?.numeracyLevel?.div(3f) ?: 0f,
            progressColor = ChartColorNumeracy
        )
        Spacer(modifier = Modifier.height(12.dp))
        SkillProgressRow(
            iconRes       = R.drawable.ic_skill_interactive,
            label         = stringResource(R.string.analytics_skill_interactive),
            progress      = childProfile?.gameScore ?: 0f,
            progressColor = ChartColorInteractive
        )
        Spacer(modifier = Modifier.height(12.dp))
        SkillProgressRow(
            iconRes       = R.drawable.ic_skill_motor,
            label         = stringResource(R.string.analytics_skill_motor),
            progress      = childProfile?.motorLevel?.div(3f) ?: 0f,
            progressColor = ChartColorMotor
        )
    }
}

@Composable
private fun SkillProgressRow(
    iconRes: Int,
    label: String,
    progress: Float,
    progressColor: Color
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier          = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter            = painterResource(iconRes),
                contentDescription = null,
                tint               = Color.Unspecified,
                modifier           = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text  = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = NavyDark
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text  = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = NavyDark
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress   = { progress },
            modifier   = Modifier.fillMaxWidth().height(8.dp),
            color      = progressColor,
            trackColor = CardPurple,
            strokeCap  = StrokeCap.Round
        )
    }
}

// ─── Recent Activities ────────────────────────────────────────────────────────
@Composable
private fun RecentActivitiesCard(recentActivities: List<RecentActivity>) {
    AnalyticsCard(title = stringResource(R.string.analytics_recent_activities)) {
        if (recentActivities.isEmpty()) {
            Text(
                text      = stringResource(R.string.analytics_no_activities),
                style     = MaterialTheme.typography.bodyMedium,
                color     = TextSecondary,
                modifier  = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                recentActivities.forEach { activity ->
                    RecentActivityRow(
                        name    = activity.name,
                        timeAgo = activity.timeAgoLabel,
                        score   = activity.score
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentActivityRow(name: String, timeAgo: String, score: Float) {
    val badgeColor = when {
        score >= 0.85f -> BadgeGreen
        score >= 0.70f -> ChartColorNumeracy
        else           -> ChartColorMotor
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(GradientPurpleLight.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text      = name,
                    style     = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = NavyDark),
                    textAlign = TextAlign.Start,
                    modifier  = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .background(badgeColor.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text  = "${(score * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = badgeColor)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(painter = painterResource(R.drawable.ic_clock), contentDescription = null, tint = TextSecondary, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = timeAgo, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress   = { score },
                    modifier   = Modifier.weight(1f).height(6.dp),
                    color      = badgeColor,
                    trackColor = CardPurple,
                    strokeCap  = StrokeCap.Round
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier         = Modifier.size(22.dp).background(badgeColor.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter            = painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        tint               = badgeColor,
                        modifier           = Modifier.size(13.dp).graphicsLayer { scaleX = -1f }
                    )
                }
            }
        }
    }
}

// ─── Learning Performance ─────────────────────────────────────────────────────
@Composable
private fun LearningPerformanceCard(childProfile: ChildProfile?) {
    AnalyticsCard(title = stringResource(R.string.analytics_learning_performance)) {
        ModalityCard(
            label          = stringResource(R.string.analytics_visual),
            progress       = childProfile?.visualScore ?: 0f,
            progressColor  = ProgressPurple,
            cardBackground = CardPurple,
            iconRes        = R.drawable.ic_activity_visual
        )
        Spacer(modifier = Modifier.height(10.dp))
        ModalityCard(
            label          = stringResource(R.string.analytics_interactive),
            progress       = childProfile?.gameScore ?: 0f,
            progressColor  = ChartColorInteractive,
            cardBackground = ChartColorInteractive.copy(alpha = 0.18f),
            iconRes        = R.drawable.ic_activity_audio
        )
        Spacer(modifier = Modifier.height(10.dp))
        ModalityCard(
            label          = stringResource(R.string.analytics_audio),
            progress       = childProfile?.audioScore ?: 0f,
            progressColor  = ChartColorNumeracy,
            cardBackground = ChartColorNumeracy.copy(alpha = 0.18f),
            iconRes        = R.drawable.ic_activity_numeracy
        )
    }
}

@Composable
private fun ModalityCard(label: String, progress: Float, progressColor: Color, cardBackground: Color, iconRes: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(cardBackground, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier         = Modifier.size(40.dp).background(progressColor.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(painter = painterResource(iconRes), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(24.dp))
            }
            Text(text = label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = NavyDark))
            LinearProgressIndicator(
                progress   = { progress },
                modifier   = Modifier.weight(1f).height(8.dp),
                color      = progressColor,
                trackColor = progressColor.copy(alpha = 0.25f),
                strokeCap  = StrokeCap.Round
            )
            Text(
                text     = "${(progress * 100).toInt()}%",
                style    = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = NavyDark),
                modifier = Modifier.width(40.dp)
            )
        }
    }
}

// ─── Reusable Card Shell ──────────────────────────────────────────────────────
@Composable
private fun AnalyticsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp), color = NavyDark)
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}