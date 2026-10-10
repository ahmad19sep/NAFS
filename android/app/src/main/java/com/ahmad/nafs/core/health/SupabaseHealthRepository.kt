package com.ahmad.nafs.core.health

import com.ahmad.nafs.core.database.CachedHealthDao
import com.ahmad.nafs.core.database.CachedHealthLogEntity
import com.ahmad.nafs.core.database.CachedHealthProfileEntity
import com.ahmad.nafs.core.network.SupabaseClientProvider
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

@Singleton
class SupabaseHealthRepository @Inject constructor(
    private val clientProvider: SupabaseClientProvider,
    private val cache: CachedHealthDao,
    private val cacheCipher: HealthCacheCipher,
) : HealthRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun load(userId: String, date: LocalDate): HealthLoadResult = try {
        val profile = requireClient().from("users").select(PROFILE_COLUMNS) {
            filter { eq("id", userId) }
            limit(1)
        }.decodeList<HealthProfileDto>().firstOrNull()?.toModel()
            ?: return HealthLoadResult.Failure("Your health profile could not be loaded.")
        val log = requireClient().from("health_logs").select(LOG_COLUMNS) {
            filter {
                eq("user_id", userId)
                eq("date", date.toString())
            }
            limit(1)
        }.decodeList<HealthLogDto>().firstOrNull()?.toModel()
        cache.replace(
            userId,
            date.toString(),
            profile.toEntity(cacheCipher, json),
            log?.toEntity(cacheCipher, json),
            System.currentTimeMillis(),
        )
        HealthLoadResult.Success(profile, log, isOffline = false)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        loadCached(userId, date)
    }

    override suspend fun loadRange(userId: String, from: LocalDate, to: LocalDate): HealthRangeResult = try {
        require(!from.isAfter(to) && !from.isBefore(to.minusDays(RANGE_MAX_DAYS - 1L))) { "Unsupported health range" }
        val logs = requireClient().from("health_logs").select(LOG_COLUMNS) {
            filter {
                eq("user_id", userId)
                gte("date", from.toString())
                lte("date", to.toString())
            }
            order("date", Order.ASCENDING)
            limit(40)
        }.decodeList<HealthLogDto>().map { it.toModel() }
            .filter { it.userId == userId && !it.date.isBefore(from) && !it.date.isAfter(to) }
            .sortedBy { it.date }
        cacheRange(userId, logs)
        HealthRangeResult.Success(logs, isOffline = false)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        loadCachedRange(userId, from, to)
    }

    override suspend fun saveProfile(intent: SaveHealthProfileIntent): HealthActionResult {
        HealthValidator.profileError(intent)?.let { return HealthActionResult.Failure(it) }
        return healthAction {
            val profile = requireClient().from("users").update(
                HealthProfileUpdateDto(intent.heightCm, intent.weightKg),
            ) {
                select(PROFILE_COLUMNS)
                filter { eq("id", intent.userId) }
            }.decodeSingle<HealthProfileDto>().toModel()
            cache.replaceProfile(profile.toEntity(cacheCipher, json))
            HealthActionResult.ProfileSaved(profile)
        }
    }

    override suspend fun saveCustomMetrics(intent: SaveCustomMetricsIntent): HealthActionResult {
        val normalized = intent.copy(
            metrics = intent.metrics.map { metric ->
                metric.copy(
                    name = metric.name.trim(),
                    emoji = metric.emoji.trim(),
                    target = metric.target?.takeUnless { metric.type == CustomMetricType.BOOLEAN },
                    unit = metric.unit?.trim()?.takeIf(String::isNotEmpty)
                        ?.takeUnless { metric.type == CustomMetricType.BOOLEAN },
                )
            },
        )
        HealthValidator.customMetricsError(normalized)?.let { return HealthActionResult.Failure(it) }
        return healthAction {
            val profile = requireClient().from("users").update(
                HealthMetricsUpdateDto(normalized.metrics),
            ) {
                select(PROFILE_COLUMNS)
                filter { eq("id", normalized.userId) }
            }.decodeSingle<HealthProfileDto>().toModel()
            cache.replaceProfile(profile.toEntity(cacheCipher, json))
            HealthActionResult.ProfileSaved(profile)
        }
    }

    override suspend fun saveLog(intent: SaveHealthLogIntent): HealthActionResult {
        val normalizedSessions = intent.sleepSessions.map { session ->
            session.copy(start = session.start.trim(), end = session.end.trim())
        }
        val normalized = intent.copy(
            sleepHours = if (normalizedSessions.isEmpty()) {
                intent.sleepHours
            } else {
                SleepSessionRules.hours(normalizedSessions)
            },
            sleepSessions = normalizedSessions,
            meals = intent.meals.map { meal ->
                meal.copy(
                    label = meal.label.trim(),
                    emoji = meal.emoji.trim(),
                    items = meal.items.map { item ->
                        item.copy(name = item.name.trim(), emoji = item.emoji.trim())
                    },
                )
            },
            exerciseMinutes = intent.exerciseMinutes?.takeIf { intent.exerciseDone },
            notes = intent.notes?.trim()?.takeIf(String::isNotEmpty),
        )
        HealthValidator.logError(normalized)?.let { return HealthActionResult.Failure(it) }
        return healthAction {
            val payload = normalized.toDto(Instant.now().toString())
            val log = if (normalized.expectedId == null) {
                try {
                    requireClient().from("health_logs").insert(payload) {
                        select(LOG_COLUMNS)
                    }.decodeSingle<HealthLogDto>().toModel()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    if (error.isDuplicateKeyError()) {
                        return@healthAction HealthActionResult.LogConflict
                    }
                    throw error
                }
            } else {
                requireClient().from("health_logs").update(payload) {
                    select(LOG_COLUMNS)
                    filter {
                        eq("id", normalized.expectedId)
                        eq("user_id", normalized.userId)
                        eq("updated_at", checkNotNull(normalized.expectedUpdatedAt))
                    }
                }.decodeList<HealthLogDto>().firstOrNull()?.toModel()
                    ?: return@healthAction HealthActionResult.LogConflict
            }
            cache.replaceLog(log.toEntity(cacheCipher, json))
            val sync = log.weightKg?.let { syncProfileWeight(log.userId, log.date, it) } ?: WeightSync()
            HealthActionResult.LogSaved(log, profile = sync.profile, profileWeightFailed = sync.failed)
        }
    }

    private data class WeightSync(val profile: HealthProfile? = null, val failed: Boolean = false)

    /**
     * Mirrors the web: a saved daily weight becomes the profile weight so BMI follows it. Editing an older day
     * must not move BMI backwards, so the profile only changes when no later day carries a weight.
     */
    private suspend fun syncProfileWeight(userId: String, date: LocalDate, weightKg: Double): WeightSync {
        val profile = try {
            val laterWeighIn = requireClient().from("health_logs").select(Columns.list("date", "weight_kg")) {
                filter {
                    eq("user_id", userId)
                    gt("date", date.toString())
                }
                limit(400)
            }.decodeList<HealthWeightDto>().any { it.weightKg != null }
            if (laterWeighIn) return WeightSync()
            requireClient().from("users").update(HealthWeightUpdateDto(weightKg)) {
                select(PROFILE_COLUMNS)
                filter { eq("id", userId) }
            }.decodeSingle<HealthProfileDto>().toModel()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return WeightSync(failed = true)
        }
        try {
            cache.replaceProfile(profile.toEntity(cacheCipher, json))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // The server profile is authoritative; the next load refreshes the cached copy.
        }
        return WeightSync(profile = profile)
    }

    private suspend fun cacheRange(userId: String, logs: List<HealthLog>) {
        try {
            if (cache.getMetadata(userId) == null) return
            logs.forEach { log ->
                if (cache.getLog(userId, log.date.toString())?.updatedAt != log.updatedAt) {
                    cache.replaceLog(log.toEntity(cacheCipher, json))
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // Online results stay authoritative even if this device cannot keep a copy.
        }
    }

    private suspend fun loadCachedRange(userId: String, from: LocalDate, to: LocalDate): HealthRangeResult = try {
        if (cache.getMetadata(userId) == null || from.isAfter(to) || from.isBefore(to.minusDays(RANGE_MAX_DAYS - 1L))) {
            HealthRangeResult.Failure(RANGE_OFFLINE_MESSAGE)
        } else {
            val logs = mutableListOf<HealthLog>()
            var date = from
            while (!date.isAfter(to)) {
                cache.getLog(userId, date.toString())?.toModel(cacheCipher, json)?.let(logs::add)
                date = date.plusDays(1)
            }
            HealthRangeResult.Success(logs, isOffline = true)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        HealthRangeResult.Failure(RANGE_OFFLINE_MESSAGE)
    }

    override suspend fun clear(userId: String) = cache.clearForUser(userId)

    private suspend fun loadCached(userId: String, date: LocalDate): HealthLoadResult = try {
        if (cache.getMetadata(userId) == null) {
            HealthLoadResult.Failure("Unable to load health data. Check your connection and retry.")
        } else {
            val profile = cache.getProfile(userId)?.toModel(cacheCipher, json)
                ?: return HealthLoadResult.Failure("Unable to load health data. Check your connection and retry.")
            HealthLoadResult.Success(
                profile = profile,
                log = cache.getLog(userId, date.toString())?.toModel(cacheCipher, json),
                isOffline = true,
            )
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        HealthLoadResult.Failure("Unable to load health data. Check your connection and retry.")
    }

    private suspend fun healthAction(
        block: suspend () -> HealthActionResult,
    ): HealthActionResult = try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        HealthActionResult.Failure("Health data was not saved. Check your connection and retry.")
    }

    private fun requireClient() = checkNotNull(clientProvider.client) { "Supabase is not configured." }

    private companion object {
        const val RANGE_MAX_DAYS = 31
        const val RANGE_OFFLINE_MESSAGE = "Trends need a connection the first time. Reconnect and retry."
        val PROFILE_COLUMNS = Columns.list("id", "height_cm", "weight_kg", "health_extras_config")
        val LOG_COLUMNS = Columns.list(
            "id", "user_id", "date", "water_glasses", "steps", "sleep_hours",
            "sleep_sessions", "sleep_time", "wake_time",
            "meals", "extras",
            "exercise_done", "exercise_minutes", "weight_kg", "mood", "notes",
            "created_at", "updated_at",
        )
    }
}

@Serializable
private data class HealthProfileDto(
    val id: String,
    @SerialName("height_cm") val heightCm: Double? = null,
    @SerialName("weight_kg") val weightKg: Double? = null,
    @SerialName("health_extras_config") val customMetrics: List<CustomHealthMetric> = emptyList(),
)

@Serializable
private data class HealthProfileUpdateDto(
    @SerialName("height_cm") val heightCm: Double,
    @SerialName("weight_kg") val weightKg: Double,
)

@Serializable
private data class HealthWeightUpdateDto(
    @SerialName("weight_kg") val weightKg: Double,
)

@Serializable
private data class HealthWeightDto(
    val date: String,
    @SerialName("weight_kg") val weightKg: Double? = null,
)

@Serializable
private data class HealthMetricsUpdateDto(
    @SerialName("health_extras_config") val customMetrics: List<CustomHealthMetric>,
)

@Serializable
private data class HealthLogDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val date: String,
    @SerialName("water_glasses") val waterGlasses: Int = 0,
    val steps: Int? = null,
    @SerialName("sleep_hours") val sleepHours: Double? = null,
    @SerialName("sleep_sessions") val sleepSessions: List<SleepSession> = emptyList(),
    @SerialName("sleep_time") val sleepTime: String? = null,
    @SerialName("wake_time") val wakeTime: String? = null,
    val meals: List<HealthMeal> = emptyList(),
    val extras: Map<String, JsonPrimitive> = emptyMap(),
    @SerialName("exercise_done") val exerciseDone: Boolean = false,
    @SerialName("exercise_minutes") val exerciseMinutes: Int? = null,
    @SerialName("weight_kg") val weightKg: Double? = null,
    val mood: Int? = null,
    val notes: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
private data class HealthLogUpsertDto(
    @SerialName("user_id") val userId: String,
    val date: String,
    @SerialName("water_glasses") val waterGlasses: Int,
    val steps: Int?,
    @SerialName("sleep_hours") val sleepHours: Double?,
    @SerialName("sleep_sessions") val sleepSessions: List<SleepSession>,
    @SerialName("sleep_time") val sleepTime: String?,
    @SerialName("wake_time") val wakeTime: String?,
    val meals: List<HealthMeal>,
    val extras: Map<String, JsonPrimitive>,
    @SerialName("exercise_done") val exerciseDone: Boolean,
    @SerialName("exercise_minutes") val exerciseMinutes: Int?,
    @SerialName("weight_kg") val weightKg: Double?,
    val mood: Int?,
    val notes: String?,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
private data class HealthProfileCachePayload(
    val heightCm: Double?,
    val weightKg: Double?,
    val customMetrics: List<CustomHealthMetric> = emptyList(),
)

@Serializable
private data class HealthLogCachePayload(
    val waterGlasses: Int,
    val steps: Int?,
    val sleepHours: Double?,
    val sleepSessions: List<SleepSession> = emptyList(),
    val meals: List<HealthMeal> = emptyList(),
    val extras: Map<String, JsonPrimitive> = emptyMap(),
    val exerciseDone: Boolean,
    val exerciseMinutes: Int?,
    val weightKg: Double?,
    val mood: Int?,
    val notes: String?,
)

private fun HealthProfileDto.toModel() = HealthProfile(id, heightCm, weightKg, customMetrics)

private fun HealthLogDto.toModel(): HealthLog {
    val sessions = if (sleepSessions.isNotEmpty()) {
        sleepSessions.map { it.copy(start = it.start.take(5), end = it.end.take(5)) }
    } else if (sleepTime != null && wakeTime != null) {
        listOf(SleepSession("legacy-$date", sleepTime.take(5), wakeTime.take(5)))
    } else {
        emptyList()
    }
    return HealthLog(
        id = id,
        userId = userId,
        date = LocalDate.parse(date),
        waterGlasses = waterGlasses,
        steps = steps,
        sleepHours = sleepHours,
        sleepSessions = sessions,
        meals = meals,
        extras = extras,
        exerciseDone = exerciseDone,
        exerciseMinutes = exerciseMinutes,
        weightKg = weightKg,
        mood = mood,
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

private fun SaveHealthLogIntent.toDto(updatedAt: String) = HealthLogUpsertDto(
    userId,
    date.toString(),
    waterGlasses,
    steps,
    sleepHours,
    sleepSessions,
    sleepSessions.firstOrNull()?.start,
    sleepSessions.lastOrNull()?.end,
    meals,
    extras,
    exerciseDone,
    exerciseMinutes,
    weightKg,
    mood,
    notes,
    updatedAt,
)

private fun Throwable.isDuplicateKeyError(): Boolean = generateSequence(this as Throwable?) { it.cause }
    .any { error ->
        HealthWriteRules.isDuplicateKeyError(error.message) ||
            HealthWriteRules.isDuplicateKeyError(error.toString())
    }

private suspend fun HealthProfile.toEntity(
    cipher: HealthCacheCipher,
    json: Json,
): CachedHealthProfileEntity {
    val encrypted = cipher.encrypt(
        json.encodeToString(HealthProfileCachePayload(heightCm, weightKg, customMetrics)),
    )
    return CachedHealthProfileEntity(userId, encrypted.ciphertext, encrypted.iv)
}

private suspend fun HealthLog.toEntity(
    cipher: HealthCacheCipher,
    json: Json,
): CachedHealthLogEntity {
    val encrypted = cipher.encrypt(
        json.encodeToString(
            HealthLogCachePayload(
                waterGlasses,
                steps,
                sleepHours,
                sleepSessions,
                meals,
                extras,
                exerciseDone,
                exerciseMinutes,
                weightKg,
                mood,
                notes,
            ),
        ),
    )
    return CachedHealthLogEntity(
        userId,
        date.toString(),
        id,
        encrypted.ciphertext,
        encrypted.iv,
        createdAt,
        updatedAt,
    )
}

private suspend fun CachedHealthProfileEntity.toModel(
    cipher: HealthCacheCipher,
    json: Json,
): HealthProfile {
    val payload = json.decodeFromString<HealthProfileCachePayload>(
        cipher.decrypt(EncryptedHealthValue(payloadCiphertext, payloadIv)),
    )
    return HealthProfile(userId, payload.heightCm, payload.weightKg, payload.customMetrics)
}

private suspend fun CachedHealthLogEntity.toModel(
    cipher: HealthCacheCipher,
    json: Json,
): HealthLog {
    val payload = json.decodeFromString<HealthLogCachePayload>(
        cipher.decrypt(EncryptedHealthValue(payloadCiphertext, payloadIv)),
    )
    return HealthLog(
        id,
        userId,
        LocalDate.parse(date),
        payload.waterGlasses,
        payload.steps,
        payload.sleepHours,
        payload.sleepSessions,
        payload.meals,
        payload.extras,
        payload.exerciseDone,
        payload.exerciseMinutes,
        payload.weightKg,
        payload.mood,
        payload.notes,
        createdAt,
        updatedAt,
    )
}
