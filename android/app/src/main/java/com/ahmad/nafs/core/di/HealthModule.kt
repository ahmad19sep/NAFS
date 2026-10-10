package com.ahmad.nafs.core.di

import com.ahmad.nafs.core.health.HealthRepository
import com.ahmad.nafs.core.health.SupabaseHealthRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HealthModule {
    @Binds
    @Singleton
    abstract fun bindHealthRepository(implementation: SupabaseHealthRepository): HealthRepository
}
