package com.ahmad.nafs.core.letters

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LetterQueriesTest {
    @Test fun `list and lookup scope account identity and exact paging timestamps`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request -> requests += request; respond("[]", headers = headersOf(HttpHeaders.ContentType, "application/json")) }
        val client = createSupabaseClient("https://letters.example", "test-public-key") { install(Postgrest); httpEngine = engine }
        try {
            val cursor = LetterCursor(savedLetter().writtenAt, savedLetter().id)
            assertTrue(LetterQueries.list(client, "user", cursor).isEmpty())
            val list = requests[0].url.parameters
            assertEquals("eq.user", list["user_id"])
            assertEquals("written_at.desc.nullslast,id.desc.nullslast", list["order"])
            assertEquals("21", list["limit"])
            assertEquals("(written_at.lt.\"${cursor.writtenAt}\",and(written_at.eq.\"${cursor.writtenAt}\",id.lt.${cursor.id}))", list["or"])
            assertEquals(null, LetterQueries.get(client, "user", savedLetter().id))
            assertEquals("eq.user", requests[1].url.parameters["user_id"])
            assertEquals("eq.${savedLetter().id}", requests[1].url.parameters["id"])
            assertEquals("1", requests[1].url.parameters["limit"])
        } finally { client.close() }
    }

    @Test fun `creation requests representation and never send AI or delivery fields`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request -> requests += request; respond(Json.encodeToString(listOf(savedLetter())), headers = headersOf(HttpHeaders.ContentType, "application/json")) }
        val client = createSupabaseClient("https://letters.example", "test-public-key") { install(Postgrest); httpEngine = engine }
        try {
            assertEquals(savedLetter(), LetterQueries.create(client, letterIntent()))
            val request = requests.single()
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/rest/v1/future_self_letters", request.url.encodedPath)
            assertTrue(request.headers["Prefer"]!!.contains("return=representation"))
            val data = Json.parseToJsonElement((request.body as TextContent).text)
            val payload = if (data is JsonArray) data.single().jsonObject else data.jsonObject
            assertEquals(setOf("id", "user_id", "content", "target_deliver_date"), payload.keys)
            assertEquals(Json.parseToJsonElement(Json.encodeToString(letterIntent())).jsonObject, payload)
        } finally { client.close() }
    }
}
