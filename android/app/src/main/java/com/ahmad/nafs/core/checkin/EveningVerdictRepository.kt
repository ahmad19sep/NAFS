package com.ahmad.nafs.core.checkin

import com.ahmad.nafs.core.network.ApiFailure
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EveningVerdictIntent(
    @SerialName("checkin_id") val checkInId: String,
    @SerialName("expected_updated_at") val expectedUpdatedAt: String,
    @SerialName("request_id") val requestId: String,
    val date: String,
    val eveningText: String,
    val tasks: List<CheckInTask>,
)

interface EveningVerdictRepository {
    suspend fun generate(userId: String, intent: EveningVerdictIntent): EveningVerdictResult
}

sealed interface EveningVerdictResult {
    data class Saved(val record: DailyCheckIn) : EveningVerdictResult
    data class Failure(val reason: ApiFailure, val message: String) : EveningVerdictResult
}
