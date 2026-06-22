package com.babybloom.domain.insight

import com.babybloom.BuildConfig
import com.babybloom.domain.model.ChildInsightContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiInsightGenerator @Inject constructor() {
    suspend fun generate(context: ChildInsightContext): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        require(apiKey.isNotBlank()) { "Gemini API key is missing" }

        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" +
            "${BuildConfig.GEMINI_MODEL}:generateContent?key=$apiKey"
        val prompt = """
            أنت مساعد تعليمي يكتب للوالدين عن طفل عمره من 3 إلى 5 سنوات.
            استخدم بيانات Baby Bloom فقط، ولا تخترع قياسات أو أسباباً غير موجودة.
            اذكر بوضوح عندما تكون البيانات قليلة، ولا تقدم تشخيصاً طبياً أو نفسياً.
            حوّل الاتجاهات والأدلة إلى إرشادات عربية بسيطة، عملية، وداعمة.
            لا تذكر معرفات قاعدة البيانات أو القيم التقنية الخام في النص النهائي.
            اجعل كل حقل نصي جملة واحدة قصيرة لا تتجاوز 35 كلمة.
            اجعل عناوين النصائح من 3 إلى 5 كلمات، وكل عنصر في القوائم جملة قصيرة.

            بيانات الطفل المجمعة والمتحقق منها:
            ${context.json}
        """.trimIndent()

        val responseSchema = JSONObject().apply {
            put("type", "OBJECT")
            put("required", JSONArray(listOf(
                "learningStyle", "strengths", "development", "tip1Title", "tip1Body",
                "tip2Title", "tip2Body", "guidanceIntro", "recommended", "avoid"
            )))
            put("properties", JSONObject().apply {
                put("learningStyle", stringSchema())
                put("strengths", stringSchema())
                put("development", stringSchema())
                put("tip1Title", stringSchema())
                put("tip1Body", stringSchema())
                put("tip2Title", stringSchema())
                put("tip2Body", stringSchema())
                put("guidanceIntro", stringSchema())
                put("recommended", stringArraySchema())
                put("avoid", stringArraySchema())
            })
        }

        try {
            requestInsight(endpoint, prompt, responseSchema, maxOutputTokens = 4096)
        } catch (exception: AiInsightGenerationException.Truncated) {
            requestInsight(endpoint, prompt, responseSchema, maxOutputTokens = 8192)
        } catch (exception: AiInsightGenerationException.InvalidResponse) {
            requestInsight(endpoint, prompt, responseSchema, maxOutputTokens = 8192)
        }
    }

    private fun requestInsight(
        endpoint: String,
        prompt: String,
        responseSchema: JSONObject,
        maxOutputTokens: Int
    ): String {
        val requestBody = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            }))
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.25)
                put("maxOutputTokens", maxOutputTokens)
                put("responseMimeType", "application/json")
                put("responseSchema", responseSchema)
                put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
            })
        }.toString()

        val connection = URL(endpoint).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.connectTimeout = 15_000
            connection.readTimeout = 45_000
            connection.doOutput = true
            connection.outputStream.use { it.write(requestBody.toByteArray(Charsets.UTF_8)) }

            if (connection.responseCode !in 200..299) {
                val error = connection.errorStream?.bufferedReader()?.readText().orEmpty()
                throw AiInsightGenerationException.Api(
                    statusCode = connection.responseCode,
                    details = extractApiError(error)
                )
            }

            val rawResponse = connection.inputStream.bufferedReader().readText()
            try {
                val response = JSONObject(rawResponse)
                val candidate = response
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                val finishReason = candidate.optString("finishReason")
                if (finishReason == "MAX_TOKENS") {
                    throw AiInsightGenerationException.Truncated(maxOutputTokens)
                }
                val generatedJson = candidate
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                JSONObject(generatedJson).toString()
            } catch (exception: AiInsightGenerationException) {
                throw exception
            } catch (exception: Exception) {
                throw AiInsightGenerationException.InvalidResponse(exception.message.orEmpty())
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun stringSchema() = JSONObject().put("type", "STRING")

    private fun stringArraySchema() = JSONObject().apply {
        put("type", "ARRAY")
        put("minItems", 2)
        put("maxItems", 3)
        put("items", stringSchema())
    }

    private fun extractApiError(rawError: String): String = runCatching {
        JSONObject(rawError).getJSONObject("error").optString("message")
    }.getOrDefault(rawError).take(300)
}

sealed class AiInsightGenerationException(message: String) : Exception(message) {
    class Api(val statusCode: Int, details: String) :
        AiInsightGenerationException("Gemini API $statusCode: $details")

    class InvalidResponse(details: String) :
        AiInsightGenerationException("Invalid Gemini response: $details")

    class Truncated(maxOutputTokens: Int) :
        AiInsightGenerationException("Gemini response reached the $maxOutputTokens token limit")
}
