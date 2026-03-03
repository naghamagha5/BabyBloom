package com.babybloom.data.repository

import com.babybloom.di.SessionManager
import com.babybloom.data.local.dao.UserDao
import com.babybloom.data.local.entity.UserEntity
import com.babybloom.util.HashUtils
import javax.inject.Inject
import javax.inject.Singleton

// ── Result wrapper ─────────────────────────────────────────────────────────
sealed class AuthResult {
    object Success              : AuthResult()
    object InvalidCredentials   : AuthResult()
    data class Error(val message: String) : AuthResult()
}

@Singleton
class AuthRepository @Inject constructor(
    private val userDao: UserDao,
    private val sessionManager: SessionManager
) {

    // ── REGISTER ───────────────────────────────────────────────────────────
    suspend fun register(
        name: String,
        email: String,
        password: String
    ): AuthResult {
        return try {
            // 1. Check if email already exists
            val alreadyExists = userDao.emailExists(email.trim().lowercase())
            if (alreadyExists) {
                return AuthResult.Error("EMAIL_EXISTS")
            }

            // 2. Hash the password — never store plain text
            val passwordHash = HashUtils.sha256(password)

            // 3. Create entity
            val newUser = UserEntity(
                name         = name.trim(),
                email        = email.trim().lowercase(),
                passwordHash = passwordHash,
                createdAt    = System.currentTimeMillis()
            )

            // 4. Insert into Room
            val insertedId = userDao.insert(newUser)

            if (insertedId > 0) AuthResult.Success
            else AuthResult.Error("UNKNOWN_ERROR")

        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            // Safety net: catches the UNIQUE index on email column
            AuthResult.Error("EMAIL_EXISTS")
        } catch (e: Exception) {
            AuthResult.Error("UNKNOWN_ERROR")
        }
    }

    // ── LOGIN ──────────────────────────────────────────────────────────────
    suspend fun login(email: String, password: String): AuthResult {
        return try {
            val user = userDao.findByEmail(email.trim().lowercase())
                ?: return AuthResult.InvalidCredentials

            val hashedInput = HashUtils.sha256(password)
            if (hashedInput != user.passwordHash) {
                return AuthResult.InvalidCredentials
            }

            // Credentials match — save session
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

    // ── LOGOUT ─────────────────────────────────────────────────────────────
    suspend fun logout() {
        sessionManager.clearSession()
    }
}