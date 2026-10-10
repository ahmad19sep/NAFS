package com.ahmad.nafs.core.di

import com.ahmad.nafs.BuildConfig
import com.ahmad.nafs.core.network.NafsApiClient
import com.ahmad.nafs.core.network.nafsHttpEngine
import com.ahmad.nafs.core.network.SupabaseApiSessionTokens
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideApiClient(sessions: SupabaseApiSessionTokens): NafsApiClient = NafsApiClient(
        http = HttpClient(nafsHttpEngine()) {
            followRedirects = false
            expectSuccess = false
            install(HttpTimeout) {
                requestTimeoutMillis = 45_000L
                connectTimeoutMillis = 10_000L
                socketTimeoutMillis = 45_000L
            }
        },
        sessions = sessions,
        baseUrl = BuildConfig.API_BASE_URL,
    )
}
