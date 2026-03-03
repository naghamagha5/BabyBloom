package com.babybloom.data.repository

import com.babybloom.data.local.dao.UserDao
import com.babybloom.data.local.entity.UserEntity
import javax.inject.Inject
import javax.inject.Singleton

// ── Result wrapper ─────────────────────────────────────────────────────────
sealed class AuthResult<out T> {
    data class Success<T>(val data: T) : AuthResult<T>()
    data class Error(val message: String) : AuthResult<Nothing>()
    object Loading : AuthResult<Nothing>()
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
    private val userDao: UserDao       // ← your existing UserDao from Room
) {

    suspend fun register(
        name: String,
        email: String,
        password: String
    ): AuthResult<UserEntity> {

        return try {

            // ── 1. Check if email already exists in the database ───────────
            val alreadyExists = userDao.emailExists(email.trim().lowercase())
            if (alreadyExists) {
                return AuthResult.Error("EMAIL_EXISTS")
            }

            // ── 2. Hash the password — never store plain text ──────────────
            val passwordHash = hashPassword(password)

            // ── 3. Create the entity using your exact UserEntity fields ─────
            val newUser = UserEntity(
                name         = name.trim(),
                email        = email.trim().lowercase(),
                passwordHash = passwordHash,
                createdAt    = System.currentTimeMillis()
            )

            // ── 4. Insert into Room using your UserDao.insert() ────────────
            val insertedId = userDao.insert(newUser)

            if (insertedId > 0) {
                AuthResult.Success(newUser.copy(id = insertedId))
            } else {
                AuthResult.Error("UNKNOWN_ERROR")
            }

        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            // Safety net: catches the UNIQUE index on email column
            AuthResult.Error("EMAIL_EXISTS")

        } catch (e: Exception) {
            AuthResult.Error("UNKNOWN_ERROR")
        }
    }

    // ── SHA-256 hashing — built into Android, no library needed ───────────
    private fun hashPassword(password: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val bytes  = digest.digest(password.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
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