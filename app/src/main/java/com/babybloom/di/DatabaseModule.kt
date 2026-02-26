package com.babybloom.di

import android.content.Context
import androidx.room.Room
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

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "babybloom_db"
        )
            .fallbackToDestructiveMigration() // ← remove before final demo
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
}