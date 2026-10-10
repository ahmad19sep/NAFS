package com.ahmad.nafs.core.challenges

import com.ahmad.nafs.core.database.CachedChallengeCheckInEntity
import com.ahmad.nafs.core.database.CachedChallengeDao
import com.ahmad.nafs.core.database.CachedChallengeEntity
import com.ahmad.nafs.core.network.SupabaseClientProvider
import com.ahmad.nafs.domain.challenges.ChallengeCadence
import com.ahmad.nafs.domain.challenges.ChallengeFrequency
import com.ahmad.nafs.domain.challenges.ChallengeStatus
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Singleton
class SupabaseChallengeRepository @Inject constructor(
    private val clientProvider: SupabaseClientProvider,
    private val cache: CachedChallengeDao,
) : ChallengeRepository {
    override suspend fun restart(userId: String, challenge: Challenge, date: LocalDate): ChallengeActionResult = try {
        require(challenge.userId == userId)
        val dto = requireClient().postgrest.rpc("native_restart_challenge", buildJsonObject {
            put("p_id", challenge.id); put("p_expected_count", challenge.restartCount); put("p_date", date.toString())
        }).decodeAs<ChallengeDto>()
        val saved = dto.toModel(fetchCheckIns(dto.id).map(ChallengeCheckInDto::toModel))
        cache.replaceChallenge(userId, saved.toEntity(), saved.checkIns.map { it.toEntity(userId) })
        ChallengeActionResult.Success(saved)
    } catch (cancel: CancellationException) { throw cancel }
    catch (_: Exception) { ChallengeActionResult.Failure("Restart could not be confirmed. Reload before retrying. Existing check-ins are retained.") }
    override suspend fun load(userId: String): ChallengeLoadResult = try {
        val challengeDtos = requireClient().from("challenges").select(CHALLENGE_COLUMNS) {
            filter { eq("user_id", userId) }
        }.decodeList<ChallengeDto>()
        val checkInDtos = requireClient().from("challenge_checkins")
            .select(CHECK_IN_COLUMNS)
            .decodeList<ChallengeCheckInDto>()
        val challenges = assembleChallenges(challengeDtos, checkInDtos)
        cache.replaceForUser(
            userId,
            challenges.map(Challenge::toEntity),
            challenges.flatMap { challenge ->
                challenge.checkIns.map { it.toEntity(userId) }
            },
            System.currentTimeMillis(),
        )
        ChallengeLoadResult.Success(challenges.sortedByDescending(Challenge::createdAt), isOffline = false)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        loadCached(userId)
    }

    override suspend fun create(intent: SaveChallengeIntent): ChallengeActionResult {
        val normalized = intent.normalized()
        ChallengeDefinitionValidator.validationError(normalized)?.let {
            return ChallengeActionResult.Failure(it)
        }
        return try {
            val created = requireClient().from("challenges")
                .insert(normalized.toInsertDto()) { select(CHALLENGE_COLUMNS) }
                .decodeSingle<ChallengeDto>()
            val challenge = created.toModel(emptyList())
            cache.replaceChallenge(normalized.userId, challenge.toEntity(), emptyList())
            ChallengeActionResult.Success(challenge)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            resolveCreateRetry(normalized)
        }
    }

    override suspend fun update(intent: SaveChallengeIntent): ChallengeActionResult {
        val normalized = intent.normalized()
        ChallengeDefinitionValidator.validationError(normalized)?.let {
            return ChallengeActionResult.Failure(it)
        }
        val challengeId = normalized.challengeId
            ?: return ChallengeActionResult.Failure("That challenge is no longer available.")
        return challengeAction {
            val current = fetchChallenge(normalized.userId, challengeId)
                ?: return@challengeAction ChallengeActionResult.Failure(
                    "That challenge is no longer available.",
                )
            val updatedDto = requireClient().from("challenges")
                .update(normalized.toUpdateDto()) {
                    select(CHALLENGE_COLUMNS)
                    filter {
                        eq("id", challengeId)
                        eq("user_id", normalized.userId)
                    }
                }
                .decodeSingle<ChallengeDto>()
            val challenge = updatedDto.toModel(current.checkIns)
            cache.replaceChallenge(
                normalized.userId,
                challenge.toEntity(),
                challenge.checkIns.map { it.toEntity(normalized.userId) },
            )
            ChallengeActionResult.Success(challenge)
        }
    }

    override suspend fun checkIn(
        userId: String,
        challengeId: String,
        date: LocalDate,
        completed: Boolean,
        evidence: ChallengeEvidence?,
        sadqaPaid: Boolean,
    ): ChallengeActionResult = challengeAction {
        val current = fetchChallenge(userId, challengeId)
            ?: return@challengeAction ChallengeActionResult.Failure(
                "That challenge is no longer available.",
            )
        if (current.status != ChallengeStatus.ACTIVE) {
            return@challengeAction ChallengeActionResult.Failure("This challenge is already complete.")
        }
        if (completed && current.requiresPhoto && evidence == null) {
            return@challengeAction ChallengeActionResult.Failure(
                "Choose a proof photo before marking this challenge done.",
            )
        }
        if (date.isBefore(current.startDate)) {
            return@challengeAction ChallengeActionResult.Failure("A check-in cannot predate the challenge.")
        }

        val photoUrl = if (completed && evidence != null) {
            uploadEvidence(userId, challengeId, date, evidence)
        } else {
            null
        }
        requireClient().from("challenge_checkins").upsert(
            ChallengeCheckInUpsertDto(
                challengeId = challengeId,
                date = date.toString(),
                completed = completed,
                photoUrl = photoUrl,
                sadqaPaid = !completed && current.sadqaAmount != null && sadqaPaid,
            ),
        ) {
            onConflict = "challenge_id,date"
        }
        val checkIns = fetchCheckIns(challengeId).map(ChallengeCheckInDto::toModel)
        val streak = ChallengeCadence.periodStreak(
            current.frequency,
            current.startDate,
            checkIns.filter { !it.date.isBefore(current.startDate) }.map(ChallengeCheckIn::dated),
            date,
        )
        val completedChallenge = ChallengeCadence.unitsElapsed(
            current.frequency,
            current.startDate,
            date,
        ) >= ChallengeCadence.totalUnits(current.frequency, current.durationDays)
        requireClient().from("challenges").update(
            ChallengeProgressDto(
                currentStreak = streak,
                longestStreak = maxOf(current.longestStreak, streak),
                status = if (completedChallenge) "completed" else "active",
            ),
        ) {
            filter {
                eq("id", challengeId)
                eq("user_id", userId)
            }
        }
        val challenge = fetchChallenge(userId, challengeId)
            ?: return@challengeAction ChallengeActionResult.Failure(
                "Check-in saved, but the challenge could not be reloaded.",
            )
        cache.replaceChallenge(
            userId,
            challenge.toEntity(),
            challenge.checkIns.map { it.toEntity(userId) },
        )
        ChallengeActionResult.Success(challenge)
    }

    override suspend fun setSadqaPaid(
        userId: String,
        challengeId: String,
        date: LocalDate,
        paid: Boolean,
    ): ChallengeActionResult = challengeAction {
        val current = fetchChallenge(userId, challengeId)
            ?: return@challengeAction ChallengeActionResult.Failure(
                "That challenge is no longer available.",
            )
        if (current.sadqaAmount == null) {
            return@challengeAction ChallengeActionResult.Failure(
                "This challenge has no sadqa pledge.",
            )
        }
        val checkIn = current.checkIns.firstOrNull { it.date == date && !it.completed }
            ?: return@challengeAction ChallengeActionResult.Failure(
                "Record a missed check-in before updating sadqa.",
            )
        requireClient().from("challenge_checkins").update(SadqaPaidDto(paid)) {
            filter {
                eq("id", checkIn.id)
                eq("challenge_id", challengeId)
            }
        }
        val challenge = fetchChallenge(userId, challengeId)
            ?: return@challengeAction ChallengeActionResult.Failure(
                "Sadqa status saved, but the challenge could not be reloaded.",
            )
        cache.replaceChallenge(
            userId,
            challenge.toEntity(),
            challenge.checkIns.map { it.toEntity(userId) },
        )
        ChallengeActionResult.Success(challenge)
    }

    override suspend fun delete(userId: String, challengeId: String): ChallengeActionResult =
        challengeAction {
            requireClient().from("challenges").delete {
                filter {
                    eq("id", challengeId)
                    eq("user_id", userId)
                }
            }
            cache.deleteChallenge(userId, challengeId)
            ChallengeActionResult.Success()
        }

    override suspend fun clear(userId: String) = cache.clearForUser(userId)

    private suspend fun resolveCreateRetry(intent: SaveChallengeIntent): ChallengeActionResult = try {
        val existing = findChallengeDto(intent.userId, intent.requestId)
            ?: return ChallengeActionResult.Failure(
                "Challenge not saved. Check your connection and retry safely.",
            )
        if (!existing.matches(intent)) {
            return ChallengeActionResult.Failure(
                "This save request was already used for different challenge details.",
            )
        }
        val challenge = existing.toModel(fetchCheckIns(existing.id).map(ChallengeCheckInDto::toModel))
        cache.replaceChallenge(
            intent.userId,
            challenge.toEntity(),
            challenge.checkIns.map { it.toEntity(intent.userId) },
        )
        ChallengeActionResult.Success(challenge)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        ChallengeActionResult.Failure("Challenge not saved. Check your connection and retry safely.")
    }

    private suspend fun fetchChallenge(userId: String, challengeId: String): Challenge? {
        val dto = findChallengeDto(userId, challengeId) ?: return null
        return dto.toModel(fetchCheckIns(challengeId).map(ChallengeCheckInDto::toModel))
    }

    private suspend fun findChallengeDto(userId: String, challengeId: String): ChallengeDto? =
        requireClient().from("challenges").select(CHALLENGE_COLUMNS) {
            filter {
                eq("id", challengeId)
                eq("user_id", userId)
            }
            limit(1)
        }.decodeList<ChallengeDto>().firstOrNull()

    private suspend fun fetchCheckIns(challengeId: String): List<ChallengeCheckInDto> =
        requireClient().from("challenge_checkins").select(CHECK_IN_COLUMNS) {
            filter { eq("challenge_id", challengeId) }
        }.decodeList()

    private suspend fun uploadEvidence(
        userId: String,
        challengeId: String,
        date: LocalDate,
        evidence: ChallengeEvidence,
    ): String {
        val path = "$userId/challenges/$challengeId-$date.${evidence.extension}"
        val bucket = requireClient().storage.from(CHALLENGE_PHOTOS_BUCKET)
        bucket.upload(path, evidence.bytes) {
            upsert = true
            contentType = ContentType.parse(evidence.mimeType)
        }
        return bucket.publicUrl(path)
    }

    private suspend fun loadCached(userId: String): ChallengeLoadResult = try {
        if (cache.getMetadata(userId) == null) {
            ChallengeLoadResult.Failure("Unable to load challenges. Check your connection and try again.")
        } else {
            val checkIns = cache.getCheckIns(userId).map(CachedChallengeCheckInEntity::toModel)
            val byChallenge = checkIns.groupBy(ChallengeCheckIn::challengeId)
            val challenges = cache.getChallenges(userId).map {
                it.toModel(byChallenge[it.id].orEmpty())
            }
            ChallengeLoadResult.Success(challenges, isOffline = true)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        ChallengeLoadResult.Failure("Unable to load challenges. Check your connection and try again.")
    }

    private suspend fun challengeAction(
        block: suspend () -> ChallengeActionResult,
    ): ChallengeActionResult = try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        ChallengeActionResult.Failure(
            "The challenge was not changed. Check your connection and try again.",
        )
    }

    private fun requireClient() = checkNotNull(clientProvider.client) { "Supabase is not configured." }

    private companion object {
        const val CHALLENGE_PHOTOS_BUCKET = "challenge-photos"
        val CHALLENGE_COLUMNS = Columns.list(
            "id", "user_id", "title", "emoji", "description", "frequency", "duration_days",
            "start_date", "requires_photo", "sadqa_amount", "sadqa_currency", "current_streak",
            "longest_streak", "status", "restart_count", "last_restart_at", "created_at",
        )
        val CHECK_IN_COLUMNS = Columns.list(
            "id", "challenge_id", "date", "completed", "photo_url", "sadqa_paid", "created_at",
        )
    }
}

