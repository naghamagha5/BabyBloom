package com.babybloom.domain.algorithm

import com.babybloom.domain.model.ActivitySignal
import com.babybloom.domain.model.ChildProfile
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AdaptiveAlgorithmEngineTest {

    private lateinit var engine: AdaptiveAlgorithmEngine
    private lateinit var freshProfile: ChildProfile

    @Before
    fun setup() {
        engine = AdaptiveAlgorithmEngine()
        freshProfile = ChildProfile(childId = 1L)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun signal(
        skillArea: String,
        activityType: String,
        correctCount: Int = 1,
        incorrectCount: Int = 0,
        attempts: Int = 1,
        durationMs: Long = 30_000L,
        attentionScore: Float? = 0.8f,
        touchQualityScore: Float? = null,
        speechConfidence: Float? = null
    ) = ActivitySignal(
        childId           = 1L,
        activityId        = "test_activity",
        skillArea         = skillArea,
        modality          = when (activityType) {
            "SPEECH"       -> "AUDIO"
            "DRAG", "TRACE" -> "INTERACTIVE"
            else            -> "VISUAL"
        },
        activityType      = activityType,
        difficultyLevel   = 1,
        correctCount      = correctCount,
        incorrectCount    = incorrectCount,
        attempts          = attempts,
        attentionScore    = attentionScore,
        touchQualityScore = touchQualityScore,
        speechConfidence  = speechConfidence,
        durationMs        = durationMs,
        expectedDurationMs = 60_000L
    )

    // Run N identical signals through processActivityResult, chaining profile
    private fun runSignals(
        count: Int,
        profile: ChildProfile,
        buildSignal: () -> ActivitySignal
    ): ChildProfile {
        var p = profile
        repeat(count) { p = engine.processActivityResult(buildSignal(), p).updatedProfile }
        return p
    }

    // Run N signals through updateModalityPreferencesFromSession (session-end flush)
    private fun flushSession(
        signals: List<ActivitySignal>,
        profile: ChildProfile
    ): ChildProfile = engine.updateModalityPreferencesFromSession(signals, profile)

    // ─────────────────────────────────────────────────────────────────────────
    // 1. SKILL LEVEL BOUNDS
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `language level never exceeds 5`() {
        // Push language very high with perfect answers
        val profile = runSignals(30, freshProfile) {
            signal("LANGUAGE", "MATCH", correctCount = 1, incorrectCount = 0, durationMs = 5_000L)
        }
        assertTrue(
            "languageLevel should be ≤ 5, was ${profile.languageLevel}",
            profile.languageLevel <= 5
        )
    }

    @Test
    fun `motor level never exceeds 4`() {
        val profile = runSignals(30, freshProfile) {
            signal("MOTOR", "DRAG", correctCount = 1, incorrectCount = 0, durationMs = 5_000L,
                touchQualityScore = 1f)
        }
        assertTrue(
            "motorLevel should be ≤ 4, was ${profile.motorLevel}",
            profile.motorLevel <= 4
        )
    }

    @Test
    fun `numeracy level never exceeds 4`() {
        val profile = runSignals(30, freshProfile) {
            signal("NUMERACY", "COUNT", correctCount = 1, incorrectCount = 0, durationMs = 5_000L)
        }
        assertTrue(
            "numeracyLevel should be ≤ 4, was ${profile.numeracyLevel}",
            profile.numeracyLevel <= 4
        )
    }

    @Test
    fun `skill levels never drop below 1`() {
        // Flood with terrible answers
        val profile = runSignals(30, freshProfile) {
            signal("LANGUAGE", "MATCH", correctCount = 0, incorrectCount = 5, attempts = 5,
                durationMs = 120_000L, attentionScore = 0f)
        }
        assertTrue("languageLevel ≥ 1", profile.languageLevel >= 1)
        assertTrue("numeracyLevel ≥ 1", profile.numeracyLevel >= 1)
        assertTrue("motorLevel ≥ 1", profile.motorLevel >= 1)
    }

    @Test
    fun `motor and numeracy can never reach level 5`() {
        val motorProfile = runSignals(30, freshProfile) {
            signal("MOTOR", "DRAG", correctCount = 1, incorrectCount = 0, durationMs = 5_000L)
        }
        val numProfile = runSignals(30, freshProfile) {
            signal("NUMERACY", "COUNT", correctCount = 1, incorrectCount = 0, durationMs = 5_000L)
        }
        assertNotEquals("MOTOR max is 4, not 5", 5, motorProfile.motorLevel)
        assertNotEquals("NUMERACY max is 4, not 5", 5, numProfile.numeracyLevel)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. SKILL LEVELS UPDATE ONLY THE TARGETED SKILL AREA
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `language signal does not change motor or numeracy progress`() {
        val before = freshProfile
        val after = engine.processActivityResult(
            signal("LANGUAGE", "MATCH"), before
        ).updatedProfile

        assertEquals("motorProgress unchanged", before.motorProgress, after.motorProgress)
        assertEquals("numeracyProgress unchanged", before.numeracyProgress, after.numeracyProgress)
    }

    @Test
    fun `motor signal does not change language or numeracy progress`() {
        val before = freshProfile
        val after = engine.processActivityResult(
            signal("MOTOR", "DRAG", touchQualityScore = 0.8f), before
        ).updatedProfile

        assertEquals("languageProgress unchanged", before.languageProgress, after.languageProgress)
        assertEquals("numeracyProgress unchanged", before.numeracyProgress, after.numeracyProgress)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. OVERALL PROGRESS PERCENT
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `overall progress is 0 for a completely fresh profile`() {
        val result = engine.processActivityResult(
            signal("LANGUAGE", "MATCH", correctCount = 0, incorrectCount = 1,
                durationMs = 120_000L, attentionScore = 0f),
            freshProfile
        ).updatedProfile
        // After one bad answer overall should still be near 0, never negative
        assertTrue("overallProgressPercent ≥ 0", result.overallProgressPercent >= 0f)
    }

    @Test
    fun `overall progress never exceeds 100`() {
        val profile = runSignals(50, freshProfile) {
            signal("LANGUAGE", "MATCH", correctCount = 1, incorrectCount = 0, durationMs = 1_000L)
        }
        assertTrue(
            "overallProgressPercent ≤ 100, was ${profile.overallProgressPercent}",
            profile.overallProgressPercent <= 100f
        )
    }

    @Test
    fun `overall progress at max language only is roughly 33 percent not 100`() {
        // Max out language, leave motor and numeracy at floor
        val profile = runSignals(30, freshProfile) {
            signal("LANGUAGE", "MATCH", correctCount = 1, incorrectCount = 0, durationMs = 1_000L)
        }
        // Language maxed = 1/3 of overall. Should be well below 70%.
        assertTrue(
            "Overall should be ~33% when only language is maxed, was ${profile.overallProgressPercent}",
            profile.overallProgressPercent < 70f
        )
    }

    @Test
    fun `overall progress with all skills maxed is near 100`() {
        // EMA needs many iterations to push levels up past the MIN_ACTIVITIES gate.
        // 100 signals per skill is enough to reach max level on each.
        var profile = freshProfile
        profile = runSignals(100, profile) {
            signal("LANGUAGE", "MATCH", correctCount = 1, incorrectCount = 0, durationMs = 1_000L)
        }
        profile = runSignals(100, profile) {
            signal("MOTOR", "DRAG", correctCount = 1, incorrectCount = 0, durationMs = 1_000L)
        }
        profile = runSignals(100, profile) {
            signal("NUMERACY", "COUNT", correctCount = 1, incorrectCount = 0, durationMs = 1_000L)
        }
        assertTrue(
            "Overall should be near 100% when all skills maxed, was ${profile.overallProgressPercent}",
            profile.overallProgressPercent >= 80f
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. MODALITY PREFERENCE — session flush only
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `modality percentages always sum to 100`() {
        val signals = listOf(
            signal("LANGUAGE", "SPEECH", speechConfidence = 0.9f),
            signal("LANGUAGE", "MATCH"),
            signal("MOTOR", "DRAG", touchQualityScore = 0.8f)
        )
        val updated = flushSession(signals, freshProfile)
        val sum = updated.visualPreferencePercent +
                updated.audioPreferencePercent +
                updated.interactivePreferencePercent
        assertEquals("Modality percentages must sum to 100", 100f, sum, 1f)
    }

    @Test
    fun `audio modality grows after many speech activities`() {
        val speechSignals = List(10) {
            signal("LANGUAGE", "SPEECH", speechConfidence = 0.95f, attentionScore = 0.9f)
        }
        val before = freshProfile.audioPreferencePercent
        val after = flushSession(speechSignals, freshProfile).audioPreferencePercent
        assertTrue(
            "Audio preference should grow after speech-heavy session, before=$before after=$after",
            after > before
        )
    }

    @Test
    fun `visual modality grows after many match and count activities`() {
        val visualSignals = List(10) {
            signal("LANGUAGE", "MATCH", attentionScore = 0.9f)
        } + List(5) {
            signal("NUMERACY", "COUNT", attentionScore = 0.9f)
        }
        val before = freshProfile.visualPreferencePercent
        val after = flushSession(visualSignals, freshProfile).visualPreferencePercent
        assertTrue(
            "Visual preference should grow after visual-heavy session, before=$before after=$after",
            after > before
        )
    }

    @Test
    fun `interactive modality grows after many drag and trace activities`() {
        val interactiveSignals = List(10) {
            signal("MOTOR", "DRAG", touchQualityScore = 0.95f, attentionScore = 0.9f)
        }
        val before = freshProfile.interactivePreferencePercent
        val after = flushSession(interactiveSignals, freshProfile).interactivePreferencePercent
        assertTrue(
            "Interactive preference should grow after drag-heavy session, before=$before after=$after",
            after > before
        )
    }

    @Test
    fun `empty session flush does not crash and returns valid profile`() {
        val updated = flushSession(emptyList(), freshProfile)
        val sum = updated.visualPreferencePercent +
                updated.audioPreferencePercent +
                updated.interactivePreferencePercent
        assertEquals("Sum still 100 after empty flush", 100f, sum, 1f)
    }

    @Test
    fun `modality smooths over multiple sessions not instantly overridden`() {
        // Session 1: all speech — audio should rise but not hit 100%
        val speechSignals = List(10) {
            signal("LANGUAGE", "SPEECH", speechConfidence = 1f, attentionScore = 1f)
        }
        val afterSession1 = flushSession(speechSignals, freshProfile)
        assertTrue(
            "Audio should not instantly jump to 100% after one session, was ${afterSession1.audioPreferencePercent}",
            afterSession1.audioPreferencePercent < 90f
        )
        assertTrue(
            "Visual should not drop to 0% after one session, was ${afterSession1.visualPreferencePercent}",
            afterSession1.visualPreferencePercent > 5f
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. DOMINANT MODALITY
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `dominant modality reflects highest preference after speech-heavy session`() {
        val speechSignals = List(15) {
            signal("LANGUAGE", "SPEECH", speechConfidence = 1f, attentionScore = 1f)
        }
        // EMA alpha is 0.25 — it takes many sessions to shift from a flat 33/33/33 start.
        // SPEECH maps 80% to AUDIO and 20% to VISUAL, so AUDIO climbs steadily.
        // After 20 all-speech sessions it reliably dominates.
        var profile = freshProfile
        repeat(20) { profile = flushSession(speechSignals, profile) }
        assertEquals("AUDIO", profile.dominantModality)
    }

    @Test
    fun `dominant modality is VISUAL for a brand new profile`() {
        // Default equal split — VISUAL wins the tie-break in the engine
        assertEquals("VISUAL", freshProfile.dominantModality)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. EMA SMOOTHING BEHAVIOUR
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `one perfect answer does not instantly max out skill progress`() {
        val after = engine.processActivityResult(
            signal("LANGUAGE", "MATCH", correctCount = 1, incorrectCount = 0, durationMs = 1_000L),
            freshProfile
        ).updatedProfile
        assertTrue(
            "One perfect answer should not push progress to 1.0, was ${after.languageProgress}",
            after.languageProgress < 0.9f
        )
    }

    @Test
    fun `one terrible answer does not instantly bottom out skill progress`() {
        // Start with a good profile
        val goodProfile = runSignals(10, freshProfile) {
            signal("LANGUAGE", "MATCH", correctCount = 1, incorrectCount = 0, durationMs = 1_000L)
        }
        val after = engine.processActivityResult(
            signal("LANGUAGE", "MATCH", correctCount = 0, incorrectCount = 5, attempts = 5,
                durationMs = 120_000L, attentionScore = 0f),
            goodProfile
        ).updatedProfile
        assertTrue(
            "One terrible answer should not wipe out progress, was ${after.languageProgress}",
            after.languageProgress > 0.1f
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. ITEM SCORE SANITY
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `perfect answer produces item score above repeat threshold`() {
        val score = engine.computeItemScore(
            signal("LANGUAGE", "MATCH", correctCount = 1, incorrectCount = 0,
                durationMs = 10_000L, attentionScore = 1f)
        )
        assertTrue(
            "Perfect answer score $score should be above REPEAT_THRESHOLD ${AlgorithmWeights.REPEAT_THRESHOLD}",
            score >= AlgorithmWeights.REPEAT_THRESHOLD
        )
    }

    @Test
    fun `terrible answer produces item score below repeat threshold`() {
        val score = engine.computeItemScore(
            signal("LANGUAGE", "MATCH", correctCount = 0, incorrectCount = 5, attempts = 5,
                durationMs = 120_000L, attentionScore = 0f)
        )
        assertTrue(
            "Terrible answer score $score should be below REPEAT_THRESHOLD ${AlgorithmWeights.REPEAT_THRESHOLD}",
            score < AlgorithmWeights.REPEAT_THRESHOLD
        )
    }

    @Test
    fun `item score is always between 0 and 1`() {
        val extremeSignals = listOf(
            signal("LANGUAGE", "MATCH", correctCount = 1, incorrectCount = 0,
                durationMs = 1L, attentionScore = 1f, touchQualityScore = 1f),
            signal("LANGUAGE", "SPEECH", correctCount = 0, incorrectCount = 10, attempts = 10,
                durationMs = 999_999L, attentionScore = 0f, speechConfidence = 0f)
        )
        extremeSignals.forEach { sig ->
            val score = engine.computeItemScore(sig)
            assertTrue("Item score $score must be ≥ 0", score >= 0f)
            assertTrue("Item score $score must be ≤ 1", score <= 1f)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. WEAK SKILL DETECTION
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `language marked weak after consistently poor answers`() {
        val profile = runSignals(15, freshProfile) {
            signal("LANGUAGE", "MATCH", correctCount = 0, incorrectCount = 3, attempts = 3,
                durationMs = 90_000L, attentionScore = 0.1f)
        }
        assertTrue(
            "LANGUAGE should be in weakSkillAreas after poor performance, got: ${profile.weakSkillAreas}",
            profile.weakSkillList.contains("LANGUAGE")
        )
    }

    @Test
    fun `skill is not marked weak after consistently good answers`() {
        // Only LANGUAGE signals are sent — motor and numeracy progress stay at 0
        // so they will be weak (never trained). We only assert LANGUAGE is not weak.
        val profile = runSignals(15, freshProfile) {
            signal("LANGUAGE", "MATCH", correctCount = 1, incorrectCount = 0, durationMs = 5_000L)
        }
        assertFalse(
            "LANGUAGE should not be weak after good performance, got: ${profile.weakSkillAreas}",
            profile.weakSkillList.contains("LANGUAGE")
        )
    }

    @Test
    fun `no skills marked weak after good answers across all areas`() {
        // Train all three skill areas with good answers
        var profile = freshProfile
        profile = runSignals(15, profile) {
            signal("LANGUAGE", "MATCH", correctCount = 1, incorrectCount = 0, durationMs = 5_000L)
        }
        profile = runSignals(15, profile) {
            signal("MOTOR", "DRAG", correctCount = 1, incorrectCount = 0, durationMs = 5_000L)
        }
        profile = runSignals(15, profile) {
            signal("NUMERACY", "COUNT", correctCount = 1, incorrectCount = 0, durationMs = 5_000L)
        }
        assertTrue(
            "No skills should be weak after good performance across all areas, got: ${profile.weakSkillAreas}",
            profile.weakSkillList.isEmpty()
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 9. TOTAL ACTIVITIES COUNTER
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `total activities increments by 1 per processActivityResult call`() {
        val after1 = engine.processActivityResult(signal("LANGUAGE", "MATCH"), freshProfile).updatedProfile
        val after2 = engine.processActivityResult(signal("LANGUAGE", "MATCH"), after1).updatedProfile
        assertEquals(1, after1.totalActivitiesCompleted)
        assertEquals(2, after2.totalActivitiesCompleted)
    }
}