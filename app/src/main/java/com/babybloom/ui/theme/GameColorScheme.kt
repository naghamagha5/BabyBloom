package com.babybloom.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
// GameColorScheme.kt
// Place this file at:  com/babybloom/ui/theme/GameColorScheme.kt
// ─────────────────────────────────────────────────────────────────────────────

/**
 * All the colors a game screen needs at runtime.
 *
 * @param accent      The randomly-chosen swatch for the current round
 *                    (card borders, icon tints, highlights).
 * @param background  The card / screen background (off-white for calm,
 *                    pure white for active).
 * @param correct     Color shown on a correct answer (green family).
 * @param wrong       Color shown on a wrong answer  (red family).
 * @param isCalmMode  Convenience flag mirrored from session settings.
 */
@Immutable
data class GameColorScheme(
    val accent     : Color,
    val background : Color,
    val correct    : Color,
    val wrong      : Color,
    val isCalmMode : Boolean
)

// ── Swatch lists ──────────────────────────────────────────────────────────────

private val calmSwatches = listOf(
    GameCalmSwatch1,
    GameCalmSwatch2,
    GameCalmSwatch3,
    GameCalmSwatch4,
    GameCalmSwatch5
)

private val activeSwatches = listOf(
    GameActiveSwatch1,
    GameActiveSwatch2,
    GameActiveSwatch3,
    GameActiveSwatch4,
    GameActiveSwatch5
)

// ── Factory ───────────────────────────────────────────────────────────────────

/**
 * Build a [GameColorScheme] for the current round.
 *
 * @param isCalmMode  Whether calm mode is active.
 * @param seed        Any stable integer that changes each round
 *                    (e.g. [currentIndex]) so the accent rotates
 *                    deterministically without extra state.
 */
fun gameColorSchemeFor(isCalmMode: Boolean, seed: Int): GameColorScheme {
    val swatches = if (isCalmMode) calmSwatches else activeSwatches
    val accent   = swatches[seed % swatches.size]          // deterministic rotation

    return if (isCalmMode) {
        GameColorScheme(
            accent     = accent,
            background = GameCalmBackground,
            correct    = GameCalmCorrect,
            wrong      = GameCalmWrong,
            isCalmMode = true
        )
    } else {
        GameColorScheme(
            accent     = accent,
            background = GameActiveBackground,
            correct    = GameActiveCorrect,
            wrong      = GameActiveWrong,
            isCalmMode = false
        )
    }
}

// ── CompositionLocal so any game screen can read it without prop-drilling ─────

val LocalGameColorScheme = staticCompositionLocalOf<GameColorScheme> {
    error("No GameColorScheme provided. Wrap your content in GameColorSchemeProvider.")
}