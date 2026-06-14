package com.babybloom.domain.insight

import java.util.Calendar
import java.util.TimeZone

object InsightGenerationPolicy {
    fun canGenerate(
        lastGeneratedAt: Long?,
        now: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Boolean {
        if (lastGeneratedAt == null) return true
        val startOfToday = Calendar.getInstance(timeZone).apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return lastGeneratedAt < startOfToday
    }

    fun millisUntilNextLocalMidnight(
        now: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long {
        val nextMidnight = Calendar.getInstance(timeZone).apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return (nextMidnight - now).coerceAtLeast(1L)
    }
}
