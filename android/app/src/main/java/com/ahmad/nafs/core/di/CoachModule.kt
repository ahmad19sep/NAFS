package com.ahmad.nafs.core.di

import com.ahmad.nafs.core.coach.CoachRepository
import com.ahmad.nafs.core.coach.SupabaseCoachRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CoachModule {
    @Binds
    @Singleton
    abstract fun bindCoachRepository(implementation: SupabaseCoachRepository): CoachRepository
}
