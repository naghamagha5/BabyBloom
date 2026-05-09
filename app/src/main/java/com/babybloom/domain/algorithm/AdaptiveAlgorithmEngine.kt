package com.babybloom.domain.algorithm

import com.babybloom.domain.model.ActivitySignal
import com.babybloom.domain.model.ActivityLaunchStep
import com.babybloom.domain.model.AiInsightPayload
import com.babybloom.domain.model.AlgorithmOutput
import com.babybloom.domain.model.ChildProfile
import com.babybloom.domain.model.SessionDecision
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdaptiveAlgorithmEngine @Inject constructor() {

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Main entry point. Call this after every completed activity.
     * Returns the full updated profile + next-step decisions.
     */
    fun processActivityResult(
        signal: ActivitySignal,
        currentProfile: ChildProfile
    ): AlgorithmOutput {
        val itemScore = computeItemScore(signal)

        val (langLevel, numerLevel, motorLevel,
            langProgress, numerProgress, motorProgress) =
            updateSkillLevels(signal, itemScore, currentProfile)

        val (newVisual, newAudio, newGame) =
            updateModalityScores(signal, itemScore, currentProfile)

        val dominantModality = computeDominantModality(newVisual, newAudio, newGame)

        val weakSkills = computeWeakSkills(langProgress, numerProgress, motorProgress)

        val shouldRepeat = itemScore < AlgorithmWeights.REPEAT_THRESHOLD

        val currentLevelProgress = when (signal.skillArea) {
            "LANGUAGE" -> langProgress
            "NUMERACY" -> numerProgress
            else       -> motorProgress
        }
        val nextDifficulty = computeNextDifficulty(
            signal.difficultyLevel, currentLevelProgress, shouldRepeat
        )

        val updatedProfile = currentProfile.copy(
            visualScore              = newVisual,
            audioScore               = newAudio,
            gameScore                = newGame,
            languageLevel            = langLevel,
            numeracyLevel            = numerLevel,
            motorLevel               = motorLevel,
            languageProgress         = langProgress,
            numeracyProgress         = numerProgress,
            motorProgress            = motorProgress,
            dominantModality         = dominantModality,
            weakSkillAreas           = weakSkills,
            totalActivitiesCompleted = currentProfile.totalActivitiesCompleted + 1,
            lastUpdated              = System.currentTimeMillis()
        )

        return AlgorithmOutput(
            updatedProfile          = updatedProfile,
            shouldRepeat            = shouldRepeat,
            nextDifficultyLevel     = nextDifficulty,
            nextSkillAreaSuggestion = pickNextSkillArea(updatedProfile),
            nextModalitySuggestion  = dominantModality,
            aiInsightPayload        = buildAiPayload(updatedProfile, signal, itemScore)
        )
    }

    /**
     * Bootstraps a fresh ChildProfile from assessment signals.
     * Call this once after the initial assessment session completes.
     */
    fun bootstrapProfileFromAssessment(
        childId: Long,
        signals: List<ActivitySignal>
    ): ChildProfile {
        val bySkill = signals.groupBy { it.skillArea }

        fun assessSkill(area: String): Pair<Int, Float> {
            val areaSignals = bySkill[area] ?: return 1 to 0.3f
            val avg = areaSignals.map { computeItemScore(it) }.average().toFloat()
            val level = when {
                avg >= 0.80f -> 3
                avg >= 0.55f -> 2
                else         -> 1
            }
            return level to avg
        }

        val (langLevel,  langProg)  = assessSkill("LANGUAGE")
        val (numerLevel, numerProg) = assessSkill("NUMERACY")
        val (motorLevel, motorProg) = assessSkill("MOTOR")

        val modalityScores = mutableMapOf("VISUAL" to 0f, "AUDIO" to 0f, "INTERACTIVE" to 0f)
        signals.forEach { sig ->
            val score = computeItemScore(sig)
            AlgorithmWeights.ACTIVITY_MODALITY_WEIGHTS[sig.activityType]?.forEach { (mod, w) ->
                modalityScores[mod] = (modalityScores[mod] ?: 0f) + score * w
            }
        }

        val dominant = modalityScores.maxByOrNull { it.value }?.key ?: "VISUAL"
        val weakSkills = computeWeakSkills(langProg, numerProg, motorProg)

        return ChildProfile(
            childId                  = childId,
            visualScore              = (modalityScores["VISUAL"] ?: 0.5f).coerceIn(0f, 1f),
            audioScore               = (modalityScores["AUDIO"]  ?: 0.5f).coerceIn(0f, 1f),
            gameScore                = (modalityScores["INTERACTIVE"] ?: 0.5f).coerceIn(0f, 1f),
            languageLevel            = langLevel,
            numeracyLevel            = numerLevel,
            motorLevel               = motorLevel,
            languageProgress         = langProg,
            numeracyProgress         = numerProg,
            motorProgress            = motorProg,
            dominantModality         = dominant,
            weakSkillAreas           = weakSkills,
            totalActivitiesCompleted = signals.size,
            assessmentCompleted      = true
        )
    }

    /**
     * Decides whether to repeat the current activity or advance to next.
     */
    fun resolveSessionDecision(
        output: AlgorithmOutput,
        currentActivityId: String,
        queue: List<String>
    ): SessionDecision {
        if (output.shouldRepeat) return SessionDecision.Repeat(currentActivityId)
        val next = queue.firstOrNull { it != currentActivityId }
        return if (next != null) SessionDecision.Next(next)
        else SessionDecision.SessionComplete
    }

    fun resolveSessionDecision(
        output: AlgorithmOutput,
        currentStep: ActivityLaunchStep,
        queue: List<ActivityLaunchStep>,
        currentIndex: Int
    ): SessionDecision {
        if (output.shouldRepeat) {
            return SessionDecision.Repeat(
                activityId = currentStep.activityId,
                contentId = currentStep.contentId
            )
        }

        val nextStep = queue.getOrNull(currentIndex + 1)
        return if (nextStep != null) {
            SessionDecision.Next(
                activityId = nextStep.activityId,
                contentId = nextStep.contentId
            )
        } else {
            SessionDecision.SessionComplete
        }
    }

    /**
     * Builds the AI insight payload from a profile snapshot.
     * Call this whenever the parent opens the Insights tab.
     */
    fun buildAiPayloadFromProfile(profile: ChildProfile): AiInsightPayload =
        buildAiPayload(profile, null, null)

    // ── Score Computation ──────────────────────────────────────────────────

    fun computeItemScore(signal: ActivitySignal): Float {
        val total = (signal.correctCount + signal.incorrectCount).coerceAtLeast(1)
        val correctness = signal.correctCount.toFloat() / total

        val speedRatio = (AlgorithmWeights.EMA_ALPHA * 60_000f /
                signal.durationMs.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)

        val attention = signal.attentionScore ?: 0.5f

        val attemptsScore = (1f / signal.attempts.coerceAtLeast(1)).coerceIn(0f, 1f)

        var base =
            correctness  * AlgorithmWeights.WEIGHT_CORRECTNESS  +
                    speedRatio   * AlgorithmWeights.WEIGHT_SPEED         +
                    attention    * AlgorithmWeights.WEIGHT_ATTENTION     +
                    attemptsScore * AlgorithmWeights.WEIGHT_ATTEMPTS_INV

        // Speech confidence blend
        signal.speechConfidence?.let { conf ->
            base = base * (1f - AlgorithmWeights.SPEECH_CONFIDENCE_WEIGHT) +
                    conf  * AlgorithmWeights.SPEECH_CONFIDENCE_WEIGHT
        }

        // Touch complexity bonus
        signal.touchComplexity?.let { tc ->
            base += tc * AlgorithmWeights.TOUCH_COMPLEXITY_BONUS_MAX
        }

        return base.coerceIn(0f, 1f)
    }

    // ── Internal Helpers ───────────────────────────────────────────────────

    private fun emaUpdate(current: Float, observation: Float): Float =
        AlgorithmWeights.EMA_ALPHA * observation + (1f - AlgorithmWeights.EMA_ALPHA) * current

    private data class SkillState(
        val langLevel: Int,   val numerLevel: Int,  val motorLevel: Int,
        val langProg: Float,  val numerProg: Float, val motorProg: Float
    )

    private fun updateSkillLevels(
        signal: ActivitySignal,
        itemScore: Float,
        profile: ChildProfile
    ): SkillState {
        fun resolveLevel(current: Int, progress: Float): Int {
            if (profile.totalActivitiesCompleted < AlgorithmWeights.MIN_ACTIVITIES_FOR_LEVEL_CHANGE)
                return current
            return when {
                progress >= AlgorithmWeights.LEVEL_UP_THRESHOLD   && current < 5 -> current + 1
                progress <= AlgorithmWeights.LEVEL_DOWN_THRESHOLD && current > 1 -> current - 1
                else -> current
            }
        }

        return when (signal.skillArea) {
            "LANGUAGE" -> {
                val newProg  = emaUpdate(profile.languageProgress, itemScore)
                val newLevel = resolveLevel(profile.languageLevel, newProg)
                val finalProg = if (newLevel != profile.languageLevel) 0f else newProg
                SkillState(newLevel, profile.numeracyLevel, profile.motorLevel,
                    finalProg, profile.numeracyProgress, profile.motorProgress)
            }
            "NUMERACY" -> {
                val newProg  = emaUpdate(profile.numeracyProgress, itemScore)
                val newLevel = resolveLevel(profile.numeracyLevel, newProg)
                val finalProg = if (newLevel != profile.numeracyLevel) 0f else newProg
                SkillState(profile.languageLevel, newLevel, profile.motorLevel,
                    profile.languageProgress, finalProg, profile.motorProgress)
            }
            "MOTOR" -> {
                val newProg  = emaUpdate(profile.motorProgress, itemScore)
                val newLevel = resolveLevel(profile.motorLevel, newProg)
                val finalProg = if (newLevel != profile.motorLevel) 0f else newProg
                SkillState(profile.languageLevel, profile.numeracyLevel, newLevel,
                    profile.languageProgress, profile.numeracyProgress, finalProg)
            }
            else -> SkillState(
                profile.languageLevel, profile.numeracyLevel, profile.motorLevel,
                profile.languageProgress, profile.numeracyProgress, profile.motorProgress
            )
        }
    }

    private fun updateModalityScores(
        signal: ActivitySignal,
        itemScore: Float,
        profile: ChildProfile
    ): Triple<Float, Float, Float> {
        val weights = AlgorithmWeights.ACTIVITY_MODALITY_WEIGHTS[signal.activityType]
            ?: mapOf(signal.modality to 1.0f)

        var newVisual = profile.visualScore
        var newAudio  = profile.audioScore
        var newGame   = profile.gameScore

        weights["VISUAL"]?.let       { w -> newVisual = emaUpdate(profile.visualScore, itemScore * w) }
        weights["AUDIO"]?.let        { w -> newAudio  = emaUpdate(profile.audioScore,  itemScore * w) }
        weights["INTERACTIVE"]?.let  { w -> newGame   = emaUpdate(profile.gameScore,   itemScore * w) }

        return Triple(
            newVisual.coerceIn(0f, 1f),
            newAudio.coerceIn(0f, 1f),
            newGame.coerceIn(0f, 1f)
        )
    }

    private fun computeDominantModality(v: Float, a: Float, g: Float) = when {
        v >= a && v >= g -> "VISUAL"
        a >= v && a >= g -> "AUDIO"
        else             -> "INTERACTIVE"
    }

    private fun computeWeakSkills(lang: Float, numer: Float, motor: Float): String =
        buildList {
            if (lang  < AlgorithmWeights.WEAK_SKILL_THRESHOLD) add("LANGUAGE")
            if (numer < AlgorithmWeights.WEAK_SKILL_THRESHOLD) add("NUMERACY")
            if (motor < AlgorithmWeights.WEAK_SKILL_THRESHOLD) add("MOTOR")
        }.joinToString(",")

    private fun computeNextDifficulty(
        lastDifficulty: Int,
        progress: Float,
        shouldRepeat: Boolean
    ): Int {
        if (shouldRepeat) return lastDifficulty
        return when {
            progress >= AlgorithmWeights.LEVEL_UP_THRESHOLD   -> (lastDifficulty + 1).coerceAtMost(5)
            progress <= AlgorithmWeights.LEVEL_DOWN_THRESHOLD -> (lastDifficulty - 1).coerceAtLeast(1)
            else -> lastDifficulty
        }
    }

    private fun pickNextSkillArea(profile: ChildProfile): String {
        val weak = profile.weakSkillList
        return when {
            weak.contains("LANGUAGE") -> "LANGUAGE"
            weak.contains("NUMERACY") -> "NUMERACY"
            weak.contains("MOTOR")    -> "MOTOR"
            else -> {
                mapOf(
                    "LANGUAGE" to profile.languageLevel,
                    "NUMERACY" to profile.numeracyLevel,
                    "MOTOR"    to profile.motorLevel
                ).minByOrNull { it.value }?.key ?: "LANGUAGE"
            }
        }
    }

    private fun buildAiPayload(
        profile: ChildProfile,
        lastSignal: ActivitySignal?,
        lastScore: Float?
    ): AiInsightPayload {

        val weakArList = profile.weakSkillList.joinToString(" و ") {
            when (it) {
                "LANGUAGE" -> "اللغة العربية"
                "NUMERACY" -> "الأرقام والحساب"
                "MOTOR"    -> "المهارات الحركية"
                else       -> it
            }
        }.ifEmpty { "لا توجد نقاط ضعف واضحة حتى الآن" }

        val structuredJson = """
        {
          "childId": ${profile.childId},
          "dominantModality": "${profile.dominantModality}",
          "skillLevels": {
            "language": ${profile.languageLevel},
            "numeracy": ${profile.numeracyLevel},
            "motor": ${profile.motorLevel}
          },
          "skillProgress": {
            "language": ${profile.languageProgress},
            "numeracy": ${profile.numeracyProgress},
            "motor": ${profile.motorProgress}
          },
          "modalityScores": {
            "visual": ${profile.visualScore},
            "audio": ${profile.audioScore},
            "interactive": ${profile.gameScore}
          },
          "weakSkillAreas": [${profile.weakSkillList.joinToString(",") { "\"$it\"" }}],
          "totalActivitiesCompleted": ${profile.totalActivitiesCompleted},
          "lastActivityScore": ${lastScore ?: "null"},
          "lastActivitySkillArea": "${lastSignal?.skillArea ?: ""}",
          "lastActivityType": "${lastSignal?.activityType ?: ""}"
        }
        """.trimIndent()

        val prompt = """
أنت مساعد تعليمي متخصص في تطوير الأطفال من عمر 3-5 سنوات.
بناءً على بيانات أداء الطفل، اكتب تقريراً شاملاً للوالدين باللغة العربية.

بيانات الطفل:
- أسلوب التعلم المفضل: ${profile.dominantModalityArabic}
- مستوى اللغة: ${profile.languageLevel}/5 (تقدم: ${(profile.languageProgress * 100).toInt()}%)
- مستوى الأرقام: ${profile.numeracyLevel}/5 (تقدم: ${(profile.numeracyProgress * 100).toInt()}%)
- مستوى المهارات الحركية: ${profile.motorLevel}/5 (تقدم: ${(profile.motorProgress * 100).toInt()}%)
- المجالات التي تحتاج تحسيناً: $weakArList
- عدد الأنشطة المكتملة: ${profile.totalActivitiesCompleted}

اكتب التقرير بهذا التنسيق بالضبط (لا تضف أي نص خارج هذا التنسيق):
أسلوب_التعلم: [جملة واحدة تصف أسلوب تعلم الطفل]
نقاط_القوة: [جملة واحدة عن نقاط قوة الطفل]
مجالات_التطوير: [جملة واحدة عن المجالات التي تحتاج تطوير]
نصيحة_1_عنوان: [3-4 كلمات]
نصيحة_1_تفاصيل: [جملة واحدة]
نصيحة_2_عنوان: [3-4 كلمات]
نصيحة_2_تفاصيل: [جملة واحدة]
نصيحة_3_عنوان: [3-4 كلمات]
نصيحة_3_تفاصيل: [جملة واحدة]
إرشاد_مقدمة: [جملة تمهيدية للتوصيات]
موصى_1: [نشاط أو أسلوب محدد]
موصى_2: [نشاط أو أسلوب محدد]
موصى_3: [نشاط أو أسلوب محدد]
تجنب_1: [ما يجب تجنبه]
تجنب_2: [ما يجب تجنبه]
تجنب_3: [ما يجب تجنبه]
        """.trimIndent()

        return AiInsightPayload(
            structuredJson        = structuredJson,
            naturalLanguagePrompt = prompt
        )
    }
}
