package com.ahmad.nafs.core.letters

import com.ahmad.nafs.core.network.ApiFailure
import com.ahmad.nafs.core.network.NafsApiClient
import com.ahmad.nafs.core.network.NafsApiException
import com.ahmad.nafs.core.reports.ReportRules
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

@Serializable
data class LetterReplyIntent(val letter: FutureLetter, val requestId: String)

sealed interface LetterReplyResult {
    data class Saved(val letter: FutureLetter, val localCleanupFailed: Boolean = false,
        val contextPartial: Boolean = false, val historySaved: Boolean? = null) : LetterReplyResult
    data class Failure(val message: String, val pending: Boolean) : LetterReplyResult
}

@Serializable
data class LetterReplyResponse(val reply: String, val letter: FutureLetter,
    @SerialName("context_partial") val contextPartial: Boolean = false,
    @SerialName("history_saved") val historySaved: Boolean? = null)

fun interface LetterReplyRemote {
    suspend fun generate(userId: String, intent: LetterReplyIntent): LetterReplyResponse
}

@Singleton
class ApiLetterReplyRemote @Inject constructor(private val api: NafsApiClient) : LetterReplyRemote {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    override suspend fun generate(userId: String, intent: LetterReplyIntent): LetterReplyResponse {
        val body = Request(intent.letter.id, LetterReplyRules.fingerprint(intent.letter), intent.requestId)
        val response = api.post(userId, "/api/ai/future-self", json.encodeToJsonElement(body).jsonObject)
        val result = json.decodeFromJsonElement(LetterReplyResponse.serializer(), response)
        if (!LetterReplyRules.matches(userId, intent, result.letter) || result.reply != result.letter.aiReplyText || result.reply.length > 30_000) {
            throw NafsApiException(ApiFailure.INVALID_RESPONSE)
        }
        return result
    }
    @Serializable private data class Request(@SerialName("letter_id") val letterId: String,
        @SerialName("expected_fingerprint") val fingerprint: String, @SerialName("request_id") val requestId: String)
}

object LetterReplyRules {
    fun fingerprint(letter: FutureLetter): String {
        val hash = MessageDigest.getInstance("SHA-256")
        listOf(letter.id, letter.userId, letter.content, letter.writtenAt, letter.targetDate).forEach { field ->
            val bytes = field.toByteArray(Charsets.UTF_8)
            hash.update(ByteBuffer.allocate(4).putInt(bytes.size).array())
            hash.update(bytes)
        }
        return hash.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
    fun validIntent(userId: String, intent: LetterReplyIntent): Boolean = ReportRules.validId(intent.requestId) &&
        LetterRules.validLetter(userId, intent.letter) && intent.letter.content.isNotBlank() &&
        intent.letter.aiReplyText == null && intent.letter.deliveredAt == null
    fun eligible(letter: FutureLetter, today: LocalDate): Boolean = letter.content.isNotBlank() &&
        letter.aiReplyText == null && letter.deliveredAt == null && LocalDate.parse(letter.targetDate) <= today
    fun saved(letter: FutureLetter): Boolean = !letter.aiReplyText.isNullOrBlank() && letter.deliveredAt != null
    fun matches(userId: String, intent: LetterReplyIntent, letter: FutureLetter): Boolean =
        LetterRules.validLetter(userId, letter) && fingerprint(letter) == fingerprint(intent.letter) && saved(letter)
    fun message(failure: ApiFailure): String = when (failure) {
        ApiFailure.AUTHENTICATION -> "Sign in again before requesting a letter reply."
        ApiFailure.CONFIGURATION -> "Future-self replies need a backend update. Your letter is saved."
        ApiFailure.CONFLICT -> "This letter changed or is not ready yet. Reload its saved version."
        ApiFailure.IN_PROGRESS -> "Your reply is still being generated. Wait a moment, then retry or reload."
        ApiFailure.RATE_LIMITED -> "Too many AI requests. Wait a moment and retry."
        ApiFailure.TIMEOUT, ApiFailure.RESULT_UNCONFIRMED -> "The reply could not be confirmed. Reload your letter before retrying."
        ApiFailure.NETWORK -> "Unable to reach the coach. Check your connection, then reload or retry."
        ApiFailure.INVALID_REQUEST -> "Open a valid saved letter whose revisit date has arrived."
        ApiFailure.INVALID_RESPONSE -> "The backend returned an incompatible reply. Reload your saved letter; the backend may need an update."
        ApiFailure.UNAVAILABLE -> "The coach is temporarily unavailable. Your letter and request are retained."
    }
}