@Serializable
private data class ChallengeDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val title: String,
    val emoji: String = "\uD83C\uDFAF",
    val description: String? = null,
    val frequency: String = "daily",
    @SerialName("duration_days") val durationDays: Int = 21,
    @SerialName("start_date") val startDate: String,
    @SerialName("requires_photo") val requiresPhoto: Boolean = false,
    @SerialName("sadqa_amount") val sadqaAmount: Double? = null,
    @SerialName("sadqa_currency") val sadqaCurrency: String = "PKR",
    @SerialName("current_streak") val currentStreak: Int = 0,
    @SerialName("longest_streak") val longestStreak: Int = 0,
    val status: String = "active",
    @SerialName("restart_count") val restartCount: Int = 0,
    @SerialName("last_restart_at") val lastRestartAt: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
private data class ChallengeCheckInDto(
    val id: String,
    @SerialName("challenge_id") val challengeId: String,
    val date: String,
    val completed: Boolean = false,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("sadqa_paid") val sadqaPaid: Boolean = false,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
private data class ChallengeInsertDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val title: String,
    val emoji: String,
    val description: String,
    val frequency: String,
    @SerialName("duration_days") val durationDays: Int,
    @SerialName("start_date") val startDate: String,
    @SerialName("requires_photo") val requiresPhoto: Boolean,
    @SerialName("sadqa_amount") val sadqaAmount: Double?,
    @SerialName("sadqa_currency") val sadqaCurrency: String,
    @SerialName("current_streak") val currentStreak: Int,
    @SerialName("longest_streak") val longestStreak: Int,
    val status: String,
    @SerialName("restart_count") val restartCount: Int,
)

