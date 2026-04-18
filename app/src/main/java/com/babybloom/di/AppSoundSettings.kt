package com.babybloom.di

import android.content.Context
import android.media.MediaPlayer
import android.media.SoundPool
import android.util.Log
import com.babybloom.util.AssetPathResolver
import com.babybloom.util.SoundEffect
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppSoundSettings @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val soundEnabled = MutableStateFlow(true)
    
    private var musicPlayer: MediaPlayer? = null
    private var sfxPool: SoundPool? = null
    private val sfxMap = mutableMapOf<SoundEffect, Int>()

    // Called when entering activity shell — pass child settings
    fun startSession(
        backgroundMusicEnabled: Boolean,
        soundEffectsEnabled: Boolean
    ) {
        if (backgroundMusicEnabled) startBackgroundMusic()
        if (soundEffectsEnabled) loadSoundEffects()
    }

    fun stopSession() {
        musicPlayer?.stop()
        musicPlayer?.release()
        musicPlayer = null
        sfxPool?.release()
        sfxPool = null
        sfxMap.clear()
    }

    fun playSoundEffect(effect: SoundEffect) {
        if (!soundEnabled.value) return
        val soundId = sfxMap[effect] ?: return
        sfxPool?.play(soundId, 1f, 1f, 1, 0, 1f)
    }

    private fun startBackgroundMusic() {
        try {
            val afd = context.assets.openFd(AssetPathResolver.backgroundMusicPath())
            musicPlayer = MediaPlayer().apply {
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                isLooping = true
                setVolume(0.3f, 0.3f)   // subtle background level
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.w("AppSoundSettings", "Background music not found", e)
        }
    }

    private fun loadSoundEffects() {
        sfxPool = SoundPool.Builder()
            .setMaxStreams(3)
            .build()
        SoundEffect.entries.forEach { effect ->
            try {
                val afd = context.assets.openFd(AssetPathResolver.soundEffectPath(effect))
                val id = sfxPool!!.load(afd, 1)
                sfxMap[effect] = id
            } catch (e: Exception) {
                Log.w("AppSoundSettings", "Sound effect not found: ${effect.fileName}", e)
            }
        }
    }
}
