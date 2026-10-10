package com.ahmad.nafs.core.coach

import com.ahmad.nafs.core.database.CachedCoachDao
import com.ahmad.nafs.core.database.CachedCoachEntity
import com.ahmad.nafs.core.network.ApiFailure
import com.ahmad.nafs.core.network.NafsApiClient
import com.ahmad.nafs.core.network.NafsApiException
import com.ahmad.nafs.core.network.SupabaseApiSessionTokens
import com.ahmad.nafs.core.network.SupabaseClientProvider
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

@Singleton
class SupabaseCoachRepository @Inject constructor(
    private val clientProvider: SupabaseClientProvider,
    private val sessions: SupabaseApiSessionTokens,
    private val api: NafsApiClient,
    private val cache: CachedCoachDao,
    private val cipher: CoachCacheCipher,
) : CoachRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val cacheMutex = Mutex()

    override suspend fun load(userId: String): CoachLoadResult {
        var cached: CoachSnapshot? = null
        return try {
            checkAccount(userId)
            cached = try { cacheMutex.withLock { read(userId) } } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) { null } // Rebuild an unreadable cache from authoritative online history.
            checkAccount(userId)
            val conversations = requireClient().from("ai_conversations").select(COLUMNS) {
                filter { eq("user_id", userId) }
                order("updated_at", Order.DESCENDING)
                limit(20)
            }.decodeList<CoachConversation>().toMutableList()
            // An unfinished send may belong to a conversation outside the recent list.
            cached?.pending?.conversationId?.takeIf { id -> conversations.none { it.id == id } }?.let { id ->
                conversations += requireClient().from("ai_conversations").select(COLUMNS) {
                    filter { eq("user_id", userId); eq("id", id) }
                    limit(1)
                }.decodeList<CoachConversation>()
            }
            checkAccount(userId)
            if (conversations.any { !CoachRules.validConversation(userId, it) }) throw NafsApiException(ApiFailure.INVALID_RESPONSE)
            val snapshot = cacheMutex.withLock {
                val latest = try { read(userId) } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) { cached }
                val refreshed = CoachRules.refreshed(userId, cached, latest, conversations)
                try { write(userId, refreshed) } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) { /* Online history remains usable. */ }
                refreshed
            }
            checkAccount(userId)
            CoachLoadResult.Loaded(snapshot, isOffline = false)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            if (!sessions.isCurrentUser(userId)) return CoachLoadResult.Failure("Sign in again to load your conversations.")
            val fallback = cached ?: try { cacheMutex.withLock { read(userId) } } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) { null }
            if (fallback != null) CoachLoadResult.Loaded(fallback, isOffline = true)
            else CoachLoadResult.Failure("Unable to load your conversations. Check your connection and retry.")
        }
    }

    override suspend fun send(userId: String, intent: CoachSendIntent): CoachSendResult = try {
        if (!CoachRules.validIntent(intent)) throw NafsApiException(ApiFailure.INVALID_REQUEST)
        // Persist the exact request before generation, including across app/process restarts.
        cacheMutex.withLock {
            checkAccount(userId)
            val snapshot = read(userId) ?: CoachSnapshot()
            if (snapshot.pending != null && snapshot.pending != intent) throw NafsApiException(ApiFailure.CONFLICT)
            try { write(userId, snapshot.copy(pending = intent)) } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) { throw NafsApiException(ApiFailure.UNAVAILABLE) }
        }
        val body = api.post(userId, "/api/ai/chat", json.encodeToJsonElement(intent).jsonObject)
        val response = json.decodeFromJsonElement(CoachResponse.serializer(), body)
        if (!CoachRules.matches(userId, intent, response.conversation, response.reply)) {
            throw NafsApiException(ApiFailure.INVALID_RESPONSE)
        }
        checkAccount(userId)
        // The returned conversation is already saved; local cache failure does not undo that result.
        try {
            cacheMutex.withLock {
                val snapshot = read(userId) ?: CoachSnapshot()
                write(userId, snapshot.copy(
                    conversations = (listOf(response.conversation) + snapshot.conversations.filter { it.id != intent.conversationId }).take(20),
                    pending = snapshot.pending?.takeUnless { it == intent },
                ))
            }
        } catch (cancellation: CancellationException) { throw cancellation } catch (_: Exception) { /* Recover on reload. */ }
        checkAccount(userId)
        CoachSendResult.Saved(response.conversation, response.contextPartial)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: NafsApiException) {
        CoachSendResult.Failure(error.failure, CoachErrors.message(error.failure))
    } catch (_: Exception) {
        CoachSendResult.Failure(ApiFailure.INVALID_RESPONSE, CoachErrors.message(ApiFailure.INVALID_RESPONSE))
    }

    override suspend fun discardPending(userId: String) = cacheMutex.withLock {
        checkAccount(userId)
        read(userId)?.let { write(userId, it.copy(pending = null)) }
        Unit
    }

    override suspend fun clear(userId: String) = cacheMutex.withLock { cache.clearForUser(userId) }

    private suspend fun read(userId: String): CoachSnapshot? {
        checkAccount(userId)
        val row = cache.get(userId) ?: return null
        val snapshot = json.decodeFromString<CoachSnapshot>(cipher.decrypt(EncryptedCoachValue(row.payloadCiphertext, row.payloadIv)))
        checkAccount(userId)
        if (snapshot.conversations.any { !CoachRules.validConversation(userId, it) } ||
            snapshot.pending?.let { !CoachRules.validIntent(it) } == true) throw NafsApiException(ApiFailure.INVALID_RESPONSE)
        return snapshot
    }

    private suspend fun write(userId: String, snapshot: CoachSnapshot) {
        val encrypted = cipher.encrypt(json.encodeToString(snapshot))
        checkAccount(userId)
        cache.upsert(CachedCoachEntity(userId, encrypted.ciphertext, encrypted.iv))
    }

    private fun checkAccount(userId: String) {
        if (!sessions.isCurrentUser(userId)) throw NafsApiException(ApiFailure.AUTHENTICATION)
    }
    private fun requireClient() = checkNotNull(clientProvider.client)
    private companion object {
        val COLUMNS = Columns.list("id", "user_id", "messages", "created_at", "updated_at")
    }
}

@Serializable
private data class CoachResponse(
    val reply: String,
    val conversation: CoachConversation,
    @SerialName("context_partial") val contextPartial: Boolean = false,
)

object CoachErrors {
    fun message(failure: ApiFailure): String = when (failure) {
        ApiFailure.CONFLICT -> "This conversation changed. Reload the saved conversation before sending again."
        ApiFailure.IN_PROGRESS -> "The coach is still replying. Wait a moment, then retry or reload."
        ApiFailure.RESULT_UNCONFIRMED, ApiFailure.TIMEOUT -> "Reload your saved conversation to recover any reply. Your message is retained."
        ApiFailure.UNAVAILABLE -> "The coach is temporarily unavailable. Your message is retained for retry."
        ApiFailure.INVALID_REQUEST -> "Reload this conversation and check your message before sending."
        ApiFailure.INVALID_RESPONSE -> "The chat backend returned an incompatible reply. Reload your conversation; the backend may need an update."
        else -> NafsApiException(failure).message.orEmpty()
    }
}
