package com.ahmad.nafs.core.plans

import com.ahmad.nafs.core.network.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ApiRecoveryRemoteTest {
    private val sessions = object : ApiSessionTokens {
        override fun isCurrentUser(userId: String) = userId == "user"
        override suspend fun accessToken(userId: String, forceRefresh: Boolean) = "verified-token"
    }
    @Test fun `problem request carries bearer stable ID and mode without account ownership`() = runBlocking {
        val engine = MockEngine { request ->
            assertEquals("/api/ai/plan", request.url.encodedPath)
            assertEquals("Bearer verified-token", request.headers[HttpHeaders.Authorization])
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals(setOf("request_id", "intent", "mode"), body.keys)
            assertEquals("overcome", body["mode"]!!.jsonPrimitive.content)
            assertEquals(planRequestId, body["request_id"]!!.jsonPrimitive.content)
            respond(Json.encodeToString(RecoveryResponse.serializer(), planResponse()), headers = io.ktor.http.headersOf(HttpHeaders.ContentType, "application/json"))
        }
        HttpClient(engine).use { http ->
            val result = ApiRecoveryRemote(NafsApiClient(http, sessions, "https://nafs.example")).generate("user", planIntent())
            assertEquals(planResponse(), result)
        }
    }
    @Test fun `saved chat request preserves exact version with no supplied transcript`() = runBlocking {
        val intent = RecoveryIntent(planRequestId, conversationId = planStepId, expectedUpdatedAt = "2026-10-10T12:00:00.123456+00:00")
        val engine = MockEngine { request ->
            assertEquals("/api/ai/plan-from-chat", request.url.encodedPath)
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals(setOf("request_id", "conversation_id", "expected_updated_at"), body.keys)
            assertEquals(intent.expectedUpdatedAt, body["expected_updated_at"]!!.jsonPrimitive.content)
            respond(Json.encodeToString(RecoveryResponse.serializer(), planResponse().copy(provider = "anthropic", fellBack = true)), headers = io.ktor.http.headersOf(HttpHeaders.ContentType, "application/json"))
        }
        HttpClient(engine).use { http ->
            val result = ApiRecoveryRemote(NafsApiClient(http, sessions, "https://nafs.example")).generate("user", intent)
            assertTrue(result.fellBack)
            assertEquals("anthropic", result.provider)
        }
    }
    @Test fun `mismatched request or malformed response cannot become an applicable plan`() = runBlocking {
        listOf(Json.encodeToString(RecoveryResponse.serializer(), planResponse().copy(requestId = planStepId)), """{"recovery":null}""").forEach { body ->
            val engine = MockEngine { respond(body, headers = io.ktor.http.headersOf(HttpHeaders.ContentType, "application/json")) }
            HttpClient(engine).use { http ->
                try { ApiRecoveryRemote(NafsApiClient(http, sessions, "https://nafs.example")).generate("user", planIntent()); fail("Expected invalid response") }
                catch (error: NafsApiException) { assertEquals(ApiFailure.INVALID_RESPONSE, error.failure) }
            }
        }
    }
}
