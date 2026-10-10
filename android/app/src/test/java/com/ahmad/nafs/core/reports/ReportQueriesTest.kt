package com.ahmad.nafs.core.reports

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportQueriesTest {
    @Test fun `filtered keyset request scopes the account and uses both timestamp and UUID`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request -> requests += request; respond("[]", headers = headersOf(HttpHeaders.ContentType, "application/json")) }
        val client = createSupabaseClient("https://reports.example", "test-public-key") { install(Postgrest); httpEngine = engine }
        try {
            val cursor = ReportCursor(savedReport().generatedAt, savedReport().id)
            assertTrue(ReportQueries.list(client, "user", ReportFilter.GROWTH, cursor).isEmpty())
            val query = requests.single().url.parameters
            assertEquals("eq.user", query["user_id"])
            assertEquals("eq.growth", query["type"])
            assertEquals("generated_at.desc.nullslast,id.desc.nullslast", query["order"])
            assertEquals("21", query["limit"])
            assertEquals("(generated_at.lt.\"${cursor.generatedAt}\",and(generated_at.eq.\"${cursor.generatedAt}\",id.lt.${cursor.id}))", query["or"])
            assertFalse(query["select"]!!.contains("content_md"))
        } finally { client.close() }
    }

    @Test fun `all archive has no type constraint and detail scopes the account and identity`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request -> requests += request; respond("[]", headers = headersOf(HttpHeaders.ContentType, "application/json")) }
        val client = createSupabaseClient("https://reports.example", "test-public-key") { install(Postgrest); httpEngine = engine }
        try {
            ReportQueries.list(client, "user", ReportFilter.ALL, null)
            assertEquals(null, requests[0].url.parameters["type"])
            assertEquals(null, requests[0].url.parameters["or"])
            assertEquals(null, ReportQueries.detail(client, "user", savedReport().id))
            val detail = requests[1].url.parameters
            assertEquals("eq.user", detail["user_id"])
            assertEquals("eq.${savedReport().id}", detail["id"])
            assertEquals("1", detail["limit"])
            assertTrue(detail["select"]!!.contains("content_md"))
        } finally { client.close() }
    }
}
