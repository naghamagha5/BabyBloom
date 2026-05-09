package com.babybloom.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babybloom.BuildConfig
import com.babybloom.domain.algorithm.AdaptiveAlgorithmEngine
import com.babybloom.domain.model.AiInsight
import com.babybloom.domain.model.ChildInsight
import com.babybloom.domain.repository.AiInsightRepository
import com.babybloom.domain.repository.ChildProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

sealed class InsightsUiState {
    object Idle    : InsightsUiState()
    object Loading : InsightsUiState()
    data class Ready(val insight: ChildInsight) : InsightsUiState()
    data class Error(val message: String)       : InsightsUiState()
}

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val childProfileRepository : ChildProfileRepository,
    private val aiInsightRepository    : AiInsightRepository,
    private val algorithmEngine        : AdaptiveAlgorithmEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow<InsightsUiState>(InsightsUiState.Idle)
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()

    // ── Load — shows cached first, refreshes if older than 24h ───────────

    fun loadInsight(childId: Long) {
        viewModelScope.launch {
            _uiState.value = InsightsUiState.Loading

            val cached = aiInsightRepository.getLatestForChild(childId)
            val oneDayMs = 24 * 60 * 60 * 1000L

            if (cached != null &&
                System.currentTimeMillis() - cached.generatedAt < oneDayMs) {
                // Show cached immediately
                _uiState.value = InsightsUiState.Ready(parseInsightText(cached.insightText))
            } else {
                // Generate fresh
                generateInsight(childId)
            }
        }
    }

    // ── Force refresh ─────────────────────────────────────────────────────

    fun generateInsight(childId: Long) {
        viewModelScope.launch {
            _uiState.value = InsightsUiState.Loading
            try {
                val profile = childProfileRepository.getByChildId(childId) ?: run {
                    _uiState.value = InsightsUiState.Error("لم يتم العثور على ملف الطفل")
                    return@launch
                }

                val payload = algorithmEngine.buildAiPayloadFromProfile(profile)
                val rawText = callGeminiApi(payload.naturalLanguagePrompt)

                // Save to DB
                aiInsightRepository.save(
                    AiInsight(
                        childId     = childId,
                        insightText = rawText,
                        generatedAt = System.currentTimeMillis()
                    )
                )

                // Clean up old ones — keep only the last 3
                aiInsightRepository.deleteOldForChild(childId, keepLatest = 3)

                _uiState.value = InsightsUiState.Ready(parseInsightText(rawText))

            } catch (e: Exception) {
                _uiState.value = InsightsUiState.Error("فشل توليد التقرير، تحقق من الإنترنت")
            }
        }
    }

    // ── Gemini API (free tier — gemini-1.5-flash) ─────────────────────────

    private suspend fun callGeminiApi(prompt: String): String =
        withContext(Dispatchers.IO) {
            val apiKey = BuildConfig.GEMINI_API_KEY
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" +
                    "gemini-1.5-flash:generateContent?key=$apiKey"

            val requestBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.7)
                    put("maxOutputTokens", 1024)
                })
            }.toString()

            val conn = URL(endpoint).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 15_000
                conn.readTimeout    = 30_000
                conn.doOutput = true
                conn.outputStream.use { it.write(requestBody.toByteArray()) }

                val responseCode = conn.responseCode
                if (responseCode != 200) {
                    val error = conn.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                    throw Exception("Gemini API error $responseCode: $error")
                }

                val response = conn.inputStream.bufferedReader().readText()
                JSONObject(response)
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
            } finally {
                conn.disconnect()
            }
        }

    // ── Parser ────────────────────────────────────────────────────────────

    private fun parseInsightText(raw: String): ChildInsight {
        val map = raw.lines()
            .filter { it.contains(":") }
            .associate { line ->
                val idx = line.indexOf(":")
                line.substring(0, idx).trim() to line.substring(idx + 1).trim()
            }
        return ChildInsight(
            learningStyle    = map["أسلوب_التعلم"]    ?: "",
            strengths        = map["نقاط_القوة"]       ?: "",
            developmentAreas = map["مجالات_التطوير"]  ?: "",
            tip1Title        = map["نصيحة_1_عنوان"]   ?: "",
            tip1Detail       = map["نصيحة_1_تفاصيل"]  ?: "",
            tip2Title        = map["نصيحة_2_عنوان"]   ?: "",
            tip2Detail       = map["نصيحة_2_تفاصيل"]  ?: "",
            tip3Title        = map["نصيحة_3_عنوان"]   ?: "",
            tip3Detail       = map["نصيحة_3_تفاصيل"]  ?: "",
            guidanceIntro    = map["إرشاد_مقدمة"]      ?: "",
            recommended      = listOfNotNull(
                map["موصى_1"], map["موصى_2"], map["موصى_3"]
            ).filter { it.isNotEmpty() },
            avoid            = listOfNotNull(
                map["تجنب_1"], map["تجنب_2"], map["تجنب_3"]
            ).filter { it.isNotEmpty() }
        )
    }
}