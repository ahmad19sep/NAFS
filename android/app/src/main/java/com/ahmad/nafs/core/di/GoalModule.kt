package com.ahmad.nafs.core.di

import com.ahmad.nafs.core.goals.GoalRepository
import com.ahmad.nafs.core.goals.SupabaseGoalRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class GoalModule {
    @Binds
    abstract fun bindGoalRepository(repository: SupabaseGoalRepository): GoalRepository
}
