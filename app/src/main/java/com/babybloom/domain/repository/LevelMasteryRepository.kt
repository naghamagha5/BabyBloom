package com.babybloom.domain.repository

import com.babybloom.data.local.entity.LevelMasteryEntity

interface LevelMasteryRepository {
    suspend fun upsert(entity: LevelMasteryEntity)
    suspend fun get(childId: Long, skillArea: String, level: Int): LevelMasteryEntity?
    suspend fun getForSkill(childId: Long, skillArea: String): List<LevelMasteryEntity>
    suspend fun getAllForChild(childId: Long): List<LevelMasteryEntity>
    suspend fun getByContentId(childId: Long, contentId: String): LevelMasteryEntity?
    suspend fun getContentScoresForChild(childId: Long): List<LevelMasteryEntity>
    suspend fun incrementMastered(childId: Long, skillArea: String, level: Int)
    suspend fun getMasteredCount(childId: Long, skillArea: String, level: Int): Int
    suspend fun deleteAllForChild(childId: Long)
}
