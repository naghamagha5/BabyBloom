package com.babybloom.data.repository

import com.babybloom.data.local.dao.UserDao
import com.babybloom.data.local.entity.UserEntity
import com.babybloom.domain.model.User
import com.babybloom.domain.repository.UserRepository
import org.mindrot.jbcrypt.BCrypt
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

    override suspend fun setParentLockPin(userId: Long, rawPin: String) {
        val hashed = BCrypt.hashpw(rawPin, BCrypt.gensalt())
        val user = userDao.getById(userId) ?: return
        userDao.update(user.copy(parentLockPin = hashed))
    }

    override suspend fun verifyParentLockPin(userId: Long, enteredPin: String): Boolean {
        val user = userDao.getById(userId) ?: return false
        val storedHash = user.parentLockPin ?: return false
        return BCrypt.checkpw(enteredPin, storedHash)
    }

    override suspend fun verifyParentPassword(userId: Long, enteredPassword: String): Boolean {
        val user = userDao.getById(userId) ?: return false
        return BCrypt.checkpw(enteredPassword, user.passwordHash)
    }
}

fun UserEntity.toDomain() = User(id, name, email, passwordHash, parentLockPin, createdAt)
fun User.toEntity() = UserEntity(id, name, email, passwordHash, parentLockPin, createdAt)
