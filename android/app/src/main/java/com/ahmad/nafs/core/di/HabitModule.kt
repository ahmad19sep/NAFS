package com.ahmad.nafs.core.di

import com.ahmad.nafs.core.habits.HabitRepository
import com.ahmad.nafs.core.habits.SupabaseHabitRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class HabitModule {
    @Binds
    abstract fun bindHabitRepository(repository: SupabaseHabitRepository): HabitRepository
}
