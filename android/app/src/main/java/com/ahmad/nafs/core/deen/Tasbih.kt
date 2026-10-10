package com.ahmad.nafs.core.deen

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A dhikr to count: Arabic text, how to say it, what it means, and how many times. */
data class Dhikr(val arabic: String, val transliteration: String, val meaning: String, val target: Int)

object TasbihPresets {
    /** The tasbih after each salah: 33 + 33 + 34 = 100. */
    val afterSalah = listOf(
        Dhikr("سُبْحَانَ ٱللَّٰهِ", "SubhanAllah", "Glory be to Allah", 33),
        Dhikr("ٱلْحَمْدُ لِلَّٰهِ", "Alhamdulillah", "All praise is for Allah", 33),
        Dhikr("ٱللَّٰهُ أَكْبَرُ", "Allahu Akbar", "Allah is the Greatest", 34),
    )
}

enum class TasbihMode { SET, CUSTOM }

/** Progress through one counter: which dhikr of the set ([step]), the count so far and finished rounds. */
@Serializable
data class TasbihCount(val step: Int = 0, val count: Int = 0, val rounds: Int = 0)

/** Everything the counter keeps on this device for one account. Totals only grow with taps and shrink with undo. */
@Serializable
data class TasbihState(
    val mode: TasbihMode = TasbihMode.SET,
    val set: TasbihCount = TasbihCount(),
    val custom: TasbihCount = TasbihCount(),
    val customText: String = TasbihRules.DEFAULT_CUSTOM_TEXT,
    val customTarget: Int = TasbihRules.DEFAULT_CUSTOM_TARGET,
    /** The account day [today] counts, as an ISO date. */
    val todayDate: String = "",
    val today: Int = 0,
    val total: Long = 0,
) {
    val counter: TasbihCount get() = if (mode == TasbihMode.SET) set else custom
}

/** What one tap did, so the screen can pick the haptic: a light tick, or a stronger one at the target. */
data class TasbihTap(val state: TasbihState, val reachedTarget: Boolean, val finishedRound: Boolean)

/** A snapshot to step back to with undo. */
data class TasbihUndo(val mode: TasbihMode, val set: TasbihCount, val custom: TasbihCount, val date: String)

object TasbihRules {
    const val DEFAULT_CUSTOM_TEXT = "Astaghfirullah"
    const val DEFAULT_CUSTOM_TARGET = 100
    const val MAX_TARGET = 9_999
    const val MAX_TEXT = 60
    const val MAX_UNDO = 100

    /** The dhikr being counted now. A custom dhikr is the user's own words, shown as typed. */
    fun current(state: TasbihState): Dhikr = when (state.mode) {
        TasbihMode.SET -> TasbihPresets.afterSalah[state.set.step.coerceIn(TasbihPresets.afterSalah.indices)]
        TasbihMode.CUSTOM -> Dhikr(state.customText, state.customText, "", state.customTarget)
    }

    fun target(state: TasbihState): Int = current(state).target

    /** Starts today's count again when the account day has moved on. */
    fun rollDay(state: TasbihState, date: LocalDate): TasbihState =
        if (state.todayDate == date.toString()) state else state.copy(todayDate = date.toString(), today = 0)

    /**
     * Counts one. Reaching the target finishes that dhikr: the set moves on to the next one (and after Allahu Akbar
     * starts a new round at SubhanAllah); a custom dhikr starts its next round.
     */
    fun tap(state: TasbihState, date: LocalDate): TasbihTap {
        val counted = rollDay(state, date).let { it.copy(today = it.today + 1, total = it.total + 1) }
        val counter = counted.counter
        val reached = counter.count + 1 >= target(counted)
        val last = counted.mode == TasbihMode.CUSTOM || counter.step >= TasbihPresets.afterSalah.lastIndex
        val next = when {
            !reached -> counter.copy(count = counter.count + 1)
            last -> TasbihCount(step = 0, count = 0, rounds = counter.rounds + 1)
            else -> counter.copy(step = counter.step + 1, count = 0)
        }
        return TasbihTap(counted.withCounter(next), reachedTarget = reached, finishedRound = reached && last)
    }

    fun snapshot(state: TasbihState, date: LocalDate) = TasbihUndo(state.mode, state.set, state.custom, date.toString())

    /** Steps back one tap: restores the counters and takes that tap off the totals. */
    fun undo(state: TasbihState, to: TasbihUndo): TasbihState = state.copy(
        mode = to.mode,
        set = to.set,
        custom = to.custom,
        today = if (to.date == state.todayDate) (state.today - 1).coerceAtLeast(0) else state.today,
        total = (state.total - 1).coerceAtLeast(0),
    )

    /** Starts the current counter from zero. Today's and all-time totals keep what was counted. */
    fun reset(state: TasbihState): TasbihState = state.withCounter(TasbihCount())

    /** A valid custom dhikr, or a message saying what to fix. */
    fun customError(text: String, target: Int?): String? = when {
        text.isBlank() -> "Enter the dhikr to count."
        text.trim().length > MAX_TEXT -> "Keep the dhikr under $MAX_TEXT characters."
        target == null || target !in 1..MAX_TARGET -> "Choose a target from 1 to 9,999."
        else -> null
    }

    private fun TasbihState.withCounter(counter: TasbihCount) =
        if (mode == TasbihMode.SET) copy(set = counter) else copy(custom = counter)

    /** Repairs values a damaged or older saved copy might hold. */
    fun sanitized(state: TasbihState): TasbihState = state.copy(
        set = state.set.copy(step = state.set.step.coerceIn(TasbihPresets.afterSalah.indices), count = state.set.count.coerceAtLeast(0)),
        custom = state.custom.copy(step = 0, count = state.custom.count.coerceIn(0, MAX_TARGET)),
        customText = state.customText.trim().take(MAX_TEXT).ifBlank { DEFAULT_CUSTOM_TEXT },
        customTarget = state.customTarget.coerceIn(1, MAX_TARGET),
        today = state.today.coerceAtLeast(0),
        total = state.total.coerceAtLeast(0),
    )
}

/** Keeps the tasbih counters and totals on this device, per account. Nothing is sent to NAFS servers. */
@Singleton
class TasbihStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun read(userId: String): TasbihState {
        val saved = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(key(userId), null) ?: return TasbihState()
        return runCatching { TasbihRules.sanitized(json.decodeFromString<TasbihState>(saved)) }.getOrDefault(TasbihState())
    }

    fun write(userId: String, state: TasbihState) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit { putString(key(userId), json.encodeToString(state)) }
    }

    private companion object {
        const val FILE = "tasbih_counter"
        fun key(userId: String) = "$userId/state"
    }
}
