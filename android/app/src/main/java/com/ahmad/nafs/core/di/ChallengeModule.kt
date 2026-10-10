package com.ahmad.nafs.core.di

import com.ahmad.nafs.core.challenges.ChallengeRepository
import com.ahmad.nafs.core.challenges.SupabaseChallengeRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ChallengeModule {
    @Binds
    abstract fun bindChallengeRepository(repository: SupabaseChallengeRepository): ChallengeRepository
}
