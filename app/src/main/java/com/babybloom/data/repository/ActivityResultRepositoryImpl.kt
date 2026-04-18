package com.babybloom.data.repository

import com.babybloom.data.local.dao.ActivityResultDao
import com.babybloom.data.local.entity.ActivityResultEntity
import com.babybloom.domain.model.ActivityResult
import com.babybloom.domain.model.RecentActivity
import com.babybloom.domain.repository.ActivityResultRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import com.babybloom.data.local.entity.SkillScoreRow

class ActivityResultRepositoryImpl @Inject constructor(
    private val activityResultDao: ActivityResultDao
) : ActivityResultRepository {

    override suspend fun saveResult(result: ActivityResult): Long =
        activityResultDao.insert(result.toEntity())

    override suspend fun getBySession(sessionId: Long): List<ActivityResult> =
        activityResultDao.getBySession(sessionId).map { it.toDomain() }

    override suspend fun getByChild(childId: Long): List<ActivityResult> =
        activityResultDao.getByChild(childId).map { it.toDomain() }

    override suspend fun getRecentBySkillArea(childId: Long, skillArea: String, limit: Int): List<ActivityResult> =
        activityResultDao.getRecentBySkillArea(childId, skillArea, limit).map { it.toDomain() }

    override suspend fun getRecentByModality(childId: Long, modality: String, limit: Int): List<ActivityResult> =
        activityResultDao.getRecentByModality(childId, modality, limit).map { it.toDomain() }

    override fun observeByChild(childId: Long): Flow<List<ActivityResult>> =
        activityResultDao.observeByChild(childId).map { it.map { e -> e.toDomain() } }

    override suspend fun getRecentActivities(childId: Long, limit: Int): List<RecentActivity> =
        activityResultDao.getRecentWithTitle(childId, limit).map { row ->
            RecentActivity(
                name = row.activityTitle,
                score = row.score,
                timeAgoLabel = formatTimeAgo(row.timestamp),
                durationMs = row.duration
            )
        }

    private fun formatTimeAgo(timestamp: Long): String {
        val diff    = System.currentTimeMillis() - timestamp
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        val hours   = TimeUnit.MILLISECONDS.toHours(diff)
        val days    = TimeUnit.MILLISECONDS.toDays(diff)
        return when {
            minutes < 1   -> "منذ لحظات"
            minutes < 60  -> "منذ $minutes دقيقة"
            hours   < 24  -> "منذ $hours ساعة"
            days    == 1L -> "منذ يوم"
            else          -> "منذ $days أيام"
        }
    }

    override suspend fun getSkillScoresForChart(childId: Long): List<SkillScoreRow> =
        activityResultDao.getSkillScoresForChart(childId)
}

fun ActivityResultEntity.toDomain() = ActivityResult(id, sessionId, childId, activityId, score, duration, correctCount, incorrectCount, timestamp)
fun ActivityResult.toEntity() = ActivityResultEntity(id, sessionId, childId, activityId, score, duration, correctCount, incorrectCount, timestamp)