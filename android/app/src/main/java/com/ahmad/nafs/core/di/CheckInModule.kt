package com.ahmad.nafs.core.di

import com.ahmad.nafs.core.checkin.CheckInRepository
import com.ahmad.nafs.core.checkin.SupabaseCheckInRepository
import com.ahmad.nafs.core.checkin.EveningVerdictRepository
import com.ahmad.nafs.core.checkin.ApiEveningVerdictRepository
import com.ahmad.nafs.core.checkin.CheckInDraftStore
import com.ahmad.nafs.core.checkin.EncryptedCheckInDraftStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CheckInModule {
    @Binds
    @Singleton
    abstract fun bindCheckInRepository(implementation: SupabaseCheckInRepository): CheckInRepository

    @Binds
    @Singleton
    abstract fun bindEveningVerdictRepository(implementation: ApiEveningVerdictRepository): EveningVerdictRepository

    @Binds
    @Singleton
    abstract fun bindCheckInDraftStore(implementation: EncryptedCheckInDraftStore): CheckInDraftStore
}
