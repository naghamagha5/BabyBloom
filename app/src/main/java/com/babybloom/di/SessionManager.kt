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

private val Context.dataStore by preferencesDataStore(name = "session_prefs")

@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_IS_LOGGED_IN    = booleanPreferencesKey("is_logged_in")
        private val KEY_USER_ID         = longPreferencesKey("user_id")
        private val KEY_USER_NAME       = stringPreferencesKey("user_name")
        private val KEY_USER_EMAIL      = stringPreferencesKey("user_email")

        // ── NEW: tracks whether the user has seen LandingScreen before ────
        // Stored in DataStore so it survives app restarts
        // Only resets if the user clears app data or reinstalls — exactly
        // what "show only once on new device" means
        private val KEY_HAS_SEEN_LANDING = booleanPreferencesKey("has_seen_landing")
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
        context.dataStore.edit { prefs ->
            // Only clear login data — keep KEY_HAS_SEEN_LANDING intact
            // so landing screen never shows again after first launch
            prefs[KEY_IS_LOGGED_IN] = false
            prefs.remove(KEY_USER_ID)
            prefs.remove(KEY_USER_NAME)
            prefs.remove(KEY_USER_EMAIL)
        }
    }

    // ─── Mark landing as seen — called when user taps Get Started ─────────
    suspend fun markLandingSeen() {
        context.dataStore.edit { prefs ->
            prefs[KEY_HAS_SEEN_LANDING] = true
        }
    }

    // ─── Observe login state (used in NavGraph to auto-route) ─────────────
    val isLoggedIn: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_IS_LOGGED_IN] ?: false }

    // ─── Observe landing seen state (used in NavGraph startDestination) ───
    // false = first time ever → show LandingScreen
    // true  = already seen   → skip straight to Login
    val hasSeenLanding: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_HAS_SEEN_LANDING] ?: false }

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

