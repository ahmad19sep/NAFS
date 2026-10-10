package com.ahmad.nafs.core.network

import io.github.jan.supabase.auth.auth
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class SupabaseApiSessionTokens @Inject constructor(private val clientProvider: SupabaseClientProvider) : ApiSessionTokens {
    private val refreshMutex = Mutex()

    override suspend fun accessToken(userId: String, forceRefresh: Boolean): String = refreshMutex.withLock {
        val auth = clientProvider.client?.auth ?: throw NafsApiException(ApiFailure.CONFIGURATION)
        try {
            auth.awaitInitialization()
            val initial = auth.currentSessionOrNull() ?: throw NafsApiException(ApiFailure.AUTHENTICATION)
            if (initial.user?.id != userId) throw NafsApiException(ApiFailure.AUTHENTICATION)
            if (forceRefresh || initial.expiresAt.epochSeconds <= Instant.now().epochSecond + 30L) {
                auth.refreshCurrentSession()
            }
            val session = auth.currentSessionOrNull() ?: throw NafsApiException(ApiFailure.AUTHENTICATION)
            if (session.user?.id != userId || session.accessToken.isBlank()) throw NafsApiException(ApiFailure.AUTHENTICATION)
            session.accessToken
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: NafsApiException) {
            throw error
        } catch (_: Exception) {
            throw NafsApiException(ApiFailure.AUTHENTICATION)
        }
    }

    override fun isCurrentUser(userId: String): Boolean = clientProvider.client?.auth?.currentUserOrNull()?.id == userId
}