@Serializable
private data class ChallengeUpdateDto(
    val title: String,
    val emoji: String,
    val description: String,
    val frequency: String,
    @SerialName("duration_days") val durationDays: Int,
    @SerialName("requires_photo") val requiresPhoto: Boolean,
    @SerialName("sadqa_amount") val sadqaAmount: Double?,
    @SerialName("sadqa_currency") val sadqaCurrency: String,
)

@Serializable
private data class ChallengeCheckInUpsertDto(
    @SerialName("challenge_id") val challengeId: String,
    val date: String,
    val completed: Boolean,
    @SerialName("photo_url") val photoUrl: String?,
    @SerialName("sadqa_paid") val sadqaPaid: Boolean,
)

@Serializable
private data class ChallengeProgressDto(
    @SerialName("current_streak") val currentStreak: Int,
    @SerialName("longest_streak") val longestStreak: Int,
    val status: String,
)

@Serializable
private data class SadqaPaidDto(
    @SerialName("sadqa_paid") val sadqaPaid: Boolean,
)

private fun assembleChallenges(
    challenges: List<ChallengeDto>,
    checkIns: List<ChallengeCheckInDto>,
): List<Challenge> {
    val byChallenge = checkIns.map(ChallengeCheckInDto::toModel)
        .groupBy(ChallengeCheckIn::challengeId)
    return challenges.map { it.toModel(byChallenge[it.id].orEmpty()) }
}

