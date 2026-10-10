package com.ahmad.nafs.domain.onboarding

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID

data class StarterHabit(
    val key: String,
    val name: String,
    val emoji: String,
    val type: String,
    val targetValue: Int,
    val timeTargetMinutes: Int,
    val unit: String,
    val category: String,
    val scoreWeight: Int,
)

val starterHabits = listOf(
    StarterHabit("exercise", "Exercise", "\uD83D\uDCAA", "simple", 1, 30, "", "health", 3),
    StarterHabit("reading", "Reading", "\uD83D\uDCDA", "duration", 30, 30, "mins", "health", 2),
    StarterHabit("deep-work", "Deep Work / Coding", "\uD83D\uDCBB", "duration", 120, 120, "mins", "work", 5),
    StarterHabit("no-social-media", "No Social Media", "\uD83D\uDCF5", "simple", 1, 0, "", "discipline", 3),
    StarterHabit("sleep-by-11", "Sleep by 11 PM", "\uD83D\uDE34", "simple", 1, 0, "", "health", 2),
    StarterHabit("water", "Drink Water (8 glasses)", "\uD83D\uDCA7", "counter", 8, 0, "glasses", "health", 1),
    StarterHabit("journaling", "Journaling", "\u270D", "duration", 15, 15, "mins", "mindset", 2),
    StarterHabit("cold-shower", "Cold Shower", "\uD83D\uDEBF", "simple", 1, 0, "", "discipline", 1),
    StarterHabit("quran-reading", "Quran Reading", "\uD83D\uDCD6", "counter", 5, 20, "pages", "islamic", 3),
    StarterHabit("dhikr", "Dhikr", "\uD83D\uDCFF", "counter", 100, 10, "count", "islamic", 2),
)

fun deterministicStarterHabitId(userId: String, habitKey: String): String {
    val namespace = UUID.fromString(userId)
    val namespaceBytes = ByteBuffer.allocate(16)
        .putLong(namespace.mostSignificantBits)
        .putLong(namespace.leastSignificantBits)
        .array()
    val digest = MessageDigest.getInstance("SHA-1").digest(
        namespaceBytes + "nafs-starter:$habitKey".encodeToByteArray(),
    )

    digest[6] = ((digest[6].toInt() and 0x0F) or 0x50).toByte()
    digest[8] = ((digest[8].toInt() and 0x3F) or 0x80).toByte()
    val uuidBytes = ByteBuffer.wrap(digest.copyOf(16))
    return UUID(uuidBytes.long, uuidBytes.long).toString()
}
