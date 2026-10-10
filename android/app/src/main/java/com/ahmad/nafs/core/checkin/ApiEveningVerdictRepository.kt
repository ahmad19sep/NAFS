package com.ahmad.nafs.core.checkin

import com.ahmad.nafs.core.time.ServerTimestamp
import com.ahmad.nafs.core.network.ApiFailure
import com.ahmad.nafs.core.network.NafsApiClient
import com.ahmad.nafs.core.network.NafsApiException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

@Singleton
class ApiEveningVerdictRepository @Inject constructor(
    private val api: NafsApiClient,
    private val checkIns: CheckInRepository,
) : EveningVerdictRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun generate(userId: String, intent: EveningVerdictIntent): EveningVerdictResult = try {
        val response = api.post(userId, "/api/ai/evening-verdict", json.encodeToJsonElement(intent).jsonObject)
        val result = json.decodeFromJsonElement(VerdictResponse.serializer(), response)
        if (!EveningVerdictRules.matches(userId, intent, result.checkin, result.verdict)) {
            throw NafsApiException(ApiFailure.INVALID_RESPONSE)
        }
        // The successful response already represents a guarded server write.
        // Cache failure must not be shown as an unsuccessful generation.
        try { checkIns.cacheGeneratedReview(result.checkin) } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) { /* Online result remains authoritative. */ }
        EveningVerdictResult.Saved(result.checkin)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: NafsApiException) {
        EveningVerdictResult.Failure(error.failure, error.message.orEmpty())
    } catch (_: Exception) {
        val error = NafsApiException(ApiFailure.INVALID_RESPONSE)
        EveningVerdictResult.Failure(error.failure, error.message.orEmpty())
    }
}

object EveningVerdictRules {
    fun matches(userId: String, intent: EveningVerdictIntent, record: DailyCheckIn, verdict: String): Boolean =
        record.userId == userId && record.id == intent.checkInId && record.date == intent.date &&
            record.tasks == intent.tasks && record.eveningText?.trim() == intent.eveningText.trim() &&
            verdict.isNotBlank() && verdict.length <= 30_000 && record.aiVerdict == verdict &&
            CheckInRules.error(record) == null && runCatching {
                ServerTimestamp.parse(record.updatedAt) > ServerTimestamp.parse(intent.expectedUpdatedAt)
            }.getOrDefault(false)
}

@Serializable
private data class VerdictResponse(val verdict: String, val checkin: DailyCheckIn)
