package com.babybloom.util

import androidx.annotation.ColorRes
import com.babybloom.R

// ─── Image Asset Sealed Class ────────────────────────────────────────────────

sealed class ImageAsset {
    data class PngAsset(val path: String) : ImageAsset()
    data class SvgDrawable(val drawableName: String, @ColorRes val tintColor: Int) : ImageAsset()
}

// ─── Asset Path Resolver ─────────────────────────────────────────────────────

object AssetPathResolver {

    fun imageAssetFor(contentId: String, category: String, isCalmMode: Boolean): ImageAsset {
        val resolvedCategory = resolveCategory(category, contentId)
        return if (resolvedCategory == "ANIMAL") {
            ImageAsset.PngAsset(animalImagePathFor(contentId, isCalmMode))
        } else {
            val tintColor = if (isCalmMode) R.color.calm_tint else R.color.active_tint
            ImageAsset.SvgDrawable(
                drawableName = if (resolvedCategory == "LETTER_SOUND" && contentId.endsWith("_s"))
                    contentId.dropLast(2) else contentId,
                tintColor = tintColor
            )
        }
    }

    fun audioPathFor(contentId: String, category: String): String =
        "learning_content/audio/${categoryToFolder(category)}/$contentId.ogg"

    fun animalImagePathFor(contentId: String, isCalmMode: Boolean): String {
        val mood = if (isCalmMode) "calm" else "active"
        return "learning_content/visual/$mood/$contentId.png"
    }

    fun androidAssetUri(assetPath: String): String = "file:///android_asset/$assetPath"

    fun backgroundMusicPath(): String = "activities/audio/background_music.ogg"

    fun soundEffectPath(effect: SoundEffect): String = "activities/audio/${effect.fileName}"

    // ── Drag game instruction audio ───────────────────────────────────────────
    fun dragInstructionColorPath(): String =
        "activities/audio/drag/drag_instruction_color.ogg"

    fun dragInstructionLetterPath(): String =
        "activities/audio/drag/drag_instruction_letter.ogg"

    fun dragInstructionShapePath(): String =
        "activities/audio/drag/drag_instruction_shape.ogg"

    /**
     * Maps the target count to its corresponding instruction audio file.
     * Naming convention: drag_instruction_number_N.ogg (N = targetCount).
     * Adding new audio files for higher numbers requires zero code changes.
     */
    fun dragInstructionNumberPath(numberContentId: String): String {
        val numberToken = numberContentId.substringAfter("number_", numberContentId)
        return "activities/audio/drag/drag_instruction_number_$numberToken.ogg"
    }

    // ─── Private Helpers ─────────────────────────────────────────────────────

    private fun resolveCategory(category: String, contentId: String): String {
        val normalized = category.uppercase().trim()
        if (normalized.isNotBlank() && normalized != "UNKNOWN") return normalized
        return when {
            contentId.startsWith("letter_", ignoreCase = true) -> "LETTER_NAME"
            contentId.startsWith("number_", ignoreCase = true) -> "NUMBER"
            contentId.startsWith("shape_",  ignoreCase = true) -> "SHAPE"
            contentId.startsWith("color_",  ignoreCase = true) -> "COLOR"
            contentId.startsWith("animal_", ignoreCase = true) -> "ANIMAL"
            else -> normalized.ifBlank { "UNKNOWN" }
        }
    }

    private fun categoryToFolder(category: String): String = when (category.uppercase()) {
        "ANIMAL"       -> "animals"
        "COLOR"        -> "colors"
        "NUMBER"       -> "numbers"
        "SHAPE"        -> "shapes"
        "LETTER_NAME"  -> "name of letters"
        "LETTER_SOUND" -> "sound of letters"
        else           -> category.lowercase()
    }

    fun countQuestionAudioPath(subjectId: String): String {
        val token = subjectId.removePrefix("animal_").removePrefix("shape_")
        return "activities/audio/count/count_$token.ogg"
    }
}

// ─── Sound Effect Enum ───────────────────────────────────────────────────────

enum class SoundEffect(val fileName: String) {
    CORRECT("correct.ogg"),
    WRONG("wrong.ogg"),
    COMPLETE("complete.ogg"),
    TAP("tap.ogg"),
    TRY_AGAIN("try_again.ogg"),
    GOOD_JOB("good_job.ogg");

    companion object {
        val fileNames: Set<String> = entries.map { it.fileName }.toSet()
    }
}
