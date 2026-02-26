package com.babybloom.data.repository

import androidx.room.PrimaryKey
import com.babybloom.data.local.dao.ActivityContentDao
import com.babybloom.data.local.dao.LearningContentDao
import com.babybloom.data.local.entity.LearningContentEntity
import com.babybloom.domain.model.LearningContent
import com.babybloom.domain.repository.LearningContentRepository
import javax.inject.Inject

class LearningContentRepositoryImpl @Inject constructor(
    private val learningContentDao: LearningContentDao,
    private val activityContentDao: ActivityContentDao
) : LearningContentRepository {

    override suspend fun seedContent(contentList: List<LearningContent>) =
        learningContentDao.insertAll(contentList.map { it.toEntity() })

    override suspend fun getById(id: String): LearningContent? =
        learningContentDao.getById(id)?.toDomain()

    override suspend fun getByCategory(category: String): List<LearningContent> =
        learningContentDao.getByCategory(category).map { it.toDomain() }

    override suspend fun getContentForActivity(activityId: String): List<LearningContent> =
        activityContentDao.getContentForActivity(activityId).map { it.toDomain() }

    override suspend fun count(): Int =
        learningContentDao.count()
}

fun LearningContentEntity.toDomain() = LearningContent(
    id, labelAr, code ,category, imagePath, audioPath, difficultyLevel
)
fun LearningContent.toEntity() = LearningContentEntity(
    id, labelAr, code ,category, imagePath, audioPath, difficultyLevel
)
