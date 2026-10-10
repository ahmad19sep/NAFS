package com.ahmad.nafs.core.di

import com.ahmad.nafs.core.deen.DeenRepository
import com.ahmad.nafs.core.deen.SupabaseDeenRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DeenModule {
    @Binds
    @Singleton
    abstract fun bindDeenRepository(implementation: SupabaseDeenRepository): DeenRepository
}
