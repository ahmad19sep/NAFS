package com.ahmad.nafs.core.di

import com.ahmad.nafs.core.auth.AuthRepository
import com.ahmad.nafs.core.auth.SupabaseAuthRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {
    @Binds
    abstract fun bindAuthRepository(repository: SupabaseAuthRepository): AuthRepository
}
