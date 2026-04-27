package com.babybloom.util.trace

import android.content.Context
import android.util.Log
import androidx.compose.ui.geometry.Offset
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

private const val TAG = "TracePathLoader"

@Singleton
class TracePathLoader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val cache = mutableMapOf<String, TracePathData?>()
    private val HINT_POINTS_PER_STROKE = 50

    fun load(contentId: String, labelAr: String = ""): TracePathData? {
        if (cache.containsKey(contentId)) return cache[contentId]
        val result = runCatching { parse(contentId, labelAr) }
            .onFailure { Log.e(TAG, "Failed loading $contentId", it) }
            .getOrNull()
        cache[contentId] = result
        return result
    }

    private fun parse(contentId: String, labelAr: String): TracePathData? {
        val resId = context.resources.getIdentifier(
            "${contentId}_trace", "drawable", context.packageName
        )
        if (resId == 0) { Log.w(TAG, "'${contentId}_trace' not found"); return null }

        val parser    = context.resources.getXml(resId)
        var viewportW = 0f
        var viewportH = 0f
        val logicNamed = mutableListOf<Pair<String, List<Offset>>>()
        val dotRaw     = mutableListOf<TraceCircle>()

        try {
            var ev = parser.eventType
            while (ev != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (ev == org.xmlpull.v1.XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "vector" -> {
                            for (i in 0 until parser.attributeCount) {
                                val local = parser.getAttributeName(i).substringAfterLast(':').trim()
                                val v     = parser.getAttributeValue(i)
                                when (local) {
                                    "viewportWidth"  -> viewportW = v.toFloatOrNull() ?: 0f
                                    "viewportHeight" -> viewportH = v.toFloatOrNull() ?: 0f
                                }
                            }
                        }
                        "path" -> {
                            var name: String? = null; var pathData: String? = null
                            for (i in 0 until parser.attributeCount) {
                                when (parser.getAttributeName(i).substringAfterLast(':').trim()) {
                                    "name"     -> name     = parser.getAttributeValue(i)
                                    "pathData" -> pathData = parser.getAttributeValue(i)
                                }
                            }
                            if (!pathData.isNullOrBlank()) when {
                                name == "background_shape" -> Unit
                                name != null && name.startsWith("logic_") -> {
                                    val pts = SvgPathParser.parseSubpaths(pathData)
                                        .filter { it.size >= 2 }.flatten()
                                    if (pts.size >= 2) logicNamed.add(Pair(name, pts))
                                }
                                else -> detectCircle(pathData)?.let { dotRaw.add(it) }
                            }
                        }
                    }
                }
                ev = parser.next()
            }
        } finally { parser.close() }

        // Infer viewport from bounding box if missing
        if (viewportW <= 0f || viewportH <= 0f) {
            val allX = logicNamed.flatMap { it.second }.map { it.x } + dotRaw.map { it.center.x }
            val allY = logicNamed.flatMap { it.second }.map { it.y } + dotRaw.map { it.center.y }
            viewportW = (allX.maxOrNull() ?: 100f) * 1.05f
            viewportH = (allY.maxOrNull() ?: 100f) * 1.05f
            Log.w(TAG, "$contentId: viewport inferred ${viewportW}x${viewportH}")
        }

        if (logicNamed.isEmpty() && dotRaw.isEmpty()) {
            Log.w(TAG, "$contentId: nothing useful"); return null
        }

        val sortedLogic = logicNamed.sortedBy { it.first }

        val normLogic = sortedLogic.map { (_, pts) ->
            pts.map { Offset((it.x / viewportW).coerceIn(0f,1f), (it.y / viewportH).coerceIn(0f,1f)) }
        }
        val normDots = dotRaw.map { c ->
            TraceCircle(
                center = Offset((c.center.x / viewportW).coerceIn(0f,1f), (c.center.y / viewportH).coerceIn(0f,1f)),
                radius = c.radius / viewportW
            )
        }

        val hintStrokes = normLogic.map { resample(it, HINT_POINTS_PER_STROKE) }
        val hintFlat    = hintStrokes.flatten()

        // THE KEY VALUE: ratio of the SVG viewport — used by TraceScreen to set
        // the card's aspectRatio so FillBounds maps SVG coords 1:1 to Canvas coords
        val ratio = (viewportW / viewportH).coerceIn(0.1f, 10f)

        Log.d(TAG, "$contentId: ratio=$ratio, ${normLogic.size} strokes, ${normDots.size} circles")

        return TracePathData(
            contentId          = contentId,
            labelAr            = labelAr,
            viewportRatio      = ratio,
            logicStrokes       = normLogic,
            orderedHintStrokes = hintStrokes,
            circles            = normDots,
            hintPath           = hintFlat
        )
    }

    private fun detectCircle(pathData: String): TraceCircle? {
        val arcCount = pathData.count { it == 'a' || it == 'A' }
        if (arcCount != 2) return null
        val pts = SvgPathParser.parseSubpaths(pathData).firstOrNull()?.takeIf { it.size >= 6 } ?: return null
        val cx = pts.sumOf { it.x.toDouble() }.toFloat() / pts.size
        val cy = pts.sumOf { it.y.toDouble() }.toFloat() / pts.size
        val r  = pts.map { sqrt((it.x-cx).pow(2)+(it.y-cy).pow(2)) }.average().toFloat()
        if (r < 0.5f) return null
        return TraceCircle(Offset(cx, cy), r)
    }

    private fun resample(points: List<Offset>, count: Int): List<Offset> {
        if (points.isEmpty()) return emptyList()
        if (points.size <= count) return points
        val segs  = points.zipWithNext { a, b -> sqrt((b.x-a.x).pow(2)+(b.y-a.y).pow(2)) }
        val total = segs.sum().coerceAtLeast(1e-6f)
        val step  = total / count
        val out   = mutableListOf(points.first())
        var accum = 0f; var next = step
        segs.forEachIndexed { i, seg ->
            val end = accum + seg
            while (next <= end && out.size < count) {
                val t = ((next-accum)/seg.coerceAtLeast(1e-8f)).coerceIn(0f,1f)
                out += Offset(points[i].x+(points[i+1].x-points[i].x)*t, points[i].y+(points[i+1].y-points[i].y)*t)
                next += step
            }
            accum = end
        }
        if (out.last() != points.last()) out += points.last()
        return out
    }
}