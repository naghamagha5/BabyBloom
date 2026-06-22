package com.babybloom.util.touch

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

data class TouchAnalysis(
    val touchQualityScore: Float,
    val pathEfficiency: Float,
    val averageMovementDistance: Float,
    val correctionCount: Int,
    val timeToFirstTouchMs: Long,
    val pathLength: Float,
    val strokeCount: Int = 0
)

enum class TouchScoringMode {
    DIRECT_PATH,
    PRECISION_RELEASE,
    COVERAGE_STROKES,
    TRACE
}

class TouchPatternAnalyzer {
    private val strokes = mutableListOf<MutableList<Offset>>()
    private var activeStroke: MutableList<Offset>? = null
    private var startTime: Long = 0L
    private var firstTouchTime: Long = -1L
    private var correctionCount = 0
    private var lastDirection: Offset? = null

    fun onSessionStart() {
        strokes.clear()
        activeStroke = null
        startTime = System.currentTimeMillis()
        firstTouchTime = -1L
        correctionCount = 0
        lastDirection = null
    }

    fun onStrokeStart(position: Offset? = null) {
        val stroke = mutableListOf<Offset>()
        strokes.add(stroke)
        activeStroke = stroke
        lastDirection = null
        position?.let { onPointerEvent(it) }
    }

    fun onPointerEvent(position: Offset) {
        if (firstTouchTime == -1L) {
            firstTouchTime = System.currentTimeMillis()
        }

        val stroke = activeStroke ?: mutableListOf<Offset>().also {
            strokes.add(it)
            activeStroke = it
        }

        if (stroke.isNotEmpty()) {
            val direction = position - stroke.last()
            if (direction.getDistance() > MIN_MOVEMENT_PX) {
                lastDirection?.let { previous ->
                    val dot = previous.x * direction.x + previous.y * direction.y
                    if (dot < 0f) correctionCount++
                }
                lastDirection = direction
            }
        }

        stroke.add(position)
    }

    fun onStrokeEnd() {
        activeStroke = null
        lastDirection = null
    }

    fun analyze(
        attempts: Int,
        releasePoint: Offset? = allPoints().lastOrNull(),
        targetCenter: Offset? = null,
        snapRadiusPx: Float? = null,
        expectedStrokeCount: Int? = null,
        progress: Float? = null,
        pathAdherence: Float? = null,
        mode: TouchScoringMode = TouchScoringMode.DIRECT_PATH
    ): TouchAnalysis {
        val points = allPoints()
        val pathLength = strokes.sumOf { stroke ->
            stroke.zipWithNext { a, b -> distance(a, b).toDouble() }.sum()
        }.toFloat()
        val segmentCount = strokes.sumOf { stroke -> (stroke.size - 1).coerceAtLeast(0) }
        val pathEfficiency = pathEfficiencyScore()
        val averageMovementDistance = if (segmentCount > 0) pathLength / segmentCount else 0f

        val timeToFirstTouchMs = if (firstTouchTime != -1L) firstTouchTime - startTime else 0L
        val smoothnessScore = (1f - correctionCount / 12f).coerceIn(0f, 1f)
        val responseReadinessScore = (1f - timeToFirstTouchMs / 3_000f).coerceIn(0f, 1f)
        val attemptScore = attemptScore(attempts)
        val endpointPrecision = endpointPrecision(releasePoint, targetCenter, snapRadiusPx)
        val progressScore = progress?.coerceIn(0f, 1f) ?: if (points.isEmpty()) 0f else 0.5f
        val adherenceScore = pathAdherence?.coerceIn(0f, 1f) ?: 1f
        val strokeCompletionScore = strokeCompletionScore(expectedStrokeCount)
        val strokeControlScore = strokeControlScore(pathLength)

        val touchQualityScore = when (mode) {
            TouchScoringMode.DIRECT_PATH -> (
                attemptScore * 0.30f +
                    endpointPrecision * 0.35f +
                    pathEfficiency * 0.10f +
                    smoothnessScore * 0.15f +
                    responseReadinessScore * 0.10f
                )

            TouchScoringMode.PRECISION_RELEASE -> (
                endpointPrecision * 0.45f +
                    attemptScore * 0.25f +
                    smoothnessScore * 0.20f +
                    strokeCompletionScore * 0.05f +
                    responseReadinessScore * 0.05f
                )

            TouchScoringMode.COVERAGE_STROKES -> (
                progressScore * 0.30f +
                    adherenceScore * 0.30f +
                    smoothnessScore * 0.20f +
                    strokeControlScore * 0.15f +
                    attemptScore * 0.05f +
                    responseReadinessScore * 0.05f
                )

            TouchScoringMode.TRACE -> (
                progressScore * 0.35f +
                    adherenceScore * 0.25f +
                    smoothnessScore * 0.25f +
                    strokeCompletionScore * 0.10f +
                    responseReadinessScore * 0.05f
                )
        }.coerceIn(0f, 1f)

        return TouchAnalysis(
            touchQualityScore = touchQualityScore,
            pathEfficiency = pathEfficiency,
            averageMovementDistance = averageMovementDistance,
            correctionCount = correctionCount,
            timeToFirstTouchMs = timeToFirstTouchMs,
            pathLength = pathLength,
            strokeCount = strokes.count { it.isNotEmpty() }
        )
    }

