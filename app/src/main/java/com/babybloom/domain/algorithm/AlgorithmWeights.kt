package com.babybloom.domain.algorithm

object AlgorithmWeights {

    // ── EMA smoothing ──────────────────────────────────────────────────────
    // 0.1 = very slow / stable,  0.5 = very fast / reactive
    const val EMA_ALPHA = 0.25f

    // ── Item score weights (must sum to 1.0) ───────────────────────────────
    const val WEIGHT_CORRECTNESS  = 0.50f
    const val WEIGHT_SPEED        = 0.15f
    const val WEIGHT_ATTENTION    = 0.20f
    const val WEIGHT_ATTEMPTS_INV = 0.15f

    // ── Optional signal blending ───────────────────────────────────────────
    const val SPEECH_CONFIDENCE_WEIGHT  = 0.30f   // blended in when available
    const val TOUCH_QUALITY_BONUS_MAX = 0.10f  // additive bonus for motor control and choice confidence

    // ── Modality contribution per activity type ────────────────────────────
    // Each entry: how much a completed activity of that type updates each modality
    val ACTIVITY_MODALITY_WEIGHTS: Map<String, Map<String, Float>> = mapOf(
        "STORY"  to mapOf("VISUAL" to 0.4f, "AUDIO"       to 0.6f),
        "SPEECH" to mapOf("AUDIO"  to 0.8f, "VISUAL"      to 0.2f),
        "MATCH"  to mapOf("VISUAL" to 0.9f, "INTERACTIVE" to 0.1f),
        "TRACE"  to mapOf("INTERACTIVE" to 0.8f, "VISUAL" to 0.2f),
        "COUNT"  to mapOf("INTERACTIVE" to 0.1f, "VISUAL" to 0.9f),
        "DRAG"   to mapOf("INTERACTIVE" to 0.8f, "VISUAL" to 0.2f),
    )

    // ── Level thresholds ───────────────────────────────────────────────────
    const val LEVEL_UP_THRESHOLD   = 0.80f   // EMA score above this → level up
    const val LEVEL_DOWN_THRESHOLD = 0.45f   // EMA score below this → level down
    const val MIN_ACTIVITIES_FOR_LEVEL_CHANGE = 3   // safety gate

    // ── Progress bar ───────────────────────────────────────────────────────
    // Number of mastered activities (score ≥ LEVEL_UP_THRESHOLD) to fill bar to 100%
    const val ACTIVITIES_TO_MASTER_LEVEL = 5

    // ── Repeat / weakness decisions ────────────────────────────────────────
    const val REPEAT_THRESHOLD     = 0.60f   // item score below this → repeat activity
    const val CONTENT_PASS_THRESHOLD = 0.60f // latest test score required to keep content out of learning queue
    const val WEAK_SKILL_THRESHOLD = 0.55f   // progress below this → mark area as weak

    // ── Session planning ───────────────────────────────────────────────────
    const val WEAK_SKILL_ACTIVITY_RATIO = 0.50f  // 50% of session targets weak skills
    const val SESSION_ACTIVITY_COUNT    = 4       // activities per regular session
    const val REVISION_CONTENT_COUNT    = 3       // passed content IDs revised after the learning tests

    // ── Assessment ─────────────────────────────────────────────────────────
    const val ASSESSMENT_ACTIVITIES_PER_SKILL = 2
    const val ASSESSMENT_START_DIFFICULTY     = 2   // 1=easy … 5=hard
}
