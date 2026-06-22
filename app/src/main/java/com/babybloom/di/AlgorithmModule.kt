package com.babybloom.di

import com.babybloom.data.repository.ActivityRecommendationRepositoryImpl
import com.babybloom.data.repository.AiInsightRepositoryImpl
import com.babybloom.data.repository.AssessmentRepositoryImpl
import com.babybloom.data.repository.LevelMasteryRepositoryImpl
import com.babybloom.domain.repository.ActivityRecommendationRepository
import com.babybloom.domain.repository.AiInsightRepository
import com.babybloom.domain.repository.AssessmentRepository
import com.babybloom.domain.repository.LevelMasteryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AlgorithmModule {

    @Binds @Singleton
    abstract fun bindActivityRecommendationRepository(
        impl: ActivityRecommendationRepositoryImpl
    ): ActivityRecommendationRepository

    @Binds @Singleton
    abstract fun bindAssessmentRepository(
        impl: AssessmentRepositoryImpl
    ): AssessmentRepository

    @Binds @Singleton
    abstract fun bindLevelMasteryRepository(
        impl: LevelMasteryRepositoryImpl
    ): LevelMasteryRepository

    @Binds @Singleton
    abstract fun bindAiInsightRepository(
        impl: AiInsightRepositoryImpl
    ): AiInsightRepository
}