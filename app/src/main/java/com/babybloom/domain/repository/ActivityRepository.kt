package com.babybloom.domain.repository

import com.babybloom.domain.model.Activity
import com.babybloom.domain.model.ActivityWithContent

interface ActivityRepository {
    suspend fun seedActivities(activities: List<Activity>)
    suspend fun getAll(): List<Activity>
    suspend fun getFiltered(modality: String, skillArea: String, difficulty: Int): List<Activity>
    suspend fun count(): Int
    suspend fun getActivityWithContent(activityId: String): ActivityWithContent?  // ← add this
    suspend fun getActivitiesForPlanning(skillArea: String, difficultyLevel: Int): List<Activity>
    suspend fun getById(activityId: String): Activity?
}