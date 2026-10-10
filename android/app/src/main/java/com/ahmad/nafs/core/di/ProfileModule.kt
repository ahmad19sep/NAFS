package com.ahmad.nafs.core.di

import com.ahmad.nafs.core.profile.ProfileRepository
import com.ahmad.nafs.core.profile.SupabaseProfileRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ProfileModule {
    @Binds
    abstract fun bindProfileRepository(repository: SupabaseProfileRepository): ProfileRepository
}
