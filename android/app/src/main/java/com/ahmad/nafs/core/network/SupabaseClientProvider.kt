package com.ahmad.nafs.core.network

import com.ahmad.nafs.BuildConfig
import com.ahmad.nafs.core.auth.KeystoreSessionManager
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseClientProvider @Inject constructor(
    private val sessionManager: KeystoreSessionManager,
) {
    val isConfigured: Boolean =
        BuildConfig.SUPABASE_URL.startsWith("https://") &&
            BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()

    val client: SupabaseClient? by lazy {
        if (!isConfigured) {
            null
        } else {
            createSupabaseClient(
                supabaseUrl = BuildConfig.SUPABASE_URL,
                supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
            ) {
                httpEngine = nafsHttpEngine()
                install(Auth) {
                    this.sessionManager = this@SupabaseClientProvider.sessionManager
                    flowType = FlowType.PKCE
                    scheme = BuildConfig.APPLICATION_ID
                    host = "auth"
                    alwaysAutoRefresh = true
                    autoLoadFromStorage = true
                    autoSaveToStorage = true
                }
                install(Postgrest)
                install(Storage)
            }
        }
    }
}
