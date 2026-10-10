package com.ahmad.nafs.core.plans

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PlanItemQueriesTest {
    @Test fun `online check and all item reads use account and stable ID filters`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            requests += request
            respond(if (request.url.encodedPath.endsWith("/users")) """[{"id":"user"}]""" else "[]", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val client = createSupabaseClient("https://plans.example", "test-public-key") { install(Postgrest); httpEngine = engine }
        try {
            assertTrue(PlanItemQueries.online(client, "user"))
            assertEquals("eq.user", requests[0].url.parameters["id"])
            listOf(taskProposal(), habitProposal(), challengeProposal()).forEach { proposal ->
                assertEquals(PlanItemState.MISSING, PlanItemQueries.inspect(client, "user", proposal, PlanStepState(planStepId, applyDate = planToday.toString())))
            }
            assertEquals(listOf("/rest/v1/tasks", "/rest/v1/habits", "/rest/v1/challenges"), requests.drop(1).map { it.url.encodedPath })
            requests.drop(1).forEach {
                assertEquals(HttpMethod.Get, it.method)
                assertEquals("eq.user", it.url.parameters["user_id"])
                assertEquals("eq.$planStepId", it.url.parameters["id"])
                assertEquals("1", it.url.parameters["limit"])
                assertFalse(it.url.toString().contains("Move%20the"))
            }
        } finally { client.close() }
    }
    @Test fun `matching habit schedule and challenge proof are adopted without a write`() = runTest {
        val engine = MockEngine { request ->
            val row = if (request.url.encodedPath.endsWith("/habits"))
                """{"id":"$planStepId","user_id":"user","name":"Read a book","emoji":"📖","type":"duration","target_value":1,"unit":"","time_target_mins":20,"schedule_kind":"weekdays","schedule_days":["fri","mon"],"is_active":true}"""
            else """{"id":"$planStepId","user_id":"user","title":"Read for two weeks","emoji":"📖","description":"A fourteen day commitment to get started.","frequency":"daily","duration_days":14,"start_date":"2026-10-10","requires_photo":true,"sadqa_amount":null}"""
            assertEquals(HttpMethod.Get, request.method)
            respond("[$row]", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val client = createSupabaseClient("https://plans.example", "test-public-key") { install(Postgrest); httpEngine = engine }
        try {
            val step = PlanStepState(planStepId, applyDate = planToday.toString())
            assertEquals(PlanItemState.ADDED, PlanItemQueries.inspect(client, "user", habitProposal(), step))
            assertEquals(PlanItemState.ADDED, PlanItemQueries.inspect(client, "user", challengeProposal(), step))
            assertEquals(PlanItemState.CONFLICT, PlanItemQueries.inspect(client, "foreign", habitProposal(), step))
            assertEquals(PlanItemState.CONFLICT, PlanItemQueries.inspect(client, "user", challengeProposal().copy(challenge = PlanChallenge("daily", 14, false)), step))
        } finally { client.close() }
    }
}
