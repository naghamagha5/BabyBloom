package com.babybloom.domain.model

/**
 * Structured form of [AiInsight.insightText].
 * The raw text uses a simple "key: value" format (one per line) so it can be
 * stored as a plain string in Room and parsed here without schema changes.
 * When you switch to a real AI API, keep the same key names in the prompt and
 * this parser will keep working.
 */
data class ParsedInsight(
    val learningStyle: String = "",
    val strengths: String = "",
    val development: String = "",
    val tip1Title: String = "",
    val tip1Body: String = "",
    val tip2Title: String = "",
    val tip2Body: String = "",
    val guidanceIntro: String = "",
    val recommended: List<String> = emptyList(),
    val avoid: List<String> = emptyList()
) {
    companion object {
        fun from(insightText: String): ParsedInsight {
            val map = insightText.lines()
                .filter { it.contains(':') }
                .associate { line ->
                    val idx = line.indexOf(':')
                    line.substring(0, idx).trim() to line.substring(idx + 1).trim()
                }

            return ParsedInsight(
                learningStyle = map["أسلوب_التعلم"] ?: "",
                strengths     = map["نقاط_القوة"]   ?: "",
                development   = map["مجالات_التطوير"] ?: "",
                tip1Title     = map["نصيحة_1_عنوان"]  ?: "",
                tip1Body      = map["نصيحة_1_تفاصيل"] ?: "",
                tip2Title     = map["نصيحة_2_عنوان"]  ?: "",
                tip2Body      = map["نصيحة_2_تفاصيل"] ?: "",
                guidanceIntro = map["إرشاد_مقدمة"]    ?: "",
                recommended   = (1..5).mapNotNull { map["موصى_$it"] }.filter { it.isNotEmpty() },
                avoid         = (1..5).mapNotNull { map["تجنب_$it"] }.filter { it.isNotEmpty() }
            )
        }
    }
}