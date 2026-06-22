package com.babybloom.data.repository

import com.babybloom.data.local.dao.ChildDao
import com.babybloom.data.local.entity.ChildEntity
import com.babybloom.domain.model.Child
import com.babybloom.domain.model.ChildStatus
import com.babybloom.domain.repository.ChildRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ChildRepositoryImpl @Inject constructor(
    private val childDao: ChildDao
) : ChildRepository {

    override suspend fun createChild(child: Child): Long =
        childDao.insert(child.toEntity())

    override suspend fun updateChild(child: Child) =
        childDao.update(child.toEntity())

    override suspend fun deleteChild(child: Child) =
        childDao.delete(child.toEntity())

    override fun getChildrenByUser(userId: Long): Flow<List<Child>> =
        childDao.getChildrenByUser(userId).map { it.map { e -> e.toDomain() } }

    override suspend fun getById(id: Long): Child? =
        childDao.getById(id)?.toDomain()

    override fun observeById(id: Long): Flow<Child?> =
        childDao.observeById(id).map { it?.toDomain() }
}

fun ChildEntity.toDomain() = Child(
    id = id,
    userId = userId,
    name = name,
    age = age,
    gender = gender,
    notes = notes,
    avatar = avatar,
    soundEffectEnabled = soundEffectEnabled,
    reducedAnimation = reducedAnimation,
    uiTheme = uiTheme,
    createdAt = createdAt,
    status = ChildStatus.valueOf(status),
    sessionDurationMinutes = sessionDurationMinutes,
    backgroundMusicEnabled = backgroundMusicEnabled
)

fun Child.toEntity() = ChildEntity(
    id = id,
    userId = userId,
    name = name,
    age = age,
    gender = gender,
    notes = notes,
    avatar = avatar,
    soundEffectEnabled = soundEffectEnabled,
    reducedAnimation = reducedAnimation,
    uiTheme = uiTheme,
    createdAt = createdAt,
    status = status.name,
    sessionDurationMinutes = sessionDurationMinutes,
    backgroundMusicEnabled = backgroundMusicEnabled
)
