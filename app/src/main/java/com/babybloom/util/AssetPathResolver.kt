package com.babybloom.util

import androidx.annotation.ColorRes
import com.babybloom.R

// ─── Image Asset Sealed Class ────────────────────────────────────────────────

sealed class ImageAsset {

    /** A file-based PNG loaded from assets (Animals only) */
    data class PngAsset(val path: String) : ImageAsset()

    /** A vector drawable from res/drawable/, tinted by mode */
    data class SvgDrawable(
        val drawableName: String,
        @ColorRes val tintColor: Int
    ) : ImageAsset()
}

// ─── Asset Path Resolver ─────────────────────────────────────────────────────

object AssetPathResolver {
    
    fun imageAssetFor(
        contentId: String,
        category: String,
        isCalmMode: Boolean
    ): ImageAsset {

        val resolvedCategory = resolveCategory(category, contentId)

        return if (resolvedCategory == "ANIMAL") {
            val mood = if (isCalmMode) "calm" else "active"
            val name = contentId.replaceFirstChar { it.uppercase() }
            ImageAsset.PngAsset("learning_content/visuals/$mood/$name.png")
        } else {
            val tintColor = if (isCalmMode) R.color.calm_tint else R.color.active_tint
            ImageAsset.SvgDrawable(
                drawableName = contentId,
                tintColor    = tintColor
            )
        }
    }

    fun audioPathFor(contentId: String, category: String): String {
        val folder = categoryToFolder(category)
        val name = contentId.replaceFirstChar { it.uppercase() }
        return "learning_content/audio/$folder/$name.ogg"
    }

    fun backgroundMusicPath(): String =
        "activities/audio/background_music.ogg"

    fun soundEffectPath(effect: SoundEffect): String =
        "activities/audio/${effect.fileName}"

    // ─── Private Helpers ─────────────────────────────────────────────────────

    /**
     * Uses [category] as the primary signal.
     * Falls back to inferring from the [contentId] prefix if category is blank or unknown.
     */
    private fun resolveCategory(category: String, contentId: String): String {
        val normalized = category.uppercase().trim()
        if (normalized.isNotBlank() && normalized != "UNKNOWN") return normalized

        // Fallback: infer from contentId prefix
        return when {
            contentId.startsWith("letter_", ignoreCase = true) -> "LETTER_NAME"
            contentId.startsWith("number_", ignoreCase = true) -> "NUMBER"
            contentId.startsWith("shape_",  ignoreCase = true) -> "SHAPE"
            contentId.startsWith("color_",  ignoreCase = true) -> "COLOR"
            contentId.startsWith("animal_", ignoreCase = true) -> "ANIMAL"
            else -> normalized.ifBlank { "UNKNOWN" }
        }
    }

    private fun categoryToFolder(category: String): String =
        when (category.uppercase()) {
            "ANIMAL"       -> "animals"
            "COLOR"        -> "colors"
            "NUMBER"       -> "numbers"
            "SHAPE"        -> "shapes"
            "LETTER_NAME"  -> "name of letters"
            "LETTER_SOUND" -> "sound of letters"
            else           -> category.lowercase()
        }
}

// ─── Sound Effect Enum ───────────────────────────────────────────────────────

enum class SoundEffect(val fileName: String) {
    CORRECT("correct.ogg"),
    WRONG("wrong.ogg"),
    COMPLETE("complete.ogg"),
    TAP("tap.ogg")
}
