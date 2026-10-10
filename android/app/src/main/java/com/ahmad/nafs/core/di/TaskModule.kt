package com.ahmad.nafs.core.di

import com.ahmad.nafs.core.tasks.SupabaseTaskRepository
import com.ahmad.nafs.core.tasks.TaskRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class TaskModule {
    @Binds
    abstract fun bindTaskRepository(repository: SupabaseTaskRepository): TaskRepository
}
