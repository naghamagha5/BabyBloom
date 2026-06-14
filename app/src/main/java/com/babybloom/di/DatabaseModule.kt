package com.babybloom.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.babybloom.data.local.AppDatabase
import com.babybloom.data.local.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS activity_results_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    sessionId INTEGER NOT NULL,
                    childId INTEGER NOT NULL,
                    activityId TEXT NOT NULL,
                    contentId TEXT NOT NULL,
                    score REAL NOT NULL,
                    duration INTEGER NOT NULL,
                    correctCount INTEGER NOT NULL,
                    incorrectCount INTEGER NOT NULL,
                    attempts INTEGER NOT NULL,
                    speechConfidence REAL,
                    motorSkillScore REAL,
                    choiceConfidenceScore REAL,
                    attentionScore REAL,
                    timestamp INTEGER NOT NULL,
                    FOREIGN KEY(sessionId) REFERENCES sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(childId) REFERENCES children(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(activityId) REFERENCES activities(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO activity_results_new (
                    id, sessionId, childId, activityId, contentId, score, duration,
                    correctCount, incorrectCount, attempts, speechConfidence,
                    motorSkillScore, choiceConfidenceScore, attentionScore, timestamp
                )
                SELECT
                    id, sessionId, childId, activityId, contentId, score, duration,
                    correctCount, incorrectCount, attempts, speechConfidence,
                    touchComplexity, touchComplexity, attentionScore, timestamp
                FROM activity_results
                """.trimIndent()
            )
            db.execSQL("DROP TABLE activity_results")
            db.execSQL("ALTER TABLE activity_results_new RENAME TO activity_results")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_activity_results_sessionId ON activity_results(sessionId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_activity_results_childId ON activity_results(childId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_activity_results_activityId ON activity_results(activityId)")
        }
    }

    private val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS activity_results_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    sessionId INTEGER NOT NULL,
                    childId INTEGER NOT NULL,
                    activityId TEXT NOT NULL,
                    contentId TEXT NOT NULL,
                    score REAL NOT NULL,
                    duration INTEGER NOT NULL,
                    correctCount INTEGER NOT NULL,
                    incorrectCount INTEGER NOT NULL,
                    attempts INTEGER NOT NULL,
                    speechConfidence REAL,
                    touchQualityScore REAL,
                    attentionScore REAL,
                    timestamp INTEGER NOT NULL,
                    FOREIGN KEY(sessionId) REFERENCES sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(childId) REFERENCES children(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(activityId) REFERENCES activities(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO activity_results_new (
                    id, sessionId, childId, activityId, contentId, score, duration,
                    correctCount, incorrectCount, attempts, speechConfidence,
                    touchQualityScore, attentionScore, timestamp
                )
                SELECT
                    id, sessionId, childId, activityId, contentId, score, duration,
                    correctCount, incorrectCount, attempts, speechConfidence,
                    CASE
                        WHEN motorSkillScore IS NOT NULL AND choiceConfidenceScore IS NOT NULL
                            THEN motorSkillScore * 0.55 + choiceConfidenceScore * 0.45
                        ELSE COALESCE(motorSkillScore, choiceConfidenceScore)
                    END,
                    attentionScore, timestamp
                FROM activity_results
                """.trimIndent()
            )
            db.execSQL("DROP TABLE activity_results")
            db.execSQL("ALTER TABLE activity_results_new RENAME TO activity_results")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_activity_results_sessionId ON activity_results(sessionId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_activity_results_childId ON activity_results(childId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_activity_results_activityId ON activity_results(activityId)")
        }
    }

    private val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE child_profiles ADD COLUMN visualPreferencePercent REAL NOT NULL DEFAULT 33.34")
            db.execSQL("ALTER TABLE child_profiles ADD COLUMN audioPreferencePercent REAL NOT NULL DEFAULT 33.33")
            db.execSQL("ALTER TABLE child_profiles ADD COLUMN interactivePreferencePercent REAL NOT NULL DEFAULT 33.33")
            db.execSQL("ALTER TABLE child_profiles ADD COLUMN overallProgressPercent REAL NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE level_mastery ADD COLUMN contentId TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE level_mastery ADD COLUMN contentScore REAL")
            db.execSQL("DROP INDEX IF EXISTS index_level_mastery_childId_skillArea_level")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_level_mastery_childId_skillArea_level_contentId ON level_mastery(childId, skillArea, level, contentId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_level_mastery_childId_contentId ON level_mastery(childId, contentId)")
        }
    }

    private val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE children ADD COLUMN gender TEXT NOT NULL DEFAULT 'UNSPECIFIED'")
            db.execSQL(
                """
                UPDATE children
                SET gender = CASE
                    WHEN LOWER(avatar) LIKE '%girl%' THEN 'FEMALE'
                    WHEN LOWER(avatar) LIKE '%boy%' THEN 'MALE'
                    ELSE 'UNSPECIFIED'
                END
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS child_profile_snapshots (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    childId INTEGER NOT NULL,
                    visualPreferencePercent REAL NOT NULL,
                    audioPreferencePercent REAL NOT NULL,
                    interactivePreferencePercent REAL NOT NULL,
                    dominantModality TEXT NOT NULL,
                    languageLevel INTEGER NOT NULL,
                    numeracyLevel INTEGER NOT NULL,
                    motorLevel INTEGER NOT NULL,
                    capturedAt INTEGER NOT NULL,
                    FOREIGN KEY(childId) REFERENCES children(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_child_profile_snapshots_childId ON child_profile_snapshots(childId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_child_profile_snapshots_childId_capturedAt ON child_profile_snapshots(childId, capturedAt)")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "babybloom_db"
        )
            .addMigrations(
                MIGRATION_9_10,
                MIGRATION_10_11,
                MIGRATION_11_12,
                MIGRATION_12_13,
                MIGRATION_13_14
            )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides fun provideUserDao(db: AppDatabase) = db.userDao()
    @Provides fun provideChildDao(db: AppDatabase) = db.childDao()
    @Provides fun provideChildProfileDao(db: AppDatabase) = db.childProfileDao()
    @Provides fun provideChildProfileSnapshotDao(db: AppDatabase) = db.childProfileSnapshotDao()
    @Provides fun provideSessionDao(db: AppDatabase) = db.sessionDao()
    @Provides fun provideActivityDao(db: AppDatabase) = db.activityDao()
    @Provides fun provideLearningContentDao(db: AppDatabase) = db.learningContentDao()
    @Provides fun provideActivityContentDao(db: AppDatabase) = db.activityContentDao()
    @Provides fun provideActivityResultDao(db: AppDatabase) = db.activityResultDao()
    @Provides fun provideInteractionEventDao(db: AppDatabase) = db.interactionEventDao()
    @Provides fun provideAiInsightDao(db: AppDatabase) = db.aiInsightDao()
    @Provides fun provideActivityRecommendationDao(db: AppDatabase) = db.activityRecommendationDao()
    @Provides fun provideAssessmentResultDao(db: AppDatabase) = db.assessmentResultDao()
    @Provides fun provideLevelMasteryDao(db: AppDatabase) = db.levelMasteryDao()
}
