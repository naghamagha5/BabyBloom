package com.babybloom.data.repository

import com.babybloom.data.local.dao.UserDao
import com.babybloom.data.local.entity.UserEntity
import com.babybloom.domain.model.User
import com.babybloom.domain.repository.UserRepository
import javax.inject.Inject

class UserRepositoryImpl @Inject constructor(
    private val userDao: UserDao
) : UserRepository {

    override suspend fun register(user: User): Long =
        userDao.insert(user.toEntity())

    override suspend fun getByEmail(email: String): User? =
        userDao.getByEmail(email)?.toDomain()

    override suspend fun getById(id: Long): User? =
        userDao.getById(id)?.toDomain()

    override suspend fun emailExists(email: String): Boolean =
        userDao.emailExists(email)
}

fun UserEntity.toDomain() = User(id, name, email, passwordHash, createdAt)
fun User.toEntity() = UserEntity(id, name, email, passwordHash, createdAt)