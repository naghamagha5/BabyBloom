package com.babybloom.domain.repository

import com.babybloom.domain.model.Child
import kotlinx.coroutines.flow.Flow

interface ChildRepository {
    suspend fun createChild(child: Child): Long
    suspend fun updateChild(child: Child)
    suspend fun deleteChild(child: Child)
    fun getChildrenByUser(userId: Long): Flow<List<Child>>
    suspend fun getById(id: Long): Child?
    fun observeById(id: Long): Flow<Child?>

}