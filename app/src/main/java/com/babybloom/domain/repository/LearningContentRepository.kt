package com.babybloom.domain.repository

import com.babybloom.data.local.entity.LearningContentEntity
import com.babybloom.domain.model.LearningContent

interface LearningContentRepository {
    suspend fun seedContent(contentList: List<LearningContent>): List<Long>
    suspend fun getById(id: String): LearningContent?
    suspend fun getByCategory(category: String): List<LearningContent>
    suspend fun getContentForActivity(activityId: String): List<LearningContent>
    suspend fun count(): Int
}