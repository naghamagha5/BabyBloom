package com.babybloom.util.touch

import androidx.compose.ui.geometry.Offset
import kotlin.math.pow
import kotlin.math.sqrt

data class TouchAnalysis(
    val motorSkillScore: Float,
    val choiceConfidenceScore: Float,
    val pathEfficiency: Float,
    val averageMovementDistance: Float,
    val correctionCount: Int,
    val timeToFirstTouchMs: Long,
    val pathLength: Float
)

class TouchPatternAnalyzer {
    private val points = mutableListOf<Offset>()
    private var startTime: Long = 0L
    private var firstTouchTime: Long = -1L
    private var correctionCount = 0
    private var lastDirection: Offset? = null

    fun onSessionStart() {
        points.clear()
        startTime = System.currentTimeMillis()
        firstTouchTime = -1L
        correctionCount = 0
        lastDirection = null
    }

    fun onPointerEvent(position: Offset) {
        if (firstTouchTime == -1L) {
            firstTouchTime = System.currentTimeMillis()
        }

        if (points.isNotEmpty()) {
            val direction = position - points.last()
            if (direction.getDistance() > MIN_MOVEMENT_PX) {
                lastDirection?.let { previous ->
                    val dot = previous.x * direction.x + previous.y * direction.y
                    if (dot < 0f) correctionCount++
                }
                lastDirection = direction
            }
        }

        points.add(position)
    }

    fun analyze(
        isCorrect: Boolean,
        attempts: Int,
        releasePoint: Offset? = points.lastOrNull(),
        targetCenter: Offset? = null,
        snapRadiusPx: Float? = null
    ): TouchAnalysis {
        val pathLength = points.zipWithNext { a, b -> distance(a, b) }.sum()
        val straightDistance = if (points.size > 1) distance(points.first(), points.last()) else 0f
        val pathEfficiency = when {
            pathLength <= MIN_MOVEMENT_PX -> 0f
            else -> (straightDistance / pathLength).coerceIn(0f, 1f)
        }

        val averageMovementDistance = if (points.size > 1) {
            pathLength / (points.size - 1)
        } else 0f

        val timeToFirstTouchMs = if (firstTouchTime != -1L) firstTouchTime - startTime else 0L
        val smoothnessScore = (1f - correctionCount / 12f).coerceIn(0f, 1f)
        val responseReadinessScore = (1f - timeToFirstTouchMs / 3_000f).coerceIn(0f, 1f)

        val motorSkillScore = (
            pathEfficiency * 0.45f +
                smoothnessScore * 0.35f +
                responseReadinessScore * 0.20f
            ).coerceIn(0f, 1f)

        val choiceConfidenceScore = choiceConfidenceScore(
            isCorrect = isCorrect,
            attempts = attempts,
            releasePoint = releasePoint,
            targetCenter = targetCenter,
            snapRadiusPx = snapRadiusPx
        )

        return TouchAnalysis(
            motorSkillScore = motorSkillScore,
            choiceConfidenceScore = choiceConfidenceScore,
            pathEfficiency = pathEfficiency,
            averageMovementDistance = averageMovementDistance,
            correctionCount = correctionCount,
            timeToFirstTouchMs = timeToFirstTouchMs,
            pathLength = pathLength
        )
    }

    private fun choiceConfidenceScore(
        isCorrect: Boolean,
        attempts: Int,
        releasePoint: Offset?,
        targetCenter: Offset?,
        snapRadiusPx: Float?
    ): Float {
        val correctnessScore = if (isCorrect) 1f else 0f
        val attemptScore = when (attempts.coerceAtLeast(1)) {
            1 -> 1.0f
            2 -> 0.65f
            3 -> 0.35f
            else -> 0f
        }
        val endpointPrecision = endpointPrecision(releasePoint, targetCenter, snapRadiusPx)

        return (
            correctnessScore * 0.55f +
                (if (isCorrect) attemptScore else 0f) * 0.30f +
                endpointPrecision * 0.15f
            ).coerceIn(0f, 1f)
    }

    private fun endpointPrecision(
        releasePoint: Offset?,
        targetCenter: Offset?,
        snapRadiusPx: Float?
    ): Float {
        if (releasePoint == null || targetCenter == null || snapRadiusPx == null || snapRadiusPx <= 0f) {
            return if (points.isEmpty()) 0f else 0.5f
        }

        val distanceFromTarget = distance(releasePoint, targetCenter)
        return (1f - distanceFromTarget / snapRadiusPx).coerceIn(0f, 1f)
    }

    private fun distance(a: Offset, b: Offset): Float =
        sqrt((b.x - a.x).pow(2) + (b.y - a.y).pow(2))

    private companion object {
        const val MIN_MOVEMENT_PX = 2f
    }
}
