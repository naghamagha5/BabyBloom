package com.babybloom.util.speech

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton

sealed class SpeechResult {
    data class Success(val recognizedText: String, val confidence: Float) : SpeechResult()
    data class Error(val errorCode: Int) : SpeechResult()
    object Offline : SpeechResult()
}

@Singleton
class SpeechRecognitionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var activeRecognizer: SpeechRecognizer? = null

    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.activeNetwork?.let {
            cm.getNetworkCapabilities(it)
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } ?: false
    }

    fun cancelCurrent() {
        activeRecognizer?.cancel()
        activeRecognizer?.destroy()
        activeRecognizer = null
    }

    suspend fun listenOnce(): SpeechResult = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            cancelCurrent()
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            activeRecognizer = recognizer

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar")
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
                // ✅ very low minimum — catches even the fastest short word
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 150L)
                // ✅ short possibly-complete — triggers processing quickly after a fast word ends
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1_000L)
                // ✅ longer complete — gives the engine time to finish processing before giving up
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5_000L)
            }

            var settled = false
            fun settle(result: SpeechResult) {
                if (settled) return
                settled = true
                if (activeRecognizer == recognizer) activeRecognizer = null
                recognizer.destroy()
                if (cont.isActive) cont.resume(result)
            }

            cont.invokeOnCancellation {
                if (activeRecognizer == recognizer) activeRecognizer = null
                recognizer.cancel()
                recognizer.destroy()
            }

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val allMatches = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val scores = results
                        ?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                    val text       = allMatches?.firstOrNull() ?: ""
                    val confidence = scores?.firstOrNull() ?: 0f
                    settle(SpeechResult.Success(text, confidence))
                }
                override fun onError(error: Int)                        { settle(SpeechResult.Error(error)) }
                override fun onReadyForSpeech(params: Bundle?)          {}
                override fun onBeginningOfSpeech()                      {}
                override fun onEndOfSpeech()                            {}
                override fun onPartialResults(partialResults: Bundle?)  {}
                override fun onRmsChanged(rmsdB: Float)                 {}
                override fun onBufferReceived(buffer: ByteArray?)       {}
                override fun onEvent(eventType: Int, params: Bundle?)   {}
            })

            recognizer.startListening(intent)
        }
    }
}
