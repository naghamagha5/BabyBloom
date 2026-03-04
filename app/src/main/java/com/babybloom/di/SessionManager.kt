package com.babybloom.di

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Extension on Context — do this ONCE in the file, not inside a class
private val Context.dataStore by preferencesDataStore(name = "session_prefs")

@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        private val KEY_USER_ID      = longPreferencesKey("user_id")
        private val KEY_USER_NAME    = stringPreferencesKey("user_name")
        private val KEY_USER_EMAIL   = stringPreferencesKey("user_email")
    }

    // ─── Save session after successful login ───────────────────────────────
    suspend fun saveSession(userId: Long, name: String, email: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_IS_LOGGED_IN] = true
            prefs[KEY_USER_ID]      = userId
            prefs[KEY_USER_NAME]    = name
            prefs[KEY_USER_EMAIL]   = email
        }
    }

    // ─── Clear session on logout ───────────────────────────────────────────
    suspend fun clearSession() {
        context.dataStore.edit { it.clear() }
    }

    // ─── Observe login state (used in NavGraph to auto-route) ─────────────
    val isLoggedIn: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_IS_LOGGED_IN] ?: false }

    val userId: Flow<Long> = context.dataStore.data
        .map { prefs -> prefs[KEY_USER_ID] ?: -1L }

    val userName: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[KEY_USER_NAME] ?: "" }
    val userEmail: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[KEY_USER_EMAIL] ?: "" }


    private val KEY_CREATED_AT = longPreferencesKey("created_at")
    // expose it:
    val createdAt: Flow<Long> = context.dataStore.data
        .map { prefs -> prefs[KEY_CREATED_AT] ?: 0L }
}

