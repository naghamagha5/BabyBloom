package com.babybloom.data.repository

import com.babybloom.data.local.dao.ChildProfileDao
import com.babybloom.data.local.entity.ChildProfileEntity
import com.babybloom.domain.model.ChildProfile
import com.babybloom.domain.repository.ChildProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ChildProfileRepositoryImpl @Inject constructor(
    private val childProfileDao: ChildProfileDao
) : ChildProfileRepository {

    override suspend fun createProfile(profile: ChildProfile) =
        childProfileDao.insert(profile.toEntity())

    override suspend fun updateProfile(profile: ChildProfile) =
        childProfileDao.update(profile.toEntity())

    override suspend fun getProfile(childId: Long): ChildProfile? =
        childProfileDao.getByChildId(childId)?.toDomain()

    override fun observeProfile(childId: Long): Flow<ChildProfile?> =
        childProfileDao.observeByChildId(childId).map { it?.toDomain() }
}

fun ChildProfileEntity.toDomain() = ChildProfile(childId, visualScore, audioScore, gameScore, languageLevel, numeracyLevel, motorLevel, totalSessionCount, lastUpdated)
fun ChildProfile.toEntity() = ChildProfileEntity(childId, visualScore, audioScore, gameScore, languageLevel, numeracyLevel, motorLevel, totalSessionCount, lastUpdated)