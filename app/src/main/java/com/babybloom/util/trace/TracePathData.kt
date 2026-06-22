package com.babybloom.util.trace

import androidx.compose.ui.geometry.Offset
import kotlin.math.sqrt

data class TraceCircle(
    val center: Offset,  // normalised 0..1
    val radius: Float    // normalised (relative to viewportWidth)
)

/**
 * Loaded from {contentId}_trace.xml.
 *
 * [viewportRatio]       – viewportWidth / viewportHeight from the SVG.
 *                         Used by the UI to set the card's aspectRatio so that
 *                         ContentScale.FillBounds maps SVG coords 1:1 to Canvas
 *                         coords — guaranteeing dot circles land exactly on the
 *                         circles drawn in the SVG, regardless of letter shape.
 *
 * [logicStrokes]        – named "logic_*" paths, sorted by name.
 * [orderedHintStrokes]  – same strokes, each resampled to ~50 evenly-spaced pts.
 * [circles]             – dot circles from the XML, in document order.
 * [hintPath]            – flat concat of orderedHintStrokes (legacy/fallback).
 */
data class TracePathData(
    val contentId:          String,
    val labelAr:            String,
    val viewportRatio:      Float,              // viewportWidth / viewportHeight
    val logicStrokes:       List<List<Offset>>,
    val orderedHintStrokes: List<List<Offset>>,
    val circles:            List<TraceCircle>,
    val hintPath:           List<Offset>
) {
    companion object {
        /** Fallback ratio (square) when viewport data is unavailable. */
        const val DEFAULT_RATIO = 1f
    }
}