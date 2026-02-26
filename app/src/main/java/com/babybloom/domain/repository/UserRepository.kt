package com.babybloom.domain.repository

import com.babybloom.domain.model.User

interface UserRepository {
    suspend fun register(user: User): Long
    suspend fun getByEmail(email: String): User?
    suspend fun getById(id: Long): User?
    suspend fun emailExists(email: String): Boolean
}