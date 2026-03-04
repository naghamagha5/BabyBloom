package com.babybloom.data.repository

import com.babybloom.di.SessionManager
import com.babybloom.data.local.dao.UserDao
import com.babybloom.data.local.entity.UserEntity
import com.babybloom.util.HashUtils
import javax.inject.Inject
import javax.inject.Singleton

// ── Result wrapper ─────────────────────────────────────────────────────────
sealed class AuthResult {
    object Success            : AuthResult()
    object InvalidCredentials : AuthResult()
    data class Error(val message: String) : AuthResult()
}

@Singleton
class AuthRepository @Inject constructor(
    private val userDao       : UserDao,
    private val sessionManager: SessionManager
) {

    // ── REGISTER ───────────────────────────────────────────────────────────
    suspend fun register(
        name    : String,
        email   : String,
        password: String
    ): AuthResult {
        return try {
            // 1. Check duplicate email
            if (userDao.emailExists(email.trim().lowercase()))
                return AuthResult.Error("EMAIL_EXISTS")

            // 2. Hash password
            val passwordHash = HashUtils.sha256(password)

            // 3. Build entity
            val newUser = UserEntity(
                name         = name.trim(),
                email        = email.trim().lowercase(),
                passwordHash = passwordHash,
                createdAt    = System.currentTimeMillis()
            )

            // 4. Insert and get the auto-generated id back
            val insertedId = userDao.insert(newUser)

            if (insertedId <= 0) return AuthResult.Error("UNKNOWN_ERROR")

            // 5. Save session right here — same as login does
            //    This is the key change: RegisterViewModel no longer needs
            //    to touch SessionManager at all. AddChildViewModel will call
            //    sessionManager.userId.first() and get this id correctly.
            sessionManager.saveSession(
                userId = insertedId,
                name   = newUser.name,
                email  = newUser.email
            )

            AuthResult.Success

        } catch (e: android.database.sqlite.SQLiteConstraintException) {
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

            if (HashUtils.sha256(password) != user.passwordHash)
                return AuthResult.InvalidCredentials

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