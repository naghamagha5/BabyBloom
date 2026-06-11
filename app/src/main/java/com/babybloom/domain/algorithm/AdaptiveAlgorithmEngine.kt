package com.babybloom.domain.algorithm

import com.babybloom.domain.model.ActivitySignal
import com.babybloom.domain.model.ActivityLaunchStep
import com.babybloom.domain.model.AiInsightPayload
import com.babybloom.domain.model.AlgorithmOutput
import com.babybloom.domain.model.ChildProfile
import com.babybloom.domain.model.SessionDecision
import com.babybloom.domain.model.LearningContent
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

        val dominantModality = computeDominantModality(
            currentProfile.visualPreferencePercent,
            currentProfile.audioPreferencePercent,
            currentProfile.interactivePreferencePercent
        )

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
            visualScore              = currentProfile.visualPreferencePercent / 100f,
            audioScore               = currentProfile.audioPreferencePercent / 100f,
            gameScore                = currentProfile.interactivePreferencePercent / 100f,
            visualPreferencePercent  = currentProfile.visualPreferencePercent,
            audioPreferencePercent   = currentProfile.audioPreferencePercent,
            interactivePreferencePercent = currentProfile.interactivePreferencePercent,
            languageLevel            = langLevel,
            numeracyLevel            = numerLevel,
            motorLevel               = motorLevel,
            languageProgress         = langProgress,
            numeracyProgress         = numerProgress,
            motorProgress            = motorProgress,
            dominantModality         = dominantModality,
            weakSkillAreas           = weakSkills,
            totalActivitiesCompleted = currentProfile.totalActivitiesCompleted + 1,
            overallProgressPercent   = computeOverallProgressPercent(
                langLevel,
                numerLevel,
                motorLevel,
                langProgress,
                numerProgress,
                motorProgress
            ),
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

    /**
     * Updates modality preference from completed normal-session test/revision
     * steps only. Correctness is intentionally ignored; this measures
     * engagement, not competence.
     */
    fun updateModalityPreferencesFromSession(
        signals: List<ActivitySignal>,
        currentProfile: ChildProfile
    ): ChildProfile {
        if (signals.isEmpty()) {
            return currentProfile.copy(
                overallProgressPercent = computeOverallProgressPercent(currentProfile),
                lastUpdated = System.currentTimeMillis()
            )
        }

        val weightedEngagement = mutableMapOf(
            "VISUAL" to 0f,
            "AUDIO" to 0f,
            "INTERACTIVE" to 0f
        )
        signals.forEach { signal ->
            val engagement = computeEngagementScore(signal)
            val weights = AlgorithmWeights.ACTIVITY_MODALITY_WEIGHTS[signal.activityType]
                ?: mapOf(signal.modality to 1f)

            weights.forEach { (modality, weight) ->
                weightedEngagement[modality] = (weightedEngagement[modality] ?: 0f) + engagement * weight
            }
        }

        val sessionPercentages = normalizeToPercentages(weightedEngagement)
        val sessionScores = sessionPercentages.mapValues { (_, percent) -> percent / 100f }

        val oldFractions = mapOf(
            "VISUAL" to currentProfile.visualPreferencePercent / 100f,
            "AUDIO" to currentProfile.audioPreferencePercent / 100f,
            "INTERACTIVE" to currentProfile.interactivePreferencePercent / 100f
        )
        val smoothedFractions = oldFractions.mapValues { (modality, oldValue) ->
            val sessionValue = sessionScores[modality] ?: oldValue
            emaUpdate(oldValue, sessionValue)
        }
        val percentages = normalizeToPercentages(smoothedFractions)
        val visual = percentages["VISUAL"] ?: 33.34f
        val audio = percentages["AUDIO"] ?: 33.33f
        val interactive = percentages["INTERACTIVE"] ?: 33.33f

        return currentProfile.copy(
            visualPreferencePercent = visual,
            audioPreferencePercent = audio,
            interactivePreferencePercent = interactive,
            visualScore = visual / 100f,
            audioScore = audio / 100f,
            gameScore = interactive / 100f,
            dominantModality = computeDominantModality(visual, audio, interactive),
            overallProgressPercent = computeOverallProgressPercent(currentProfile),
            lastUpdated = System.currentTimeMillis()
        )
    }

    /** Applies the canonical content-based progress rules to a profile snapshot. */
    fun applyContentProgress(
        currentProfile: ChildProfile,
        allContent: List<LearningContent>,
        learnedContentIds: Set<String>
    ): ChildProfile {
        fun categoryLevel(category: String, maxLevel: Int): Int = allContent
            .asSequence()
            .filter { it.category == category && it.id in learnedContentIds }
            .maxOfOrNull { it.difficultyLevel }
            ?.coerceIn(0, maxLevel)
            ?: 0

        val alphabet = categoryLevel("LETTER_NAME", 5)
        val animals = categoryLevel("ANIMAL", 5)
        val numbers = categoryLevel("NUMBER", 4)
        val colors = categoryLevel("COLOR", 4)
        val shapes = categoryLevel("SHAPE", 4)

        val languageScore = (alphabet + animals) / 10f
        val numeracyScore = numbers / 4f
        val motorScore = (colors + shapes) / 8f
        val totalContent = allContent.size.coerceAtLeast(1)
        val learnedCount = allContent.count { it.id in learnedContentIds }

        return currentProfile.copy(
            languageLevel = kotlin.math.round((alphabet + animals) / 2f).toInt().coerceIn(0, 5),
            numeracyLevel = numbers,
            motorLevel = kotlin.math.round((colors + shapes) / 2f).toInt().coerceIn(0, 4),
            languageProgress = languageScore,
            numeracyProgress = numeracyScore,
            motorProgress = motorScore,
            overallProgressPercent = learnedCount.toFloat() / totalContent * 100f,
            weakSkillAreas = computeWeakSkills(languageScore, numeracyScore, motorScore),
            lastUpdated = System.currentTimeMillis()
        )
    }

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

        signal.touchQualityScore?.let { score ->
            base += score * AlgorithmWeights.TOUCH_QUALITY_BONUS_MAX
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
        val languageLevel = profile.languageLevel.coerceIn(1, maxLevelFor("LANGUAGE"))
        val numeracyLevel = profile.numeracyLevel.coerceIn(1, maxLevelFor("NUMERACY"))
        val motorLevel = profile.motorLevel.coerceIn(1, maxLevelFor("MOTOR"))

        fun resolveLevel(skillArea: String, current: Int, progress: Float): Int {
            val maxLevel = maxLevelFor(skillArea)
            if (profile.totalActivitiesCompleted < AlgorithmWeights.MIN_ACTIVITIES_FOR_LEVEL_CHANGE)
                return current.coerceIn(1, maxLevel)
            return when {
                progress >= AlgorithmWeights.LEVEL_UP_THRESHOLD   && current < maxLevel -> current + 1
                progress <= AlgorithmWeights.LEVEL_DOWN_THRESHOLD && current > 1 -> current - 1
                else -> current
            }.coerceIn(1, maxLevel)
        }

        return when (signal.skillArea) {
            "LANGUAGE" -> {
                val newProg  = emaUpdate(profile.languageProgress, itemScore)
                val newLevel = resolveLevel("LANGUAGE", languageLevel, newProg)
                SkillState(newLevel, numeracyLevel, motorLevel,
                    newProg, profile.numeracyProgress, profile.motorProgress)
            }
            "NUMERACY" -> {
                val newProg  = emaUpdate(profile.numeracyProgress, itemScore)
                val newLevel = resolveLevel("NUMERACY", numeracyLevel, newProg)
                SkillState(languageLevel, newLevel, motorLevel,
                    profile.languageProgress, newProg, profile.motorProgress)
            }
            "MOTOR" -> {
                val newProg  = emaUpdate(profile.motorProgress, itemScore)
                val newLevel = resolveLevel("MOTOR", motorLevel, newProg)
                SkillState(languageLevel, numeracyLevel, newLevel,
                    profile.languageProgress, profile.numeracyProgress, newProg)
            }
            else -> SkillState(
                languageLevel, numeracyLevel, motorLevel,
                profile.languageProgress, profile.numeracyProgress, profile.motorProgress
            )
        }
    }

    private fun computeEngagementScore(signal: ActivitySignal): Float {
        val signals = buildList {
            add(signal.attentionScore ?: 0.5f)
            if (signal.activityType in setOf("DRAG", "TRACE", "MATCH")) {
                signal.touchQualityScore?.let(::add)
            }
            if (signal.activityType == "SPEECH") {
                signal.speechConfidence?.let(::add)
            }
        }
        return signals.average().toFloat().coerceIn(0f, 1f)
    }

    private fun normalizeToPercentages(values: Map<String, Float>): Map<String, Float> {
        val visual = (values["VISUAL"] ?: 0f).coerceAtLeast(0f)
        val audio = (values["AUDIO"] ?: 0f).coerceAtLeast(0f)
        val interactive = (values["INTERACTIVE"] ?: 0f).coerceAtLeast(0f)
        val total = visual + audio + interactive
        if (total <= 0f) {
            return mapOf("VISUAL" to 33.34f, "AUDIO" to 33.33f, "INTERACTIVE" to 33.33f)
        }
        val visualPercent = visual / total * 100f
        val audioPercent = audio / total * 100f
        val interactivePercent = 100f - visualPercent - audioPercent
        return mapOf(
            "VISUAL" to visualPercent.coerceIn(0f, 100f),
            "AUDIO" to audioPercent.coerceIn(0f, 100f),
            "INTERACTIVE" to interactivePercent.coerceIn(0f, 100f)
        )
    }

    private fun computeOverallProgressPercent(profile: ChildProfile): Float =
        computeOverallProgressPercent(
            profile.languageLevel,
            profile.numeracyLevel,
            profile.motorLevel,
            profile.languageProgress,
            profile.numeracyProgress,
            profile.motorProgress
        )

    private fun computeOverallProgressPercent(
        languageLevel: Int,
        numeracyLevel: Int,
        motorLevel: Int,
        languageProgress: Float,
        numeracyProgress: Float,
        motorProgress: Float
    ): Float {
        fun normalized(level: Int, progress: Float, maxLevel: Int): Float {
            val safeLevel = level.coerceIn(1, maxLevel)
            return ((safeLevel - 1) + progress.coerceIn(0f, 1f))
                .div((maxLevel - 1).coerceAtLeast(1).toFloat())
                .coerceIn(0f, 1f)
        }

        return (
            normalized(languageLevel, languageProgress, maxLevelFor("LANGUAGE")) +
                normalized(numeracyLevel, numeracyProgress, maxLevelFor("NUMERACY")) +
                normalized(motorLevel, motorProgress, maxLevelFor("MOTOR"))
            ) / 3f * 100f
    }

    private fun maxLevelFor(skillArea: String): Int =
        when (skillArea) {
            "LANGUAGE" -> 5
            "NUMERACY",
            "MOTOR" -> 4
            else -> 5
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
