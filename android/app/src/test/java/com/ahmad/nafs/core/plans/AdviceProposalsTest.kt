package com.ahmad.nafs.core.plans

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.*
import org.junit.Test

class AdviceProposalsTest {
    private fun result(value: String) = Json.parseToJsonElement(value) as JsonObject
    @Test fun goalSuggestionsMapToValidatedRepositoryDefinitions() {
        val suggestions = AdviceProposals.parse(result("""{"starter_pack":{"summary":"Make steady daily progress.","suggested_tasks":[{"title":"Choose next chapter","priority":"high"}],"suggested_habits":[{"name":"Read a chapter","emoji":"📖","type":"duration","time_target_mins":20}],"suggested_challenges":[{"title":"Daily reading","emoji":"📖","reason":"Build a dependable reading rhythm.","frequency":"daily","duration_days":14}]}}"""))
        assertEquals(listOf("task", "habit", "challenge"), suggestions.map { it.kind })
        assertEquals(20, suggestions[1].habit?.minutes)
        assertTrue(suggestions.all(RecoveryRules::validStep))
    }
    @Test fun missingOrUnsafeTargetsAreNeverCreated() {
        assertTrue(AdviceProposals.parse(result("""{"starter_pack":{"related_habits":[{"name":"Walk daily","emoji":"🚶","type":"counter"},{"name":"Study daily","emoji":"📚","type":"duration","time_target_mins":9999}],"suggested_tasks":[{"title":"Do something","priority":"urgent"}]}}""")).isEmpty())
    }
    @Test fun healthAdviceOnlyOffersSupportedHabitDefinitions() {
        val suggestions = AdviceProposals.parse(result("""{"recommendation":{"suggested_habits":[{"name":"Daily walk","emoji":"🚶","type":"boolean"}],"suggested_goals":[{"title":"Feel healthier"}]}}"""))
        assertEquals(1, suggestions.size)
        assertEquals("simple", suggestions.single().habit?.type)
    }
    @Test fun retryStorageIdentityChangesOnlyWithTheSavedAnswer() {
        val original = result("""{"starter_pack":{"summary":"A saved answer"}}""")
        assertEquals(AdviceProposals.key(original), AdviceProposals.key(result(original.toString())))
        assertNotEquals(AdviceProposals.key(original), AdviceProposals.key(result("""{"starter_pack":{"summary":"A different answer"}}""")))
    }
}
