package com.ahmad.nafs.domain.onboarding

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StarterHabitsTest {
    private val userId = "123e4567-e89b-12d3-a456-426614174000"

    @Test
    fun `starter identifiers are deterministic UUID version 5 values`() {
        val first = deterministicStarterHabitId(userId, "exercise")
        val second = deterministicStarterHabitId(userId, "exercise")

        assertEquals(first, second)
        assertEquals(5, UUID.fromString(first).version())
    }

    @Test
    fun `starter identifiers differ by habit and user`() {
        val exercise = deterministicStarterHabitId(userId, "exercise")
        val reading = deterministicStarterHabitId(userId, "reading")
        val anotherUser = deterministicStarterHabitId(
            "123e4567-e89b-12d3-a456-426614174001",
            "exercise",
        )

        assertNotEquals(exercise, reading)
        assertNotEquals(exercise, anotherUser)
    }

    @Test
    fun `starter keys and names are unique`() {
        assertEquals(starterHabits.size, starterHabits.map { it.key }.toSet().size)
        assertEquals(starterHabits.size, starterHabits.map { it.name }.toSet().size)
        assertTrue(starterHabits.all { it.scoreWeight > 0 })
    }
}
