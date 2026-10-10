package com.ahmad.nafs.core.di

import com.ahmad.nafs.core.network.ApiSessionTokens
import com.ahmad.nafs.core.network.SupabaseApiSessionTokens
import com.ahmad.nafs.core.reports.CachedReportRepository
import com.ahmad.nafs.core.reports.EncryptedReportCache
import com.ahmad.nafs.core.reports.ReportCache
import com.ahmad.nafs.core.reports.ReportRemote
import com.ahmad.nafs.core.reports.ReportRepository
import com.ahmad.nafs.core.reports.SupabaseReportRemote
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ReportModule {
    @Binds abstract fun bindRepository(implementation: CachedReportRepository): ReportRepository
    @Binds abstract fun bindRemote(implementation: SupabaseReportRemote): ReportRemote
    @Binds abstract fun bindCache(implementation: EncryptedReportCache): ReportCache
    @Binds abstract fun bindSessionTokens(implementation: SupabaseApiSessionTokens): ApiSessionTokens
}
