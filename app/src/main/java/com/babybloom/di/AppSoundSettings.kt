package com.babybloom.di

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.util.Log
import com.babybloom.util.AssetPathResolver
import com.babybloom.util.SoundEffect
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AppSoundSettings"

@Singleton
class AppSoundSettings @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val soundEnabled = MutableStateFlow(true)

    private var musicPlayer: MediaPlayer? = null
    private var sfxPool: SoundPool? = null

    // Maps SoundEffect → SoundPool stream ID (0 = not yet loaded)
    private val sfxMap      = mutableMapOf<SoundEffect, Int>()
    // Tracks which stream IDs have finished loading and are ready to play
    private val loadedIds   = mutableSetOf<Int>()
    // Queued calls that arrived before the sound finished loading
    private val pendingPlay = mutableListOf<SoundEffect>()

    // ── Session lifecycle ─────────────────────────────────────────────────────

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
        loadedIds.clear()
        pendingPlay.clear()
    }

    // ── Play ──────────────────────────────────────────────────────────────────

    fun playSoundEffect(effect: SoundEffect) {
        if (!soundEnabled.value) return
        val pool = sfxPool ?: return          // session not started yet

        val streamId = sfxMap[effect]
        when {
            streamId == null -> {
                // Effect wasn't loaded (missing file) — ignore silently
                Log.w(TAG, "SoundEffect not in map: $effect")
            }
            streamId !in loadedIds -> {
                // Still loading — queue it; will be played in onLoadComplete
                if (!pendingPlay.contains(effect)) pendingPlay.add(effect)
                Log.d(TAG, "Queued $effect (not loaded yet)")
            }
            else -> {
                pool.play(streamId, 1f, 1f, 1, 0, 1f)
            }
        }
    }

    // ── Background music ──────────────────────────────────────────────────────

    private fun startBackgroundMusic() {
        try {
            val afd = context.assets.openFd(AssetPathResolver.backgroundMusicPath())
            musicPlayer = MediaPlayer().apply {
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                isLooping = true
                setVolume(0.3f, 0.3f)
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Background music not found", e)
        }
    }

    // ── Sound effects loading ─────────────────────────────────────────────────

    private fun loadSoundEffects() {
        // Release any previous pool first
        sfxPool?.release()
        sfxMap.clear()
        loadedIds.clear()
        pendingPlay.clear()

        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val pool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(audioAttrs)
            .build()

        // Register load-complete listener BEFORE loading any sounds
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                // Sound is ready
                loadedIds.add(sampleId)
                Log.d(TAG, "Sound loaded: sampleId=$sampleId")

                // Drain the pending queue for any effects that are now ready
                val iter = pendingPlay.iterator()
                while (iter.hasNext()) {
                    val effect = iter.next()
                    val id = sfxMap[effect]
                    if (id != null && id in loadedIds) {
                        pool.play(id, 1f, 1f, 1, 0, 1f)
                        iter.remove()
                        Log.d(TAG, "Played queued $effect")
                    }
                }
            } else {
                Log.w(TAG, "SoundPool load failed for sampleId=$sampleId status=$status")
            }
        }

        // Load every effect; store the returned stream ID
        SoundEffect.entries.forEach { effect ->
            try {
                val afd = context.assets.openFd(AssetPathResolver.soundEffectPath(effect))
                val id  = pool.load(afd, 1)   // returns immediately; loading is async
                if (id > 0) {
                    sfxMap[effect] = id
                    Log.d(TAG, "Queued load for $effect → id=$id")
                } else {
                    Log.w(TAG, "pool.load() returned 0 for ${effect.fileName}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Sound effect file not found: ${effect.fileName}", e)
            }
        }

        sfxPool = pool
    }
}