    private fun attemptScore(attempts: Int): Float =
        when (attempts.coerceAtLeast(1)) {
            1 -> 1.0f
            2 -> 0.65f
            3 -> 0.35f
            else -> 0f
        }

    private fun strokeCompletionScore(expectedStrokeCount: Int?): Float {
        val expected = expectedStrokeCount?.coerceAtLeast(1) ?: return 1f
        val actual = strokes.count { it.isNotEmpty() }
        if (actual == 0) return 0f
        return (1f - abs(actual - expected).toFloat() / expected.toFloat()).coerceIn(0f, 1f)
    }

    private fun strokeControlScore(pathLength: Float): Float {
        val strokeCount = strokes.count { it.isNotEmpty() }
        if (strokeCount == 0) return 0f
        val averageStrokeLength = pathLength / strokeCount.toFloat()
        return (averageStrokeLength / MEANINGFUL_STROKE_PX).coerceIn(0f, 1f)
    }

    private fun pathEfficiencyScore(): Float {
        val efficiencies = strokes.mapNotNull { stroke ->
            val pathLength = stroke.zipWithNext { a, b -> distance(a, b) }.sum()
            if (pathLength <= MIN_MOVEMENT_PX || stroke.size < 2) null
            else (distance(stroke.first(), stroke.last()) / pathLength).coerceIn(0f, 1f)
        }
        return if (efficiencies.isEmpty()) 0f else efficiencies.average().toFloat()
    }

    private fun endpointPrecision(
        releasePoint: Offset?,
        targetCenter: Offset?,
        snapRadiusPx: Float?
    ): Float {
        if (releasePoint == null || targetCenter == null || snapRadiusPx == null || snapRadiusPx <= 0f) {
            return if (allPoints().isEmpty()) 0f else 0.5f
        }

        val distanceFromTarget = distance(releasePoint, targetCenter)
        val innerRadius = snapRadiusPx * INNER_TARGET_FRACTION
        if (distanceFromTarget <= innerRadius) return 1f
        return (1f - (distanceFromTarget - innerRadius) / (snapRadiusPx - innerRadius)).coerceIn(0f, 1f)
    }

    private fun allPoints(): List<Offset> = strokes.flatten()

    private fun distance(a: Offset, b: Offset): Float =
        sqrt((b.x - a.x).pow(2) + (b.y - a.y).pow(2))

    private companion object {
        const val MIN_MOVEMENT_PX = 2f
        const val INNER_TARGET_FRACTION = 0.45f
        const val MEANINGFUL_STROKE_PX = 120f
    }
}
