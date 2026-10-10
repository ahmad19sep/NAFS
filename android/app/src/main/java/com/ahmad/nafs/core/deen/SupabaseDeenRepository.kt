package com.ahmad.nafs.core.deen

import com.ahmad.nafs.core.database.CachedDeenDao
import com.ahmad.nafs.core.database.CachedDeenLogEntity
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
    }
}

object DeenWriteRules {
    private val json = Json { encodeDefaults = true }
    fun payload(log: PrayerLog): JsonObject = json.encodeToJsonElement(log).jsonObject
    fun sameContent(current: PrayerLog, desired: PrayerLog): Boolean = current.copy(id = desired.id) == desired
}

@Serializable
private data class CachedPrayerRecord(val log: PrayerLog?)
