package com.babybloom.fake

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.babybloom.util.speech.SpeechRecognitionService
import com.babybloom.util.speech.SpeechResult

// test/java/com/babybloom/fake/FakeSpeechRecognitionService.kt

class FakeSpeechRecognitionService(
    private val online: Boolean = true,
    private val results: List<SpeechResult> = emptyList()
) : SpeechRecognitionService {

    override fun isOnline() = online

    override fun startListening(): Flow<SpeechResult> = flow {
        if (!online) {
            emit(SpeechResult.Offline)
            return@flow
        }
        results.forEach { emit(it) }
    }
}