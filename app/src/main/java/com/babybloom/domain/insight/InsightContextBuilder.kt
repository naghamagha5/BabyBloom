package com.babybloom.domain.insight

import com.babybloom.data.local.dao.ChildProfileSnapshotDao
import com.babybloom.domain.model.ActivityResult
import com.babybloom.domain.model.ChildInsightContext
import com.babybloom.domain.repository.ActivityRepository
import com.babybloom.domain.repository.ActivityResultRepository
import com.babybloom.domain.repository.AssessmentRepository
import com.babybloom.domain.repository.ChildProfileRepository
import com.babybloom.domain.repository.ChildRepository
import com.babybloom.domain.repository.SessionRepository
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InsightContextBuilder @Inject constructor(
    private val childRepository: ChildRepository,
    private val childProfileRepository: ChildProfileRepository,
    private val sessionRepository: SessionRepository,
    private val activityRepository: ActivityRepository,
    private val activityResultRepository: ActivityResultRepository,
    private val assessmentRepository: AssessmentRepository,
    private val snapshotDao: ChildProfileSnapshotDao
) {
    suspend fun build(childId: Long, now: Long = System.currentTimeMillis()): ChildInsightContext {
        val child = requireNotNull(childRepository.getById(childId)) { "Child not found" }
        val profile = requireNotNull(childProfileRepository.getByChildId(childId)) { "Child profile not found" }
        val sessions = sessionRepository.getAllSessions(childId)
        val results = activityResultRepository.getByChild(childId).sortedBy { it.timestamp }
        val activities = activityRepository.getAll().associateBy { it.id }
        val assessment = assessmentRepository.getLatestForChild(childId)
        val snapshots = snapshotDao.getForChild(childId)
        val sevenDaysAgo = now - TimeUnit.DAYS.toMillis(7)
        val thirtyDaysAgo = now - TimeUnit.DAYS.toMillis(30)

        fun resultsSince(cutoff: Long) = results.filter { it.timestamp >= cutoff }
        fun sessionsSince(cutoff: Long) = sessions.filter { it.startTime >= cutoff }
        fun average(values: List<Float>): Double? = values.takeIf { it.isNotEmpty() }?.average()
        fun averageLong(values: List<Long>): Double? = values.takeIf { it.isNotEmpty() }?.average()
        fun rounded(value: Double?): Any = value
            ?.takeIf { it.isFinite() }
            ?.let { kotlin.math.round(it * 1000.0) / 1000.0 }
            ?: JSONObject.NULL
        fun safeNumber(value: Float): Any = value.takeIf { it.isFinite() } ?: JSONObject.NULL

        fun trend(rows: List<ActivityResult>): String {
            if (rows.size < 4) return "INSUFFICIENT_DATA"
            val ordered = rows.sortedBy { it.timestamp }
            val midpoint = ordered.size / 2
            val before = ordered.take(midpoint).map { it.score }.average()
            val after = ordered.drop(midpoint).map { it.score }.average()
            return when {
                after - before >= 0.05 -> "IMPROVING"
                before - after >= 0.05 -> "DECLINING"
                else -> "STABLE"
            }
        }

        fun windowJson(windowResults: List<ActivityResult>, windowSessions: Int) = JSONObject().apply {
            put("sessionCount", windowSessions)
            put("activityCount", windowResults.size)
            put("averageScore", rounded(average(windowResults.map { it.score })))
            put("scoreTrend", trend(windowResults))
            put("averageAttempts", rounded(average(windowResults.map { it.attempts.toFloat() })))
            put("averageDurationSeconds", rounded(averageLong(windowResults.map { it.duration })?.div(1000.0)))
        }

        val root = JSONObject().apply {
            put("schemaVersion", 1)
            put("generatedAtEpochMs", now)
            put("child", JSONObject().apply {
                put("age", child.age)
                put("gender", child.gender)
                put("parentNotes", child.notes.take(500))
            })
            put("currentProfile", JSONObject().apply {
                put("dominantModality", profile.dominantModality)
                put("modalityPercentages", JSONObject().apply {
                    put("visual", safeNumber(profile.visualPreferencePercent))
                    put("audio", safeNumber(profile.audioPreferencePercent))
                    put("interactive", safeNumber(profile.interactivePreferencePercent))
                })
                put("skillLevels", JSONObject().apply {
                    put("language", profile.languageLevel)
                    put("numeracy", profile.numeracyLevel)
                    put("motor", profile.motorLevel)
                })
                put("skillProgress", JSONObject().apply {
                    put("language", safeNumber(profile.languageProgress))
                    put("numeracy", safeNumber(profile.numeracyProgress))
                    put("motor", safeNumber(profile.motorProgress))
                })
                put("weakSkillAreas", JSONArray(profile.weakSkillList))
            })
            put("initialAssessment", assessment?.let {
                JSONObject().apply {
                    put("languageLevel", it.initialLanguageLevel)
                    put("numeracyLevel", it.initialNumeracyLevel)
                    put("motorLevel", it.initialMotorLevel)
                    put("dominantModality", it.dominantModality)
                    put("completedAt", it.completedAt)
                }
            } ?: JSONObject.NULL)
            put("lifetime", windowJson(results, sessions.size))
            put("last30Days", windowJson(resultsSince(thirtyDaysAgo), sessionsSince(thirtyDaysAgo).size))
            put("last7Days", windowJson(resultsSince(sevenDaysAgo), sessionsSince(sevenDaysAgo).size))
            put("skillPerformance", JSONArray().apply {
                listOf("LANGUAGE", "NUMERACY", "MOTOR").forEach { skill ->
                    val rows = results.filter { activities[it.activityId]?.skillArea == skill }
                    put(JSONObject().apply {
                        put("skill", skill)
                        put("activityCount", rows.size)
                        put("averageScore", rounded(average(rows.map { it.score })))
                        put("trend", trend(rows))
                    })
                }
            })
            put("modalityPerformance", JSONArray().apply {
                listOf("VISUAL", "AUDIO", "INTERACTIVE").forEach { modality ->
                    val rows = results.filter { activities[it.activityId]?.modality == modality }
                    put(JSONObject().apply {
                        put("modality", modality)
                        put("activityCount", rows.size)
                        put("averageScore", rounded(average(rows.map { it.score })))
                        put("trend", trend(rows))
                    })
                }
            })
            put("multimodalSignals", JSONObject().apply {
                put("attentionAverage", rounded(average(results.mapNotNull { it.attentionScore })))
                put("attentionSamples", results.count { it.attentionScore != null })
                put("speechConfidenceAverage", rounded(average(results.mapNotNull { it.speechConfidence })))
                put("speechSamples", results.count { it.speechConfidence != null })
                put("touchQualityAverage", rounded(average(results.mapNotNull { it.touchQualityScore })))
                put("touchSamples", results.count { it.touchQualityScore != null })
            })
            put("modalityHistory", JSONArray().apply {
                snapshots
                    .filterIndexed { index, item -> index == 0 || item.dominantModality != snapshots[index - 1].dominantModality || index == snapshots.lastIndex }
                    .takeLast(12)
                    .forEach { snapshot ->
                        put(JSONObject().apply {
                            put("capturedAt", snapshot.capturedAt)
                            put("dominantModality", snapshot.dominantModality)
                            put("visual", safeNumber(snapshot.visualPreferencePercent))
                            put("audio", safeNumber(snapshot.audioPreferencePercent))
                            put("interactive", safeNumber(snapshot.interactivePreferencePercent))
                        })
                    }
            })
            put("representativeActivities", JSONArray().apply {
                val selected = (results.takeLast(8) + results.sortedBy { it.score }.take(2))
                    .distinctBy { it.id }
                    .sortedByDescending { it.timestamp }
                    .take(10)
                selected.forEach { result ->
                    val activity = activities[result.activityId]
                    put(JSONObject().apply {
                        put("timestamp", result.timestamp)
                        put("title", activity?.title ?: result.activityId)
                        put("skill", activity?.skillArea ?: "UNKNOWN")
                        put("modality", activity?.modality ?: "UNKNOWN")
                        put("score", safeNumber(result.score))
                        put("attempts", result.attempts)
                        put("durationSeconds", result.duration / 1000)
                    })
                }
            })
            put("dataCoverage", JSONObject().apply {
                put("sessionCount", sessions.size)
                put("activityCount", results.size)
                put("profileSnapshotCount", snapshots.size)
            })
        }

        return ChildInsightContext(root.toString(), results.size, sessions.size)
    }
}
