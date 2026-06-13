package com.babybloom.data.local.seeder

import android.content.Context
import com.babybloom.data.local.dao.ActivityContentDao
import com.babybloom.data.local.dao.ActivityDao
import com.babybloom.data.local.entity.ActivityContentEntity
import com.babybloom.data.local.entity.ActivityEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActivitySeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val activityDao: ActivityDao,
    private val activityContentDao: ActivityContentDao
) {
    suspend fun seedIfEmpty() = withContext(Dispatchers.IO) {
        val json = context.assets
            .open("activities.json")
            .bufferedReader()
            .use { it.readText() }

        val type = object : TypeToken<List<ActivityJson>>() {}.type
        val items = Gson().fromJson<List<ActivityJson>>(json, type)

        items.forEach { item ->
            activityDao.insert(
                ActivityEntity(
                    id             = item.id,
                    title          = item.title,
                    description    = item.description,
                    modality       = item.modality,
                    skillArea      = item.skillArea,
                    difficultyLevel = item.difficultyLevel,
                    activityType   = item.activityType,
                    isActive       = item.isActive,
                    configJson     = item.configJson
                )
            )
            item.contentIds.forEachIndexed { index, contentId ->
                activityContentDao.insert(
                    ActivityContentEntity(
                        activityId = item.id,
                        orderIndex = index,
                        contentId  = contentId
                    )
                )
            }
        }
    }

    private data class ActivityJson(
        val id: String,
        val title: String,
        val description: String,
        val modality: String,
        val skillArea: String,
        val difficultyLevel: Int,
        val activityType: String,
        val isActive: Boolean,
        val configJson: String,
        val contentIds: List<String>
    )
}
