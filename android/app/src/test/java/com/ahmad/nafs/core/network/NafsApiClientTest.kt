package com.ahmad.nafs.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NafsApiClientTest {
    private val payload = JsonObject(mapOf("request_id" to JsonPrimitive("stable-intent")))

    private class Sessions : ApiSessionTokens {
        val refreshes = mutableListOf<Boolean>()
        var currentUser = "user"
        override suspend fun accessToken(userId: String, forceRefresh: Boolean): String {
            if (userId != currentUser) throw NafsApiException(ApiFailure.AUTHENTICATION)
            refreshes += forceRefresh
            return if (forceRefresh) "fresh-token" else "initial-token"
        }
        override fun isCurrentUser(userId: String) = currentUser == userId
    }

    private fun http(engine: MockEngine) = HttpClient(engine) { followRedirects = false; expectSuccess = false }

    @Test fun `401 refreshes once and replays exactly the same intent to the configured origin`() = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val sessions = Sessions()
        val engine = MockEngine { request ->
            requests += request
            respond(if (requests.size == 1) "{}" else "{\"ok\":true}",
                if (requests.size == 1) HttpStatusCode.Unauthorized else HttpStatusCode.OK)
        }
        http(engine).use { client ->
            val result = NafsApiClient(client, sessions, "https://nafs.example").post("user", "/api/ai/evening-verdict", payload)
            assertEquals(JsonPrimitive(true), result["ok"])
        }
        assertEquals(listOf(false, true), sessions.refreshes)
        assertEquals("Bearer initial-token", requests[0].headers[HttpHeaders.Authorization])
        assertEquals("Bearer fresh-token", requests[1].headers[HttpHeaders.Authorization])
        assertEquals((requests[0].body as TextContent).text, (requests[1].body as TextContent).text)
        assertTrue(requests.all { it.url.host == "nafs.example" })
    }

    @Test fun `a second 401 stops instead of looping`() = runBlocking {
        var calls = 0
        val sessions = Sessions()
        val engine = MockEngine { calls++; respond("{}", HttpStatusCode.Unauthorized) }
        http(engine).use { client ->
            val error = runCatching { NafsApiClient(client, sessions, "https://nafs.example").post("user", "/api/review", payload) }.exceptionOrNull()
            assertEquals(ApiFailure.AUTHENTICATION, (error as NafsApiException).failure)
        }
        assertEquals(2, calls)
    }

    @Test fun `server failures are never automatically repeated or exposed as raw text`() = runBlocking {
        var calls = 0
        val engine = MockEngine { calls++; respond("{\"error\":\"secret-token private-note\"}", HttpStatusCode.ServiceUnavailable) }
        http(engine).use { client ->
            val error = runCatching { NafsApiClient(client, Sessions(), "https://nafs.example").post("user", "/api/review", payload) }.exceptionOrNull() as NafsApiException
            assertEquals(ApiFailure.UNAVAILABLE, error.failure)
            assertFalse(error.message.orEmpty().contains("secret-token"))
            assertFalse(error.message.orEmpty().contains("private-note"))
        }
        assertEquals(1, calls)
    }

    @Test fun `redirects never forward the bearer token to another origin`() = runBlocking {
        var calls = 0
        val engine = MockEngine {
            calls++
            respond("", HttpStatusCode.TemporaryRedirect, headersOf(HttpHeaders.Location, "https://other.example/api/review"))
        }
        http(engine).use { client ->
            val error = runCatching { NafsApiClient(client, Sessions(), "https://nafs.example").post("user", "/api/review", payload) }.exceptionOrNull() as NafsApiException
            assertEquals(ApiFailure.INVALID_RESPONSE, error.failure)
        }
        assertEquals(1, calls)
    }

    @Test fun `a response arriving after an account change is discarded`() = runBlocking {
        val sessions = Sessions()
        val engine = MockEngine { sessions.currentUser = "other"; respond("{\"ok\":true}") }
        http(engine).use { client ->
            val error = runCatching { NafsApiClient(client, sessions, "https://nafs.example").post("user", "/api/review", payload) }.exceptionOrNull() as NafsApiException
            assertEquals(ApiFailure.AUTHENTICATION, error.failure)
        }
    }

    @Test fun `HTML success responses are rejected and cancellation propagates`() = runBlocking {
        http(MockEngine { respond("<html>Sign in</html>") }).use { client ->
            val error = runCatching { NafsApiClient(client, Sessions(), "https://nafs.example").post("user", "/api/review", payload) }.exceptionOrNull() as NafsApiException
            assertEquals(ApiFailure.INVALID_RESPONSE, error.failure)
        }
        val cancelled = object : ApiSessionTokens {
            override suspend fun accessToken(userId: String, forceRefresh: Boolean): String = throw CancellationException("Cancelled")
            override fun isCurrentUser(userId: String) = true
        }
        http(MockEngine { respond("{}") }).use { client ->
            val error = runCatching { NafsApiClient(client, cancelled, "https://nafs.example").post("user", "/api/review", payload) }.exceptionOrNull()
            assertTrue(error is CancellationException)
        }
    }

    @Test fun `configuration blocks plaintext credentials alternate origins and path traversal`() {
        val bases = listOf("http://nafs.example", "https://user:password@nafs.example", "https://nafs.example?token=x", "https://nafs.example/other")
        bases.forEach { assertTrue(runCatching { ApiEndpointRules.resolve(it, "/api/review") }.isFailure) }
        val paths = listOf("//other.example/api/review", "https://other.example/api/review", "/api/../secret", "/api/%2e%2e/secret")
        paths.forEach { assertTrue(runCatching { ApiEndpointRules.resolve("https://nafs.example", it) }.isFailure) }
    }

    @Test fun `saved chat responses beyond the old review limit are readable but remain bounded`() = runBlocking {
        val text = "x".repeat(180_000)
        http(MockEngine { respond("{\"history\":\"$text\"}") }).use { client ->
            assertEquals(JsonPrimitive(text), NafsApiClient(client, Sessions(), "https://nafs.example").post("user", "/api/ai/chat", payload)["history"])
        }
        http(MockEngine { respond("{\"history\":\"${"x".repeat(1_000_001)}\"}") }).use { client ->
            val error = runCatching { NafsApiClient(client, Sessions(), "https://nafs.example").post("user", "/api/ai/chat", payload) }.exceptionOrNull() as NafsApiException
            assertEquals(ApiFailure.INVALID_RESPONSE, error.failure)
        }
    }

    @Test fun `in progress and unconfirmed saves have distinct recoverable errors`() {
        assertEquals(ApiFailure.IN_PROGRESS, ApiEndpointRules.error(409, "in_flight"))
        assertEquals(ApiFailure.CONFLICT, ApiEndpointRules.error(409, "conflict"))
        assertEquals(ApiFailure.RESULT_UNCONFIRMED, ApiEndpointRules.error(503, "result_unconfirmed"))
        assertEquals(ApiFailure.RATE_LIMITED, ApiEndpointRules.error(429, null))
    }
}