private fun ChallengeDto.toModel(checkIns: List<ChallengeCheckIn>) = Challenge(
    id = id,
    userId = userId,
    title = title,
    emoji = emoji,
    description = description.orEmpty(),
    frequency = challengeFrequency(frequency),
    durationDays = durationDays,
    startDate = LocalDate.parse(startDate),
    requiresPhoto = requiresPhoto,
    sadqaAmount = sadqaAmount,
    sadqaCurrency = sadqaCurrency,
    currentStreak = currentStreak,
    longestStreak = longestStreak,
    status = challengeStatus(status),
    restartCount = restartCount,
    lastRestartAt = lastRestartAt,
    checkIns = checkIns.sortedByDescending(ChallengeCheckIn::date),
    createdAt = createdAt,
)

private fun ChallengeCheckInDto.toModel() = ChallengeCheckIn(
    id,
    challengeId,
    LocalDate.parse(date),
    completed,
    photoUrl,
    sadqaPaid,
    createdAt,
)

private fun SaveChallengeIntent.toInsertDto() = ChallengeInsertDto(
    id = requestId,
    userId = userId,
    title = title,
    emoji = emoji,
    description = description,
    frequency = frequency.name.lowercase(),
    durationDays = durationDays,
    startDate = startDate.toString(),
    requiresPhoto = requiresPhoto,
    sadqaAmount = sadqaAmount,
    sadqaCurrency = sadqaCurrency,
    currentStreak = 0,
    longestStreak = 0,
    status = "active",
    restartCount = 0,
)

