package com.ahmad.nafs.core.checkin

import com.ahmad.nafs.core.database.CachedCheckInDao
import com.ahmad.nafs.core.database.CachedCheckInEntity
import com.ahmad.nafs.core.health.HealthWriteRules
import com.ahmad.nafs.core.network.SupabaseClientProvider
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class SupabaseCheckInRepository @Inject constructor(
    private val clientProvider: SupabaseClientProvider,
    private val cache: CachedCheckInDao,
    private val cipher: CheckInCacheCipher,
) : CheckInRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun load(userId: String, date: LocalDate): CheckInLoadResult = try {
        val record = fetch(userId, date.toString())
        store(userId, date.toString(), record)
        CheckInLoadResult.Success(record, isOffline = false)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        try {
            val row = cache.get(userId, date.toString())
                ?: return CheckInLoadResult.Failure("Unable to load your check-in. Check your connection and retry.")
            val payload = cipher.decrypt(EncryptedCheckInValue(row.payloadCiphertext, row.payloadIv))
            CheckInLoadResult.Success(json.decodeFromString<CachedCheckInRecord>(payload).record, isOffline = true)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            CheckInLoadResult.Failure("Unable to read your cached check-in. Reconnect and retry.")
        }
    }

    override suspend fun save(record: DailyCheckIn, expected: DailyCheckIn?): CheckInSaveResult {
        val desired = CheckInRules.normalized(record)
        CheckInRules.error(desired)?.let { return CheckInSaveResult.Failure(it) }
        if (expected != null && (expected.id != desired.id || expected.userId != desired.userId || expected.date != desired.date)) {
            return CheckInSaveResult.Failure("Reload the current check-in before saving.")
        }
        return try {
            val payload = CheckInRules.payload(desired)
            val saved = if (expected == null) {
                try {
                    requireClient().from("daily_checkins").insert(payload) { select(COLUMNS) }.decodeSingle<DailyCheckIn>()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    if (!generateSequence(error as Throwable?) { it.cause }.any { HealthWriteRules.isDuplicateKeyError(it.message) }) throw error
                    return resolveReplay(desired)
                }
            } else {
                requireClient().from("daily_checkins").update(payload) {
                    select(COLUMNS)
                    filter {
                        eq("id", expected.id)
                        eq("user_id", expected.userId)
                        eq("date", expected.date)
                        eq("updated_at", expected.updatedAt)
                    }
                }.decodeList<DailyCheckIn>().firstOrNull() ?: return resolveReplay(desired)
            }
            store(saved.userId, saved.date, saved)
            CheckInSaveResult.Saved(saved)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            CheckInSaveResult.Failure("Your check-in was not saved. Check your connection and retry.")
        }
    }

    override suspend fun clear(userId: String) = cache.clearForUser(userId)

    override suspend fun cacheGeneratedReview(record: DailyCheckIn) = store(record.userId, record.date, record)

    private suspend fun resolveReplay(desired: DailyCheckIn): CheckInSaveResult {
        val current = fetch(desired.userId, desired.date)
        if (current == null || !CheckInRules.sameContent(current, desired)) return CheckInSaveResult.Conflict
        store(current.userId, current.date, current)
        return CheckInSaveResult.Saved(current)
    }

    private suspend fun fetch(userId: String, date: String): DailyCheckIn? = requireClient().from("daily_checkins").select(COLUMNS) {
        filter { eq("user_id", userId); eq("date", date) }
        limit(1)
    }.decodeList<DailyCheckIn>().firstOrNull()

    private suspend fun store(userId: String, date: String, record: DailyCheckIn?) {
        val encrypted = cipher.encrypt(json.encodeToString(CachedCheckInRecord(record)))
        cache.upsert(CachedCheckInEntity(userId, date, encrypted.ciphertext, encrypted.iv))
    }

    private fun requireClient() = checkNotNull(clientProvider.client)

    private companion object {
        val COLUMNS = Columns.list("id", "user_id", "date", "tasks", "evening_text", "ai_verdict", "updated_at")
    }
}

@Serializable
private data class CachedCheckInRecord(val record: DailyCheckIn?)
