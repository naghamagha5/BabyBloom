package com.babybloom.di

import com.babybloom.data.repository.*
import com.babybloom.domain.notifications.ParentNotificationHandler
import com.babybloom.domain.notifications.ParentNotificationService
import com.babybloom.domain.repository.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository

    @Binds @Singleton
    abstract fun bindChildRepository(impl: ChildRepositoryImpl): ChildRepository

    @Binds @Singleton
    abstract fun bindChildProfileRepository(impl: ChildProfileRepositoryImpl): ChildProfileRepository

    @Binds @Singleton
    abstract fun bindSessionRepository(impl: SessionRepositoryImpl): SessionRepository

    @Binds @Singleton
    abstract fun bindActivityRepository(impl: ActivityRepositoryImpl): ActivityRepository

    @Binds @Singleton
    abstract fun bindActivityResultRepository(impl: ActivityResultRepositoryImpl): ActivityResultRepository

    @Binds @Singleton
    abstract fun bindLearningContentRepository(impl: LearningContentRepositoryImpl): LearningContentRepository

    @Binds @Singleton
    abstract fun bindInteractionEventRepository(impl: InteractionEventRepositoryImpl): InteractionEventRepository

    @Binds @Singleton
    abstract fun bindAppNotificationRepository(impl: AppNotificationRepositoryImpl): AppNotificationRepository

    @Binds @Singleton
    abstract fun bindParentNotificationHandler(impl: ParentNotificationService): ParentNotificationHandler
}
