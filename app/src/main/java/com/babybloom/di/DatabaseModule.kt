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

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "babybloom_db"
        )
            .addMigrations(MIGRATION_9_10, MIGRATION_10_11)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides fun provideUserDao(db: AppDatabase) = db.userDao()
    @Provides fun provideChildDao(db: AppDatabase) = db.childDao()
    @Provides fun provideChildProfileDao(db: AppDatabase) = db.childProfileDao()
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
