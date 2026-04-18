package com.babybloom.util.touch

import androidx.compose.ui.geometry.Offset
import kotlin.math.pow
import kotlin.math.sqrt

data class TouchAnalysis(
    val averageStrokeLength: Float,
    val correctionCount: Int,       // direction reversals
    val timeToFirstTouchMs: Long,
    val touchComplexity: Float      // 0.0–1.0 composite
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

    fun onPointerEvent(change: Offset) {
        if (firstTouchTime == -1L) {
            firstTouchTime = System.currentTimeMillis()
        }
        if (points.isNotEmpty()) {
            val dir = change - points.last()
            lastDirection?.let { prev ->
                // Direction reversal = correction
                val dot = prev.x * dir.x + prev.y * dir.y
                if (dot < 0) correctionCount++
            }
            lastDirection = dir
        }
        points.add(change)
    }

    fun analyze(): TouchAnalysis {
        val totalLength = points.zipWithNext { a, b ->
            sqrt((b.x - a.x).pow(2) + (b.y - a.y).pow(2))
        }.sum()

        val avgStrokeLength = if (points.size > 1)
            totalLength / (points.size - 1) else 0f

        val timeToFirst = if (firstTouchTime != -1L)
            firstTouchTime - startTime else 0L

        // Normalize components to 0.0–1.0 then combine
        val strokeNorm  = (avgStrokeLength / 200f).coerceIn(0f, 1f)
        val corrNorm    = (correctionCount / 10f).coerceIn(0f, 1f)
        val timeNorm    = (timeToFirst / 3000f).coerceIn(0f, 1f)

        val complexity  = (strokeNorm * 0.4f + corrNorm * 0.4f + timeNorm * 0.2f)
            .coerceIn(0f, 1f)

        return TouchAnalysis(
            averageStrokeLength = avgStrokeLength,
            correctionCount = correctionCount,
            timeToFirstTouchMs = timeToFirst,
            touchComplexity = complexity
        )
    }
}
