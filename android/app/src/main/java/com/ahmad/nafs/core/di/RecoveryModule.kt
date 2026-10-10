package com.ahmad.nafs.core.di

import com.ahmad.nafs.core.plans.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RecoveryModule {
    @Binds abstract fun bindRepository(implementation: CachedRecoveryRepository): RecoveryRepository
    @Binds abstract fun bindRemote(implementation: ApiRecoveryRemote): RecoveryRemote
    @Binds abstract fun bindCache(implementation: EncryptedRecoveryCache): RecoveryCache
    @Binds abstract fun bindItems(implementation: NativePlanItemWriter): PlanItemWriter
}
