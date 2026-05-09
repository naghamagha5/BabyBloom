package com.babybloom.domain.repository

import com.babybloom.domain.model.ChildProfile
import kotlinx.coroutines.flow.Flow

interface ChildProfileRepository {
    suspend fun createProfile(profile: ChildProfile)
    suspend fun updateProfile(profile: ChildProfile)
    suspend fun getByChildId(childId: Long): ChildProfile?   // was getProfile() — unified name
    suspend fun upsert(profile: ChildProfile)
    fun observeProfile(childId: Long): Flow<ChildProfile?>
}