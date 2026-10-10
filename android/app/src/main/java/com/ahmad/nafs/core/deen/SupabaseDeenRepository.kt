package com.ahmad.nafs.core.deen

import com.ahmad.nafs.core.database.CachedDeenDao
import com.ahmad.nafs.core.database.CachedDeenLogEntity
import com.ahmad.nafs.core.health.HealthWriteRules
import com.ahmad.nafs.core.network.SupabaseClientProvider
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

@Singleton
class SupabaseDeenRepository @Inject constructor(
    private val clientProvider: SupabaseClientProvider,
    private val cache: CachedDeenDao,
    private val cipher: DeenCacheCipher,
) : DeenRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun load(userId: String, date: LocalDate): DeenLoadResult = try {
        val log = fetch(userId, date.toString())
        store(userId, date.toString(), log)
        DeenLoadResult.Success(log, isOffline = false)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        try {
            val row = cache.get(userId, date.toString())
                ?: return DeenLoadResult.Failure("Unable to load prayers. Check your connection and retry.")
            val payload = cipher.decrypt(EncryptedDeenValue(row.payloadCiphertext, row.payloadIv))
            DeenLoadResult.Success(json.decodeFromString<CachedPrayerRecord>(payload).log, isOffline = true)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            DeenLoadResult.Failure("Unable to read cached prayers. Reconnect and retry.")
        }
    }

    override suspend fun save(log: PrayerLog, expected: PrayerLog?): DeenSaveResult {
        DeenValidator.error(log)?.let { return DeenSaveResult.Failure(it) }
        if (expected != null && (expected.userId != log.userId || expected.date != log.date || expected.id != log.id)) {
            return DeenSaveResult.Failure("Reload the current prayer record before saving.")
        }
        return try {
            val payload = DeenWriteRules.payload(log)
            val saved = if (expected == null) {
                try {
                    requireClient().from("prayer_logs").insert(payload) { select(COLUMNS) }.decodeSingle<PrayerLog>()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    if (!generateSequence(error as Throwable?) { it.cause }.any { HealthWriteRules.isDuplicateKeyError(it.message) }) throw error
                    return resolveReplay(log)
                }
            } else {
                requireClient().from("prayer_logs").update(payload) {
                    select(COLUMNS)
                    filter {
                        eq("id", expected.id)
                        eq("user_id", log.userId)
                        eq("date", log.date)
                        eq("fajr", expected.fajr)
                        eq("dhuhr", expected.dhuhr)
                        eq("asr", expected.asr)
                        eq("maghrib", expected.maghrib)
                        eq("isha", expected.isha)
                        eq("extra_prayers", JsonArray(expected.extras).toString())
                    }
                }.decodeList<PrayerLog>().firstOrNull() ?: return resolveReplay(log)
            }
            store(log.userId, log.date, saved)
            DeenSaveResult.Saved(saved)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            DeenSaveResult.Failure("Prayers were not saved. Check your connection and retry.")
        }
    }

    override suspend fun clear(userId: String) = cache.clearForUser(userId)

    override suspend fun history(userId: String, from: LocalDate, to: LocalDate): DeenHistoryResult {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > MAX_HISTORY_DAYS) {
            return DeenHistoryResult.Failure("Choose a shorter range of days.")
        }
        val days = try {
            requireClient().from("prayer_logs").select(HISTORY_COLUMNS) {
                filter { eq("user_id", userId); gte("date", from.toString()); lte("date", to.toString()) }
                order("date", Order.ASCENDING)
                limit(MAX_HISTORY_DAYS + 1)
            }.decodeList<PrayerHistoryDay>()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return cachedHistory(userId, from, to)
        }
        // The saved copy only serves offline reads; failing to write it must not hide fresh history.
        try {
            storeHistory(userId, from, to, days)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
        }
        return DeenHistoryResult.Success(days, from, to, isOffline = false)
    }

    /** One encrypted snapshot per account, merged with what was saved before so a short read keeps a longer history. */
    private suspend fun storeHistory(userId: String, from: LocalDate, to: LocalDate, days: List<PrayerHistoryDay>) {
        val previous = readHistory(userId)?.takeIf { saved ->
            // Only a snapshot that overlaps or touches this range can be joined without leaving an unknown gap.
            !LocalDate.parse(saved.from).isAfter(to.plusDays(1)) && !LocalDate.parse(saved.to).isBefore(from.minusDays(1))
        }
        val start = listOfNotNull(from, previous?.let { LocalDate.parse(it.from) }).min()
        val end = listOfNotNull(to, previous?.let { LocalDate.parse(it.to) }).max()
        val kept = previous?.days.orEmpty().filter { day -> LocalDate.parse(day.date).let { it.isBefore(from) || it.isAfter(to) } }
        val oldest = maxOf(start, end.minusDays(MAX_HISTORY_DAYS))
        val merged = (kept + days).filter { !LocalDate.parse(it.date).isBefore(oldest) }.sortedBy { it.date }
        val encrypted = cipher.encrypt(json.encodeToString(CachedPrayerHistory(oldest.toString(), end.toString(), merged)))
        cache.upsert(CachedDeenLogEntity(userId, HISTORY_KEY, encrypted.ciphertext, encrypted.iv))
    }

    private suspend fun readHistory(userId: String): CachedPrayerHistory? {
        val row = cache.get(userId, HISTORY_KEY) ?: return null
        return json.decodeFromString<CachedPrayerHistory>(cipher.decrypt(EncryptedDeenValue(row.payloadCiphertext, row.payloadIv)))
    }

    private suspend fun cachedHistory(userId: String, from: LocalDate, to: LocalDate): DeenHistoryResult = try {
        val saved = readHistory(userId)
        val start = saved?.let { maxOf(from, LocalDate.parse(it.from)) }
        val end = saved?.let { minOf(to, LocalDate.parse(it.to)) }
        if (saved == null || start == null || end == null || end.isBefore(start)) {
            DeenHistoryResult.Failure("Prayer history needs a connection. Reconnect and retry.")
        } else {
            val days = saved.days.filter { day -> LocalDate.parse(day.date).let { !it.isBefore(start) && !it.isAfter(end) } }
            DeenHistoryResult.Success(days, start, end, isOffline = true)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        DeenHistoryResult.Failure("Saved prayer history could not be read. Reconnect and retry.")
    }

    private suspend fun resolveReplay(desired: PrayerLog): DeenSaveResult {
        val current = fetch(desired.userId, desired.date)
        if (current == null || !DeenWriteRules.sameContent(current, desired)) return DeenSaveResult.Conflict
        store(current.userId, current.date, current)
        return DeenSaveResult.Saved(current)
    }

    private suspend fun fetch(userId: String, date: String): PrayerLog? = requireClient().from("prayer_logs").select(COLUMNS) {
        filter { eq("user_id", userId); eq("date", date) }
        limit(1)
    }.decodeList<PrayerLog>().firstOrNull()

    private suspend fun store(userId: String, date: String, log: PrayerLog?) {
        val encrypted = cipher.encrypt(json.encodeToString(CachedPrayerRecord(log)))
        cache.upsert(CachedDeenLogEntity(userId, date, encrypted.ciphertext, encrypted.iv))
    }

    private fun requireClient() = checkNotNull(clientProvider.client)

    private companion object {
        val COLUMNS = Columns.list("id", "user_id", "date", "fajr", "dhuhr", "asr", "maghrib", "isha", "extra_prayers")
        val HISTORY_COLUMNS = Columns.list("date", "fajr", "dhuhr", "asr", "maghrib", "isha")

        /** Rows are keyed by ISO date; this key is never a date, so the history snapshot cannot replace a day. */
        const val HISTORY_KEY = "history"
        const val MAX_HISTORY_DAYS = 400L
    }
}

object DeenWriteRules {
    private val json = Json { encodeDefaults = true }
    fun payload(log: PrayerLog): JsonObject = json.encodeToJsonElement(log).jsonObject
    fun sameContent(current: PrayerLog, desired: PrayerLog): Boolean = current.copy(id = desired.id) == desired
}

@Serializable
private data class CachedPrayerRecord(val log: PrayerLog?)

/** History as last loaded online: every saved day from [from] to [to] (ISO dates). */
@Serializable
private data class CachedPrayerHistory(val from: String, val to: String, val days: List<PrayerHistoryDay>)
