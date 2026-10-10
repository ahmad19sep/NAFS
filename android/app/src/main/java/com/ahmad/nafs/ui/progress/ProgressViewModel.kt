package com.ahmad.nafs.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmad.nafs.core.network.NafsApiClient
import com.ahmad.nafs.core.records.PrivateRecordStore
import com.ahmad.nafs.core.records.RecordRules
import com.ahmad.nafs.core.time.AccountDate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

data class ProgressState(val period: String = "weekly", val offset: Int = 0, val response: JsonObject? = null,
    val loading: Boolean = true, val offline: Boolean = false, val error: String? = null)

@HiltViewModel
class ProgressViewModel @Inject constructor(private val api: NafsApiClient, private val cache: PrivateRecordStore) : ViewModel() {
    private val _state = MutableStateFlow(ProgressState()); val state = _state.asStateFlow()
    private var account = ""; private var job: Job? = null
    fun initialize(userId: String) { if (account != userId) { account = userId; reload() } }
    fun period(value: String) { if (value !in setOf("weekly", "monthly")) return; _state.update { it.copy(period = value, offset = 0, response = null) }; reload() }
    private var appliedWeekStart: String? = null
    fun openWeek(start: String, zone: String) {
        if (start == appliedWeekStart) return
        appliedWeekStart = start
        val monday = runCatching { LocalDate.parse(start) }.getOrNull() ?: return
        val current = runCatching { AccountDate.today(zone) }.getOrNull()?.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) ?: return
        val offset = ChronoUnit.WEEKS.between(monday, current).toInt().coerceIn(0, 60)
        if (_state.value.period == "weekly" && _state.value.offset == offset && _state.value.response != null) return
        _state.update { it.copy(period = "weekly", offset = offset, response = null) }; reload()
    }
    fun shift(amount: Int) { _state.update { it.copy(offset = (it.offset + amount).coerceIn(0, 60), response = null) }; reload() }
    fun reload() {
        job?.cancel(); val userId = account; val period = _state.value.period; val offset = _state.value.offset
        if (userId.isBlank()) return
        _state.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch {
            val key = "progress/$period/$offset"
            try {
                val response = api.post(userId, "/api/progress", buildJsonObject { put("period", period); put("offset", offset) })
                require(response["report"] is JsonObject && response["history"] is JsonObject)
                if (account != userId) return@launch
                _state.update { it.copy(response = response, loading = false, offline = false) }
                try { cache.write(userId, key, response) }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { _state.update { it.copy(error = "Current report loaded; its offline copy could not be saved.") } }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) {
                val saved = runCatching { cache.read(userId, key) }.getOrNull()
                if (account == userId) _state.update {
                    val visible = saved ?: it.response
                    it.copy(response = visible, loading = false, offline = visible != null,
                        error = if (visible == null) "Progress could not be loaded. Check your connection and retry." else "Showing the last saved report. Reconnect and refresh for current figures.")
                }
            }
        }
    }
}

object ProgressText {
    fun lines(response: JsonObject, history: Boolean = false): List<String> = buildList {
        val report = response["report"] as JsonObject
        add("NAFS · ${RecordRules.text(report, "label")}")
        add("${RecordRules.text(report, "start")} – ${RecordRules.text(report, "end")}")
        add("Account timezone: ${RecordRules.text(response, "timezone")}")
        add("Generated: ${RecordRules.text(report, "generated_at")}")
        val unavailable = response["unavailable"] as? JsonArray
        if (!unavailable.isNullOrEmpty()) add("Partial data: ${unavailable.joinToString { (it as JsonPrimitive).content }}")
        val logged = RecordRules.text(report, "days_logged").toIntOrNull() ?: 0
        add("Recorded days: $logged / ${RecordRules.text(report, "days_elapsed")}")
        if (logged == 0) add("No recorded data for this period.") else {
            add("Average score: ${RecordRules.text(report, "avg_score")}%")
            RecordRules.text(report, "score_delta").takeIf { it.isNotBlank() }?.let { add("Change from previous period: $it points") }
        }
        add("Tasks: ${RecordRules.text(report, "tasks_completed")} / ${RecordRules.text(report, "tasks_total")}")
        add("Habit completion: ${RecordRules.text(report, "habit_completion_pct")}%")
        if ((report["deenEnabled"] as? JsonPrimitive)?.booleanOrNull == true) {
            add("Prayer points: ${RecordRules.text(report, "prayer_points")} / ${RecordRules.text(report, "prayer_points_max")}")
            add("Quran pages: ${RecordRules.text(report, "quran_pages")}")
        }
        for ((key, title) in listOf("habits" to "Habits", "challenges" to "Challenges", "goals" to "Goals", "comparison" to "Comparison", "focus" to "Focus")) {
            val rows = report[key] as? JsonArray ?: continue
            if (rows.isNotEmpty()) add(title)
            rows.forEach { item -> if (item is JsonObject) add(item.entries.filter { (_, v) -> v is JsonPrimitive && v != JsonNull }.joinToString(" · ") { (k, v) -> "${label(k)}: ${(v as JsonPrimitive).content}" }) }
        }
        (report["insights"] as? JsonArray)?.forEach { add((it as JsonPrimitive).content) }
        (report["health"] as? JsonObject)?.let { health ->
            add("Health")
            addAll(details(health))
        }
        add("Daily records")
        (report["days"] as? JsonArray)?.forEach { item ->
            val day = item as JsonObject
            if ((day["logged"] as? JsonPrimitive)?.booleanOrNull == true) add("${RecordRules.text(day, "date")}: ${RecordRules.text(day, "score")}% · habits ${RecordRules.text(day, "habits_done")}/${RecordRules.text(day, "habits_total")} · tasks ${RecordRules.text(day, "tasks_done")}/${RecordRules.text(day, "tasks_total")}")
            else add("${RecordRules.text(day, "date")}: no recorded data")
        }
        val review = response["review"] as? JsonObject
        RecordRules.text(review, "content_md").takeIf { it.isNotBlank() }?.let { add("Coach review · ${RecordRules.text(review, "model_used")}"); addAll(it.lines()) }
        if (history) {
            add("30-day history")
            (response["history"] as JsonObject).forEach { (key, array) ->
                add(label(key)); (array as JsonArray).forEach { item -> val row = item as JsonObject
                    val coverage = RecordRules.text(row, "coverage")
                    add("${RecordRules.text(row, "date")}: " + if (coverage != "recorded") label(coverage) else "${RecordRules.text(row, "earned")}/${RecordRules.text(row, "max")} · ${RecordRules.text(row, "pct")}%")
                }
            }
        }
    }
    private fun details(value: JsonElement, prefix: String = ""): List<String> = when (value) {
        JsonNull -> emptyList()
        is JsonPrimitive -> listOf("$prefix${value.content}")
        is JsonObject -> value.entries.filter { it.key !in setOf("id", "user_id", "request_id") }
            .flatMap { (key, child) -> details(child, "$prefix${label(key)}: ") }
        is JsonArray -> value.flatMap { details(it, prefix) }
    }
    fun label(key: String) = key.replace('_', ' ').replaceFirstChar(Char::uppercase)
}
