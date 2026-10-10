package com.ahmad.nafs.core.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

enum class ApiFailure {
    AUTHENTICATION, CONFIGURATION, CONFLICT, IN_PROGRESS, RATE_LIMITED, TIMEOUT,
    NETWORK, UNAVAILABLE, RESULT_UNCONFIRMED, INVALID_REQUEST, INVALID_RESPONSE,
}

class NafsApiException(val failure: ApiFailure) : Exception(when (failure) {
    ApiFailure.AUTHENTICATION -> "Your session could not be verified. Sign in again and retry."
    ApiFailure.CONFIGURATION -> "The server is not configured for this action."
    ApiFailure.CONFLICT -> "This record changed. Reload the latest saved entry before retrying."
    ApiFailure.IN_PROGRESS -> "Your request is still being processed. Wait a moment, then retry or reload."
    ApiFailure.RATE_LIMITED -> "Too many requests. Wait a moment and try again."
    ApiFailure.TIMEOUT -> "The request timed out. Reload your saved entry before retrying."
    ApiFailure.NETWORK -> "Unable to reach the server. Check your connection and retry."
    ApiFailure.UNAVAILABLE -> "This action is temporarily unavailable. Your saved records are retained."
    ApiFailure.RESULT_UNCONFIRMED -> "Reload your saved record to recover the result before retrying."
    ApiFailure.INVALID_REQUEST -> "Check the required fields before retrying this action."
    ApiFailure.INVALID_RESPONSE -> "The server returned an incompatible response. It may need an update."
})

interface ApiSessionTokens {
    suspend fun accessToken(userId: String, forceRefresh: Boolean = false): String
    fun isCurrentUser(userId: String): Boolean
}

object ApiEndpointRules {
    fun resolve(baseUrl: String, path: String): String {
        val base = runCatching { URI(baseUrl) }.getOrNull()
            ?: throw NafsApiException(ApiFailure.CONFIGURATION)
        if (base.scheme != "https" || base.host.isNullOrBlank() || base.userInfo != null ||
            base.query != null || base.fragment != null || base.path.orEmpty() !in listOf("", "/")) {
            throw NafsApiException(ApiFailure.CONFIGURATION)
        }
        if (!path.startsWith("/api/") || !Regex("^/api/[A-Za-z0-9/_-]+$").matches(path)) {
            throw NafsApiException(ApiFailure.CONFIGURATION)
        }
        return base.resolve(path).toString()
    }

    fun error(status: Int, code: String?): ApiFailure = when {
        status == 401 || status == 403 -> ApiFailure.AUTHENTICATION
        status == 409 && code == "in_flight" -> ApiFailure.IN_PROGRESS
        status == 409 -> ApiFailure.CONFLICT
        status == 429 -> ApiFailure.RATE_LIMITED
        code == "result_unconfirmed" -> ApiFailure.RESULT_UNCONFIRMED
        code == "not_configured" -> ApiFailure.CONFIGURATION
        status == 400 || status == 413 -> ApiFailure.INVALID_REQUEST
        status in 300..399 || status == 404 -> ApiFailure.INVALID_RESPONSE
        else -> ApiFailure.UNAVAILABLE
    }
}

/** No network/status retries: only one 401 retry, before authenticated work can run. */
class NafsApiClient(
    private val http: HttpClient,
    private val sessions: ApiSessionTokens,
    private val baseUrl: String,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun post(userId: String, path: String, payload: JsonObject): JsonObject {
        val url = ApiEndpointRules.resolve(baseUrl, path)
        try {
            return withTimeoutOrNull(60_000L) {
                for (attempt in 0..1) {
                    val token = sessions.accessToken(userId, forceRefresh = attempt == 1)
                    val response = http.post(url) {
                        bearerAuth(token)
                        contentType(ContentType.Application.Json)
                        setBody(payload.toString())
                    }
                    if (!sessions.isCurrentUser(userId)) throw NafsApiException(ApiFailure.AUTHENTICATION)
                    val body = response.bodyAsText()
                    if (response.status.value == 401 && attempt == 0) continue
                    val objectBody = if (body.length <= 1_000_000) {
                        runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
                    } else null
                    if (response.status.value !in 200..299) {
                        val code = (objectBody?.get("code") as? JsonPrimitive)?.contentOrNull
                        throw NafsApiException(ApiEndpointRules.error(response.status.value, code))
                    }
                    if (!sessions.isCurrentUser(userId)) throw NafsApiException(ApiFailure.AUTHENTICATION)
                    val result = objectBody ?: throw NafsApiException(ApiFailure.INVALID_RESPONSE)
                    RedactedLog.event(NetworkEvent.REQUEST_SUCCEEDED)
                    return@withTimeoutOrNull result
                }
                throw NafsApiException(ApiFailure.AUTHENTICATION)
            } ?: throw NafsApiException(ApiFailure.TIMEOUT)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: NafsApiException) {
            RedactedLog.event(NetworkEvent.REQUEST_FAILED, error.failure)
            throw error
        } catch (_: HttpRequestTimeoutException) {
            throw NafsApiException(ApiFailure.TIMEOUT)
        } catch (_: Exception) {
            throw NafsApiException(ApiFailure.NETWORK)
        }
    }
}