private fun SaveChallengeIntent.toUpdateDto() = ChallengeUpdateDto(
    title,
    emoji,
    description,
    frequency.name.lowercase(),
    durationDays,
    requiresPhoto,
    sadqaAmount,
    sadqaCurrency,
)

private fun ChallengeDto.matches(intent: SaveChallengeIntent): Boolean =
    userId == intent.userId &&
        title == intent.title &&
        emoji == intent.emoji &&
        description.orEmpty() == intent.description &&
        challengeFrequency(frequency) == intent.frequency &&
        durationDays == intent.durationDays &&
        startDate == intent.startDate.toString() &&
        requiresPhoto == intent.requiresPhoto &&
        sadqaAmount == intent.sadqaAmount &&
        sadqaCurrency == intent.sadqaCurrency

private fun SaveChallengeIntent.normalized() = copy(
    title = title.trim(),
    emoji = emoji.trim().ifEmpty { "\uD83C\uDFAF" },
    description = description.trim(),
    sadqaCurrency = sadqaCurrency.trim().uppercase(),
)

private fun Challenge.toEntity() = CachedChallengeEntity(
    id,
    userId,
    title,
    emoji,
    description,
    frequency.name,
    durationDays,
    startDate.toString(),
    requiresPhoto,
    sadqaAmount,
    sadqaCurrency,
    currentStreak,
    longestStreak,
    status.name,
    restartCount,
    lastRestartAt,
    createdAt,
)

private fun ChallengeCheckIn.toEntity(userId: String) = CachedChallengeCheckInEntity(
    id,
    userId,
    challengeId,
    date.toString(),
    completed,
    photoUrl,
    sadqaPaid,
    createdAt,
)

private fun CachedChallengeEntity.toModel(checkIns: List<ChallengeCheckIn>) = Challenge(
    id,
    userId,
    title,
    emoji,
    description,
    challengeFrequency(frequency),
    durationDays,
    LocalDate.parse(startDate),
    requiresPhoto,
    sadqaAmount,
    sadqaCurrency,
    currentStreak,
    longestStreak,
    challengeStatus(status),
    restartCount,
    lastRestartAt,
    checkIns,
    createdAt,
)

private fun CachedChallengeCheckInEntity.toModel() = ChallengeCheckIn(
    id,
    challengeId,
    LocalDate.parse(date),
    completed,
    photoUrl,
    sadqaPaid,
    createdAt,
)

private fun challengeFrequency(value: String): ChallengeFrequency =
    runCatching { ChallengeFrequency.valueOf(value.uppercase()) }
        .getOrDefault(ChallengeFrequency.DAILY)

private fun challengeStatus(value: String): ChallengeStatus =
    runCatching { ChallengeStatus.valueOf(value.uppercase()) }
        .getOrDefault(ChallengeStatus.ACTIVE)
