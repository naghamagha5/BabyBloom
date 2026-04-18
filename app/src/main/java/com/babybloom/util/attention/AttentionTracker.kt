package com.babybloom.util.attention

class AttentionTracker {
    private val samples = mutableListOf<Boolean>()

    fun record(sample: AttentionSample?) {
        sample?.let { samples.add(it.isAttentive) }
    }

    // Returns 0.0–1.0. Call when activity ends.
    fun computeScore(): Float {
        if (samples.isEmpty()) return 0f
        return samples.count { it }.toFloat() / samples.size
    }

    fun reset() = samples.clear()
}