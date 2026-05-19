package com.babybloom.presentation.viewmodels

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babybloom.di.AppSoundSettings
import com.babybloom.domain.model.ActivityContent
import com.babybloom.util.AssetPathResolver
import com.babybloom.util.ImageAsset
import com.babybloom.util.SoundEffect
import com.babybloom.util.speech.SpeechRecognitionManager
import com.babybloom.util.speech.SpeechResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

enum class MicState { Idle, Listening, Processing }

sealed class SpeechCardState {
    object Loading : SpeechCardState()
    object Offline : SpeechCardState()
    data class Card(
        val item: ActivityContent,
        val imageAsset: ImageAsset,
        val attempts: Int = 0,
        val micState: MicState = MicState.Idle,
        val showSuccess: Boolean = false
    ) : SpeechCardState()
}

@HiltViewModel
class SpeechViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val speechRecognitionManager: SpeechRecognitionManager,
    private val appSoundSettings: AppSoundSettings
) : ViewModel() {

    private val _cardState = MutableStateFlow<SpeechCardState>(SpeechCardState.Loading)
    val cardState: StateFlow<SpeechCardState> = _cardState.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var attemptJob: Job? = null
    private var isRunning = false
    private var introPlayed = false
    private val maxAttempts = 3

    // ── Letter sound aliases ──────────────────────────────────────────────────
    // Recognizer can't reliably return a single letter sound — map all
    // plausible forms including dialect-normalized equivalents
    private val letterSoundAliases = mapOf(
        "أ" to listOf("آه", "اه", "أه", "اا", "آ", "أ", "الف", "آاه", "اأ", "ا"),
        "ب" to listOf("به", "بي", "بة", "بب", "با"),
        "ت" to listOf("ته", "تي", "تت", "تا"),
        "ث" to listOf("ثه", "ثي", "ثث", "سه", "سي"),   // ث→س in normalize
        "ج" to listOf("جيم", "جه", "جي", "جا"),
        "ح" to listOf("حه", "حي", "هه", "هي", "ها"),    // ح→ه in normalize
        "خ" to listOf("خه", "خي", "كه", "كي", "كا"),    // خ→ك in normalize
        "د" to listOf("ده", "دي", "دد", "دا"),
        "ذ" to listOf("ذه", "ذي", "زه", "زي", "زا"),    // ذ→ز in normalize
        "ر" to listOf("ره", "ري", "رر", "را"),
        "ز" to listOf("زه", "زي", "زز", "زا"),
        "س" to listOf("سه", "سي", "سس", "سا"),
        "ش" to listOf("شه", "شي", "شش", "شا"),
        "ص" to listOf("صه", "صي", "صص", "صا"),
        "ض" to listOf("ضه", "ضي", "ضض", "ضا"),
        "ط" to listOf("طه", "طي", "طط", "طا"),
        "ظ" to listOf("ظه", "ظي", "ضه", "ضي"),          // ظ→ض in normalize
        "ع" to listOf("عه", "عي", "آع", "اع", "عا"),
        "غ" to listOf("غه", "غي", "غغ", "غا"),
        "ف" to listOf("فه", "في", "فف", "فا"),
        "ق" to listOf("قه", "قي", "قق", "قا"),
        "ك" to listOf("كه", "كي", "كك", "كا"),
        "ل" to listOf("له", "لي", "لل", "لا"),
        "م" to listOf("مه", "مي", "مم", "ما"),
        "ن" to listOf("نه", "ني", "نن", "نا"),
        "ه" to listOf("هه", "هي", "اه", "هاه", "ها"),
        "و" to listOf("وه", "وو", "واو", "وا"),
        "ي" to listOf("يه", "يي", "ياء", "يا", "ية")
    )

    fun loadCard(
        item: ActivityContent,
        isCalmMode: Boolean,
        onCardComplete: (elapsedMs: Long, attempts: Int, confidence: Float?, isCorrect: Boolean) -> Unit
    ) {
        val currentCard = _cardState.value
        if (currentCard is SpeechCardState.Card
            && currentCard.item.contentId == item.contentId
            && isRunning
        ) return

        attemptJob?.cancel()
        speechRecognitionManager.cancelCurrent()
        releasePlayer()
        isRunning = true

        if (!speechRecognitionManager.isOnline()) {
            _cardState.value = SpeechCardState.Offline
            isRunning = false
            return
        }

        attemptJob = viewModelScope.launch {
            try {
                val imageAsset = AssetPathResolver.imageAssetFor(
                    item.contentId, item.category, isCalmMode
                )
                val startTime = System.currentTimeMillis()
                val audioPath = AssetPathResolver.audioPathFor(item.contentId, item.category)

                // ✅ show card immediately
                _cardState.value = SpeechCardState.Card(
                    item = item,
                    imageAsset = imageAsset,
                    micState = MicState.Idle
                )

                // ✅ play intro while card is already visible, only once per session
                if (!introPlayed) {
                    introPlayed = true
                    playAudioSuspend("activities/audio/speech/listen_and_repeat.ogg")
                }

                for (attempt in 1..maxAttempts) {

                    // ── 1. Show idle, play word audio ─────────────────────
                    _cardState.value = SpeechCardState.Card(
                        item = item, imageAsset = imageAsset,
                        attempts = attempt, micState = MicState.Idle
                    )
                    playAudioSuspend(audioPath)

                    // short delay — kids speak almost immediately after audio
                    delay(400)

                    // ── 2. Listen — 8 second hard cap ────────────────────
                    _cardState.value = SpeechCardState.Card(
                        item = item, imageAsset = imageAsset,
                        attempts = attempt, micState = MicState.Listening
                    )

                    val result = withTimeoutOrNull(8_000L) {
                        speechRecognitionManager.listenOnce()
                    }

                    // ── 3. Process ────────────────────────────────────────
                    _cardState.value = SpeechCardState.Card(
                        item = item, imageAsset = imageAsset,
                        attempts = attempt, micState = MicState.Processing
                    )
                    delay(300)

                    val activeCard = _cardState.value as? SpeechCardState.Card
                    if (activeCard?.item?.contentId != item.contentId) return@launch

                    when {
                        result == null -> appSoundSettings.playSoundEffect(SoundEffect.WRONG)

                        result is SpeechResult.Success &&
                                isMatch(result.recognizedText, item.labelAr) -> {
                            appSoundSettings.playSoundEffect(SoundEffect.CORRECT)
                            _cardState.value = SpeechCardState.Card(
                                item = item, imageAsset = imageAsset,
                                attempts = attempt, micState = MicState.Idle,
                                showSuccess = true
                            )
                            delay(1_500)
                            onCardComplete(
                                System.currentTimeMillis() - startTime,
                                attempt,
                                result.confidence,
                                true
                            )
                            return@launch
                        }

                        result is SpeechResult.Offline -> {
                            _cardState.value = SpeechCardState.Offline
                            return@launch
                        }

                        else -> appSoundSettings.playSoundEffect(SoundEffect.WRONG)
                    }
                }

                // ── All 3 attempts exhausted ──────────────────────────────
                onCardComplete(
                    System.currentTimeMillis() - startTime,
                    maxAttempts,
                    null,
                    false
                )

            } finally {
                isRunning = false
            }
        }
    }

    // ── Audio suspending ──────────────────────────────────────────────────────

    private suspend fun playAudioSuspend(path: String) =
        suspendCancellableCoroutine<Unit> { cont ->
            try {
                releasePlayer()
                mediaPlayer = MediaPlayer()
                @Suppress("DEPRECATION")
                mediaPlayer?.setAudioStreamType(AudioManager.STREAM_MUSIC)
                val afd = context.assets.openFd(path)
                mediaPlayer?.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                mediaPlayer?.setOnCompletionListener {
                    if (cont.isActive) cont.resume(Unit) {}
                }
                mediaPlayer?.setOnErrorListener { _, _, _ ->
                    if (cont.isActive) cont.resume(Unit) {}
                    true
                }
                mediaPlayer?.prepare()
                mediaPlayer?.start()
                cont.invokeOnCancellation { releasePlayer() }
            } catch (e: Exception) {
                Log.w("SpeechViewModel", "Audio not found: $path")
                if (cont.isActive) cont.resume(Unit) {}
            }
        }

    // ── Matching ──────────────────────────────────────────────────────────────

    private fun isMatch(recognized: String, expected: String): Boolean {
        val n1 = normalize(recognized)
        val n2 = normalize(expected)
        if (n1.isEmpty() || n2.isEmpty()) return false

        // ── direct / contains check ───────────────────────────────────────
        if (n1.contains(n2) || n2.contains(n1)) return true

        // ── letter sound alias check ──────────────────────────────────────
        // strip ONLY diacritics to get base letter for map lookup
        // "أَ" → "أ", "بَ" → "ب", "حَ" → "ح" etc.
        val baseExpected = expected.trim()
            .replace(Regex("[\u064B-\u065F\u0670]"), "")
        val aliases = letterSoundAliases[baseExpected]
        if (aliases != null) {
            if (aliases.any { alias ->
                    val normAlias = normalize(alias)
                    n1.contains(normAlias) ||
                            normAlias.contains(n1) ||
                            similarity(n1, normAlias) >= 0.8f
                }) return true
        }

        // ── levenshtein fallback ──────────────────────────────────────────
        return similarity(n1, n2) >= 0.8f
    }

    private fun normalize(text: String): String {
        val result = text
            .replace(Regex("[\u064B-\u065F\u0670]"), "") // strip diacritics
            .replace('\u0640', ' ')                        // tatweel
            .replace(Regex("[أإآ]"), "ا")                 // alef variants → bare alef
            .replace('ة', 'ه')                             // ta marbuta
            .replace('ى', 'ي')                             // alef maqsura
            .replace('ؤ', 'و')                             // waw with hamza
            .replace('ئ', 'ي')                             // ya with hamza
            .replace('ح', 'ه')                             // ح → ه dialect
            .replace('خ', 'ك')                             // خ → ك dialect
            .replace('ذ', 'ز')                             // ذ → ز dialect
            .replace('ث', 'س')                             // ث → س dialect
            .replace('ظ', 'ض')                             // ظ → ض dialect
            .replace(Regex("(.)\\1+"), "$1")               // collapse doubled letters
            .trim()

        // ✅ only strip medial alef from multi-character words
        // single alef (letter sound "أ") must survive normalization
        return if (result.length > 1) result.replace("ا", "") else result
    }

    private fun similarity(a: String, b: String): Float {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) dp[i - 1][j - 1]
                else minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1]) + 1
            }
        }
        val maxLen = maxOf(a.length, b.length)
        return if (maxLen == 0) 1f else 1f - dp[a.length][b.length].toFloat() / maxLen
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    private fun releasePlayer() {
        try { mediaPlayer?.stop() } catch (_: Exception) {}
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun onCleared() {
        super.onCleared()
        attemptJob?.cancel()
        speechRecognitionManager.cancelCurrent()
        releasePlayer()
    }
}
