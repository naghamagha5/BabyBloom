package com.babybloom.data.repository

import com.babybloom.data.local.dao.LevelMasteryDao
import com.babybloom.data.local.entity.LevelMasteryEntity
import com.babybloom.domain.repository.LevelMasteryRepository
import javax.inject.Inject

class LevelMasteryRepositoryImpl @Inject constructor(
    private val dao: LevelMasteryDao
) : LevelMasteryRepository {

    override suspend fun upsert(entity: LevelMasteryEntity) =
        dao.upsert(entity)

    override suspend fun get(childId: Long, skillArea: String, level: Int): LevelMasteryEntity? =
        dao.get(childId, skillArea, level)

    override suspend fun getForSkill(childId: Long, skillArea: String): List<LevelMasteryEntity> =
        dao.getForSkill(childId, skillArea)

    override suspend fun getAllForChild(childId: Long): List<LevelMasteryEntity> =
        dao.getAllForChild(childId)

    override suspend fun incrementMastered(childId: Long, skillArea: String, level: Int) =
        dao.incrementMastered(childId, skillArea, level)

    override suspend fun getMasteredCount(childId: Long, skillArea: String, level: Int): Int =
        dao.get(childId, skillArea, level)?.masteredCount ?: 0
}