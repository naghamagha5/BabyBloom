package com.babybloom.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.babybloom.data.local.dao.*
import com.babybloom.data.local.entity.*

@Database(
    entities = [
        UserEntity::class,
        ChildEntity::class,
        ChildProfileEntity::class,
        SessionEntity::class,
        ActivityEntity::class,
        LearningContentEntity::class,
        ActivityContentEntity::class,
        ActivityResultEntity::class,
        InteractionEventEntity::class,
        AiInsightEntity::class,
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun childDao(): ChildDao
    abstract fun childProfileDao(): ChildProfileDao
    abstract fun sessionDao(): SessionDao
    abstract fun activityDao(): ActivityDao
    abstract fun learningContentDao(): LearningContentDao
    abstract fun activityContentDao(): ActivityContentDao
    abstract fun activityResultDao(): ActivityResultDao
    abstract fun interactionEventDao(): InteractionEventDao

    abstract fun aiInsightDao(): AiInsightDao

}