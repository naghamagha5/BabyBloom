package com.babybloom.util.attention

class AttentionTracker {
    private val samples = mutableListOf<Float>()

    fun record(sample: AttentionSample?) {
        samples.add(sample?.attentionScore?.coerceIn(0f, 1f) ?: NO_FACE_SCORE)
    }

    fun hasSamples(): Boolean = samples.isNotEmpty()

    // Returns 0.0-1.0. Call when activity ends.
    fun computeScore(): Float {
        if (samples.isEmpty()) return 0f
        return samples.average().toFloat().coerceIn(0f, 1f)
    }

    fun reset() = samples.clear()

    private companion object {
        const val NO_FACE_SCORE = 0.10f
    }
}
