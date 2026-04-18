package com.babybloom.data.repository

import android.content.Context
import com.babybloom.data.local.dao.ActivityContentDao
import com.babybloom.data.local.dao.ActivityDao
import com.babybloom.data.local.dao.LearningContentDao
import com.babybloom.data.local.entity.ActivityContentEntity
import com.babybloom.data.local.entity.ActivityEntity
import com.babybloom.domain.model.Activity
import com.babybloom.domain.repository.ActivityRepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class ActivityRepositoryImpl @Inject constructor(
    private val activityDao: ActivityDao,
    @ApplicationContext private val context: Context
) : ActivityRepository {

    override suspend fun seedActivities(activities: List<Activity>) =
        activityDao.insertAll(activities.map { it.toEntity() })

    override suspend fun getAll(): List<Activity> =
        activityDao.getAll().map { it.toDomain() }

    override suspend fun getFiltered(
        modality: String,
        skillArea: String,
        difficulty: Int
    ): List<Activity> =
        activityDao.getFiltered(modality, skillArea, difficulty).map { it.toDomain() }

    override suspend fun getById(id: String): Activity? =
        activityDao.getById(id)?.toDomain()

    override suspend fun count(): Int =
        activityDao.count()
}

fun ActivityEntity.toDomain() = Activity(
    id, title, description, modality, skillArea, difficultyLevel, activityType, isActive , configJson
)
fun Activity.toEntity() = ActivityEntity(
    id, title, description, modality, skillArea, difficultyLevel, activityType, isActive , configJson
)
