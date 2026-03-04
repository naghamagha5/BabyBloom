package com.babybloom.presentation.viewmodels

import android.content.Context
import android.media.SoundPool
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babybloom.R
import com.babybloom.di.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class ParentViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // ── User data ────────────────────────────────────────────────────────────
    val userName: StateFlow<String> = sessionManager.userName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "")

    val userEmail: StateFlow<String> = sessionManager.userEmail
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), "")

    val createdAt: StateFlow<Long> = sessionManager.createdAt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 0L)

    // ── Greeting ─────────────────────────────────────────────────────────────
    val greeting: String
        get() {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            return when {
                hour < 12 -> "صباح الخير"
                hour < 17 -> "مساء الخير"
                else      -> "مساء النور"
            }
        }

    // ── Settings toggles ─────────────────────────────────────────────────────
    val notificationsEnabled = MutableStateFlow(true)
    val soundEnabled = MutableStateFlow(true)
    val musicEnabled = MutableStateFlow(true)

    fun toggleNotifications(enabled: Boolean) {
        notificationsEnabled.value = enabled
        playButtonSound()
    }

    fun toggleSound(enabled: Boolean) {
        // play sound BEFORE disabling so user hears the last click
        playButtonSound()
        soundEnabled.value = enabled
    }

    fun toggleMusic(enabled: Boolean) {
        musicEnabled.value = enabled
        playButtonSound()
    }

    // ── Sound pool ───────────────────────────────────────────────────────────
    private val soundPool = SoundPool.Builder().setMaxStreams(3).build()
    private var buttonSoundId: Int = 0
    private var soundLoaded = false

    init {
        soundPool.setOnLoadCompleteListener { _, _, status ->
            soundLoaded = status == 0
        }
        buttonSoundId = soundPool.load(context, R.raw.button_one, 1)
    }

    fun playButtonSound() {
        if (soundEnabled.value && soundLoaded) {
            soundPool.play(buttonSoundId, 1f, 1f, 0, 0, 1f)
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
    fun formatJoinDate(timestamp: Long): String {
        val time = if (timestamp == 0L) System.currentTimeMillis() else timestamp
        val sdf = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.ENGLISH)
        return sdf.format(java.util.Date(time))
    }

    override fun onCleared() {
        super.onCleared()
        soundPool.release()
    }
}