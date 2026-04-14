package com.babybloom.data.local.seeder

import com.babybloom.data.local.dao.ActivityDao
import com.babybloom.data.local.dao.ActivityResultDao
import com.babybloom.data.local.dao.AiInsightDao
import com.babybloom.data.local.dao.ChildDao
import com.babybloom.data.local.dao.ChildProfileDao
import com.babybloom.data.local.dao.SessionDao
import com.babybloom.data.local.dao.UserDao
import com.babybloom.data.local.entity.ActivityEntity
import com.babybloom.data.local.entity.ActivityResultEntity
import com.babybloom.data.local.entity.AiInsightEntity
import com.babybloom.data.local.entity.ChildEntity
import com.babybloom.data.local.entity.ChildProfileEntity
import com.babybloom.data.local.entity.SessionEntity
import com.babybloom.data.local.entity.UserEntity
import com.babybloom.util.HashUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseSeeder @Inject constructor(
    private val userDao: UserDao,
    private val childDao: ChildDao,
    private val childProfileDao: ChildProfileDao,
    private val sessionDao: SessionDao,
    private val activityDao: ActivityDao,
    private val activityResultDao: ActivityResultDao,
    private val aiInsightDao: AiInsightDao
) {

    suspend fun seedIfEmpty() = withContext(Dispatchers.IO) {
        if (childDao.countAll() > 0) return@withContext

        // ── 1. User ───────────────────────────────────────────────────────
        val userId = userDao.insert(
            UserEntity(
                name = "أحمد محمد",
                email = "test@babybloom.com",
                passwordHash = HashUtils.sha256("test1234!")
            )
        )

        // ── 2. Child ──────────────────────────────────────────────────────
        val childId = childDao.insert(
            ChildEntity(
                userId = userId,
                name = "ليلى",
                age = 4,
                notes = "تحب الرسم والألوان",
                avatar = "avatar_girl_1",
                musicEnabled = true,
                backgroundMusicEnabled = true,
                reducedAnimation = false,
                uiTheme = false,
                status = "NEEDS_SUPPORT",
                sessionDurationMinutes = 20
            )
        )

        // ── 3. Child Profile ──────────────────────────────────────────────
        childProfileDao.insert(
            ChildProfileEntity(
                childId = childId,
                visualScore = 0.82f,
                audioScore = 0.65f,
                gameScore = 0.78f,
                languageLevel = 2,
                numeracyLevel = 2,
                motorLevel = 3,
                totalSessionCount = 42
            )
        )

        // ── 4. Activities — must come before activity_results (FK) ────────
        val activities = listOf(
            ActivityEntity(
                id = "lang_letters_001",
                title = "الحروف العربية",
                description = "تعلم الحروف الهجائية",
                modality = "VISUAL",
                skillArea = "LANGUAGE",
                difficultyLevel = 2,
                activityType = "MATCH"
            ),
            ActivityEntity(
                id = "num_count_001",
                title = "الأرقام والحساب",
                description = "تعلم الأرقام والعد",
                modality = "INTERACTIVE",
                skillArea = "NUMERACY",
                difficultyLevel = 2,
                activityType = "COUNT"
            ),
            ActivityEntity(
                id = "vis_colors_001",
                title = "الألوان والأشكال",
                description = "التعرف على الألوان والأشكال",
                modality = "VISUAL",
                skillArea = "MOTOR",
                difficultyLevel = 1,
                activityType = "STORY"
            )
        )
        activities.forEach { activityDao.insert(it) }

        // ── 5. Sessions (6 days of history) ──────────────────────────────
        val now = System.currentTimeMillis()
        val day = 24 * 60 * 60 * 1000L
        val dur = 20 * 60 * 1000L

        val sessionIds = (0..5).map { i ->
            sessionDao.insert(
                SessionEntity(
                    userId = userId,
                    childId = childId,
                    startTime = now - (i * day),
                    endTime = now - (i * day) + dur,
                    isAssessment = (i == 0),
                    attentionScore = minOf(0.70f + i * 0.04f, 0.99f)
                )
            )
        }

        // ── 6. Activity Results ───────────────────────────────────────────
        // scores per activity — index matches activities list above
        val baseScores = listOf(0.92f, 0.88f, 0.76f)

        sessionIds.forEachIndexed { si, sessionId ->
            activities.forEachIndexed { ai, activity ->
                activityResultDao.insert(
                    ActivityResultEntity(
                        sessionId = sessionId,
                        childId = childId,
                        activityId = activity.id,   // ← valid FK, matches step 4
                        score = (baseScores[ai] - si * 0.02f).coerceAtLeast(0.50f),
                        duration = 5 * 60 * 1000L + ai * 60_000L,
                        correctCount = (baseScores[ai] * 10).toInt(),
                        incorrectCount = 10 - (baseScores[ai] * 10).toInt(),
                        timestamp = now - (si * day) + (ai * 300_000L)
                    )
                )
            }
        }

        // ── 7. AI Insight ─────────────────────────────────────────────────
        aiInsightDao.insertInsight(
            AiInsightEntity(
                childId = childId,
                insightText = SEED_INSIGHT,
                generatedAt = now
            )
        )
    }

    companion object {
        private val SEED_INSIGHT = """
            أسلوب_التعلم: بصري-حركي
            نقاط_القوة: تتفوق ليلى في التعرف على الألوان والأشكال وتُظهر مهارات حركية دقيقة ممتازة
            مجالات_التطوير: تحسين مهارات الاستماع والتركيز الصوتي
            نصيحة_1_عنوان: جلسات قصيرة ومكثفة
            نصيحة_1_تفاصيل: اجعلي جلسات التعلم بين 15-20 دقيقة مع فترات راحة للحفاظ على تركيزها
            نصيحة_2_عنوان: التعلم بالألوان والصور
            نصيحة_2_تفاصيل: استخدمي البطاقات الملونة والصور التوضيحية في تعليم الأحرف والأرقام
            إرشاد_مقدمة: بناءً على أداء ليلى نوصي بالتركيز على الأنشطة البصرية والحركية
            موصى_1: استخدام تطبيقات الرسم التفاعلية
            موصى_2: الألعاب التي تعتمد على التعرف على الأشكال
            موصى_3: قصص مصورة بألوان زاهية
            تجنب_1: الجلسات الصوتية الطويلة دون مرئيات
            تجنب_2: التمارين المجردة بدون دعم بصري
            تجنب_3: الأنشطة الساكنة لفترات طويلة
        """.trimIndent()
    }
}