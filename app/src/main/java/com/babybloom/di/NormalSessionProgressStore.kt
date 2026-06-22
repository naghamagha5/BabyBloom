package com.babybloom.di

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.normalSessionDataStore by preferencesDataStore(name = "normal_session_progress")

data class NormalSessionProgress(
    val childId: Long,
    val sessionId: Long,
    val encodedQueue: String,
    val stepIndex: Int,
    val remainingMs: Long? = null
)

@Singleton
class NormalSessionProgressStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        val KEY_CHILD_ID = longPreferencesKey("child_id")
        val KEY_SESSION_ID = longPreferencesKey("session_id")
        val KEY_QUEUE = stringPreferencesKey("queue")
        val KEY_STEP_INDEX = intPreferencesKey("step_index")
        val KEY_REMAINING_MS = longPreferencesKey("remaining_ms")
    }

    suspend fun save(progress: NormalSessionProgress) {
        context.normalSessionDataStore.edit { prefs ->
            prefs[KEY_CHILD_ID] = progress.childId
            prefs[KEY_SESSION_ID] = progress.sessionId
            prefs[KEY_QUEUE] = progress.encodedQueue
            prefs[KEY_STEP_INDEX] = progress.stepIndex
            progress.remainingMs?.let { prefs[KEY_REMAINING_MS] = it }
        }
    }

    suspend fun getForChild(childId: Long): NormalSessionProgress? {
        val prefs = context.normalSessionDataStore.data.first()
        if (prefs[KEY_CHILD_ID] != childId) return null

        val sessionId = prefs[KEY_SESSION_ID] ?: return null
        val queue = prefs[KEY_QUEUE]?.takeIf { it.isNotBlank() } ?: return null
        val stepIndex = prefs[KEY_STEP_INDEX] ?: 0
        val remainingMs = prefs[KEY_REMAINING_MS]

        return NormalSessionProgress(
            childId = childId,
            sessionId = sessionId,
            encodedQueue = queue,
            stepIndex = stepIndex,
            remainingMs = remainingMs
        )
    }

    suspend fun clear() {
        context.normalSessionDataStore.edit { it.clear() }
    }
}
