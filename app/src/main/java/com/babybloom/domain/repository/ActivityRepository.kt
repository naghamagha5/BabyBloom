package com.babybloom.domain.repository

import com.babybloom.domain.model.Activity

interface ActivityRepository {
    suspend fun seedActivities(activities: List<Activity>)
    suspend fun getAll(): List<Activity>
    suspend fun getFiltered(modality: String, skillArea: String, difficulty: Int): List<Activity>
    suspend fun getById(id: String): Activity?
    suspend fun count(): Int
}

