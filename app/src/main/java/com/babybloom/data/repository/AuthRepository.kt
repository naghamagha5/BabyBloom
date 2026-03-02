package com.babybloom.data.repository

import com.babybloom.di.SessionManager
import com.babybloom.data.local.dao.UserDao
import com.babybloom.util.HashUtils
import javax.inject.Inject
import javax.inject.Singleton

sealed class AuthResult {
    object Success          : AuthResult()
    object InvalidCredentials : AuthResult()
    data class Error(val message: String) : AuthResult()
}

@Singleton
class AuthRepository @Inject constructor(
    private val userDao: UserDao,
    private val sessionManager: SessionManager
) {
    suspend fun login(email: String, password: String): AuthResult {
        return try {
            val user = userDao.findByEmail(email.trim().lowercase())
                ?: return AuthResult.InvalidCredentials

            val hashedInput = HashUtils.sha256(password)
            if (hashedInput != user.passwordHash) {
                return AuthResult.InvalidCredentials
            }

            // ✅ Credentials match — save session
            sessionManager.saveSession(
                userId = user.id,
                name   = user.name,
                email  = user.email
            )
            AuthResult.Success

        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun logout() {
        sessionManager.clearSession()
    }
}