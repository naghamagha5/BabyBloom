package com.babybloom.domain.insight

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class InsightGenerationPolicyTest {
    private val cairo = TimeZone.getTimeZone("Africa/Cairo")

    @Test
    fun blocksAnotherGenerationOnTheSameLocalDay() {
        val now = time(2026, Calendar.JUNE, 14, 18, 0)
        val earlierToday = time(2026, Calendar.JUNE, 14, 1, 0)

        assertFalse(InsightGenerationPolicy.canGenerate(earlierToday, now, cairo))
    }

    @Test
    fun allowsGenerationImmediatelyAfterLocalMidnight() {
        val now = time(2026, Calendar.JUNE, 15, 0, 0)
        val previousDay = time(2026, Calendar.JUNE, 14, 23, 59)

        assertTrue(InsightGenerationPolicy.canGenerate(previousDay, now, cairo))
    }

    @Test
    fun allowsFirstGeneration() {
        assertTrue(InsightGenerationPolicy.canGenerate(null, time(2026, Calendar.JUNE, 14, 12, 0), cairo))
    }

    @Test
    fun calculatesDelayUntilNextLocalMidnight() {
        val now = time(2026, Calendar.JUNE, 14, 23, 59)

        assertTrue(InsightGenerationPolicy.millisUntilNextLocalMidnight(now, cairo) == 60_000L)
    }

    private fun time(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(cairo).apply {
            clear()
            set(year, month, day, hour, minute)
        }.timeInMillis
}
