package com.babybloom.speech

import android.speech.SpeechRecognizer
import com.babybloom.fake.FakeSpeechRecognitionService
import com.babybloom.util.speech.SpeechResult
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SpeechRecognitionTest {

    @Test
    fun `emits Offline when there is no internet`() = runTest {
        val service = FakeSpeechRecognitionService(online = false)

        val results = service.startListening().toList()

        assertEquals(listOf(SpeechResult.Offline), results)
    }

    @Test
    fun `emits Listening then Success with recognized text`() = runTest {
        val service = FakeSpeechRecognitionService(
            online = true,
            results = listOf(
                SpeechResult.Listening,
                SpeechResult.Success(recognizedText = "كلب", confidence = 0.95f)
            )
        )

        val results = service.startListening().toList()

        assertEquals(2, results.size)
        assertEquals(SpeechResult.Listening, results[0])
        assertEquals(SpeechResult.Success("كلب", 0.95f), results[1])
    }

    @Test
    fun `emits Error with the correct error code`() = runTest {
        val service = FakeSpeechRecognitionService(
            online = true,
            results = listOf(SpeechResult.Error(SpeechRecognizer.ERROR_NO_MATCH))
        )

        val result = service.startListening().first()

        assertTrue(result is SpeechResult.Error)
        assertEquals(SpeechRecognizer.ERROR_NO_MATCH, (result as SpeechResult.Error).errorCode)
    }

    @Test
    fun `isOnline returns false when offline`() {
        val service = FakeSpeechRecognitionService(online = false)
        assertFalse(service.isOnline())
    }
}
