package com.ahmad.nafs.screenshots

import android.app.Application
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ahmad.nafs.core.records.RecordFeature
import com.ahmad.nafs.ui.progress.AchievementsScreen
import com.ahmad.nafs.ui.progress.AchievementsState
import com.ahmad.nafs.ui.progress.ProgressActions
import com.ahmad.nafs.ui.progress.ProgressScreen
import com.ahmad.nafs.ui.progress.ProgressState
import com.ahmad.nafs.ui.records.RecordActions
import com.ahmad.nafs.ui.records.RecordScreen
import com.ahmad.nafs.ui.records.RecordUiState
import java.time.LocalDate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Progress, history, badges and the journal screens rendered with fixed sample state.
 * Images are written only by `./gradlew :app:recordRoborazziDebug`; plain unit test runs skip them.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A plain Application: the real one schedules WorkManager jobs that have no place in a render test.
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xhdpi", application = Application::class)
class ProgressRecordsScreenshotTest : ScreenshotTestBase() {

    // ─── 30-day history ──────────────────────────────────────────────────────

    @Test fun history() = captureScreen("90_history_overall") {
        ProgressScreen(ProgressState(response = ProgressSamples.weekly, loading = false), history = true, actions = ProgressActions())
    }

    @Test fun historyByArea() = captureScreen("90_history_by_area") {
        ProgressScreen(ProgressState(response = ProgressSamples.weekly, loading = false), history = true, actions = ProgressActions(),
            listState = rememberLazyListState(initialFirstVisibleItemIndex = 4))
    }

    @Test fun historyDays() = captureScreen("90_history_day_by_day") {
        ProgressScreen(ProgressState(response = ProgressSamples.weekly, loading = false), history = true, actions = ProgressActions(),
            listState = rememberLazyListState(initialFirstVisibleItemIndex = 6))
    }

    @Test fun historyEmptyOffline() = captureScreen("90_history_empty_offline") {
        ProgressScreen(
            ProgressState(response = ProgressSamples.noData, loading = false, offline = true,
                error = "Showing the last saved report. Reconnect and refresh for current figures."),
            history = true,
            actions = ProgressActions(),
        )
    }

    // ─── Weekly and monthly reports ──────────────────────────────────────────

    @Test fun reportWeekly() = captureScreen("91_reports_period_weekly") {
        ProgressScreen(ProgressState(period = "weekly", offset = 0, response = ProgressSamples.weekly, loading = false), history = false,
            actions = ProgressActions())
    }

    @Test fun reportWeeklyCompare() = captureScreen("91_reports_period_weekly_compare") {
        ProgressScreen(ProgressState(period = "weekly", offset = 0, response = ProgressSamples.weekly, loading = false), history = false,
            actions = ProgressActions(), listState = rememberLazyListState(initialFirstVisibleItemIndex = 5))
    }

    @Test fun reportWeeklyFocus() = captureScreen("91_reports_period_weekly_focus") {
        ProgressScreen(ProgressState(period = "weekly", offset = 0, response = ProgressSamples.weekly, loading = false), history = false,
            actions = ProgressActions(), listState = rememberLazyListState(initialFirstVisibleItemIndex = 7))
    }

    @Test fun reportWeeklyDetails() = captureScreen("91_reports_period_weekly_details") {
        ProgressScreen(ProgressState(period = "weekly", offset = 0, response = ProgressSamples.weekly, loading = false), history = false,
            actions = ProgressActions(), listState = rememberLazyListState(initialFirstVisibleItemIndex = 11))
    }

    @Test fun reportWeeklyExport() = captureScreen("91_reports_period_weekly_export") {
        ProgressScreen(ProgressState(period = "weekly", offset = 0, response = ProgressSamples.weekly, loading = false), history = false,
            actions = ProgressActions(), exportMessage = "PDF exported.",
            listState = rememberLazyListState(initialFirstVisibleItemIndex = 21))
    }

    @Test fun reportMonthlyNoData() = captureScreen("91_reports_period_monthly_no_data") {
        ProgressScreen(ProgressState(period = "monthly", offset = 2, response = ProgressSamples.noData, loading = false), history = false,
            actions = ProgressActions())
    }

    @Test fun reportError() = captureScreen("91_reports_period_error") {
        ProgressScreen(
            ProgressState(period = "weekly", offset = 1, response = null, loading = false,
                error = "Progress could not be loaded. Check your connection and retry."),
            history = false,
            actions = ProgressActions(),
        )
    }

    // ─── Badges and levels ───────────────────────────────────────────────────

    @Test fun badges() = captureScreen("92_badges") {
        AchievementsScreen(AchievementsState(data = BadgeSamples.recognition), onBack = {}, onRefresh = {})
    }

    @Test fun badgesGrid() = captureScreen("92_badges_grid") {
        AchievementsScreen(AchievementsState(data = BadgeSamples.recognition), onBack = {}, onRefresh = {},
            listState = rememberLazyListState(initialFirstVisibleItemIndex = 2))
    }

    @Test fun badgesFirstDayOffline() = captureScreen("92_badges_first_day_offline") {
        AchievementsScreen(
            AchievementsState(data = BadgeSamples.firstDay, offline = true, error = "Achievements could not be refreshed. Reconnect and retry."),
            onBack = {},
            onRefresh = {},
        )
    }

    // ─── Journals ────────────────────────────────────────────────────────────

    @Test fun dream() = captureScreen("93_record_dream_saved") {
        RecordScreen(SampleData.profile, RecordFeature.DREAM,
            RecordUiState(feature = RecordFeature.DREAM, rows = RecordSamples.dreams, loading = false), RecordActions())
    }

    @Test fun dreamEmpty() = captureScreen("93_record_dream_empty") {
        RecordScreen(SampleData.profile, RecordFeature.DREAM, RecordUiState(feature = RecordFeature.DREAM, loading = false), RecordActions())
    }

    @Test fun dreamForm() = captureScreen("93_record_dream_form") {
        RecordScreen(SampleData.profile, RecordFeature.DREAM,
            RecordUiState(feature = RecordFeature.DREAM, rows = RecordSamples.dreams, values = RecordSamples.dreamDraft, editing = true,
                loading = false),
            RecordActions())
    }

    @Test fun log() = captureScreen("93_record_log_saved") {
        RecordScreen(SampleData.profile, RecordFeature.LOG,
            RecordUiState(feature = RecordFeature.LOG, rows = RecordSamples.logs, hasMore = true, loading = false,
                message = "Saved to your account."),
            RecordActions())
    }

    @Test fun logForm() = captureScreen("93_record_log_form") {
        RecordScreen(SampleData.profile, RecordFeature.LOG,
            RecordUiState(feature = RecordFeature.LOG, rows = RecordSamples.logs, values = RecordSamples.logDraft, date = "2026-10-10",
                editing = true, loading = false),
            RecordActions())
    }

    @Test fun logFormPhotos() = captureScreen("93_record_log_form_photos") {
        RecordScreen(SampleData.profile, RecordFeature.LOG,
            RecordUiState(feature = RecordFeature.LOG, rows = RecordSamples.logs, values = RecordSamples.logDraft, date = "2026-10-10",
                editing = true, loading = false),
            RecordActions(),
            listState = rememberLazyListState(initialFirstVisibleItemIndex = 0, initialFirstVisibleItemScrollOffset = 1500))
    }

    @Test fun logPending() = captureScreen("93_record_log_pending") {
        RecordScreen(SampleData.profile, RecordFeature.LOG,
            RecordUiState(feature = RecordFeature.LOG, rows = RecordSamples.logs, values = RecordSamples.logDraft, date = "2026-10-10",
                editing = true, loading = false, offline = true, pending = true,
                error = "Save is awaiting confirmation. It will retry when connected while this account is signed in. " +
                    "Reload to inspect the result, or retry the same request."),
            RecordActions())
    }

    @Test fun logEmpty() = captureScreen("93_record_log_empty") {
        RecordScreen(SampleData.profile, RecordFeature.LOG, RecordUiState(feature = RecordFeature.LOG, loading = false), RecordActions())
    }

    @Test fun quran() = captureScreen("93_record_quran_saved") {
        RecordScreen(SampleData.profile, RecordFeature.QURAN,
            RecordUiState(feature = RecordFeature.QURAN, rows = RecordSamples.quran, loading = false), RecordActions())
    }

    @Test fun quranForm() = captureScreen("93_record_quran_form") {
        RecordScreen(SampleData.profile, RecordFeature.QURAN,
            RecordUiState(feature = RecordFeature.QURAN, rows = RecordSamples.quran, values = RecordSamples.quranDraft, date = "2026-10-10",
                editing = true, loading = false),
            RecordActions())
    }

    @Test fun screenTime() = captureScreen("93_record_screen_saved") {
        RecordScreen(SampleData.profile, RecordFeature.SCREEN,
            RecordUiState(feature = RecordFeature.SCREEN, rows = RecordSamples.screen, loading = false), RecordActions())
    }

    @Test fun screenTimeAnalysis() = captureScreen("93_record_screen_analysis") {
        RecordScreen(SampleData.profile, RecordFeature.SCREEN,
            RecordUiState(feature = RecordFeature.SCREEN, rows = RecordSamples.screen, loading = false, message = RecordSamples.analysis),
            RecordActions())
    }

    @Test fun screenTimeForm() = captureScreen("93_record_screen_form") {
        RecordScreen(SampleData.profile, RecordFeature.SCREEN,
            RecordUiState(feature = RecordFeature.SCREEN, rows = RecordSamples.screen, values = RecordSamples.screenDraft, date = "2026-10-10",
                editing = true, loading = false),
            RecordActions())
    }

    @Test fun notes() = captureScreen("93_record_note_saved") {
        RecordScreen(SampleData.profile, RecordFeature.NOTE,
            RecordUiState(feature = RecordFeature.NOTE, rows = RecordSamples.notes, loading = false), RecordActions())
    }

    @Test fun noteForm() = captureScreen("93_record_note_form") {
        RecordScreen(SampleData.profile, RecordFeature.NOTE,
            RecordUiState(feature = RecordFeature.NOTE, rows = RecordSamples.notes, values = RecordSamples.noteDraft, editing = true,
                loading = false),
            RecordActions())
    }
}

/** `/api/progress` responses, shaped like the server's report and native history. */
private object ProgressSamples {
    private val today: LocalDate = SampleData.today

    /** Thirty days ending today: today is pending, [gaps] are unrecorded, [unknown] could not be read. */
    private fun history(seed: Int, gaps: Set<Int>, max: Int, unknown: Set<Int> = emptySet()): JsonArray = buildJsonArray {
        for (index in 0 until 30) {
            val coverage = when {
                index == 29 -> "pending"
                index in unknown -> "unknown"
                index in gaps -> "unrecorded"
                else -> "recorded"
            }
            val pct = if (coverage == "recorded") 30 + (index * 37 + seed * 23) % 71 else 0
            addJsonObject {
                put("date", today.minusDays((29 - index).toLong()).toString())
                put("earned", if (coverage == "recorded") pct * max / 100 else 0)
                put("max", if (coverage == "recorded") max else 0)
                put("pct", pct)
                put("coverage", coverage)
            }
        }
    }

    private val histories: JsonObject = buildJsonObject {
        put("overall", history(1, setOf(3, 9, 16, 22), 5))
        put("habits", history(2, setOf(3, 9, 16, 22, 23), 5))
        put("deen", history(3, setOf(9, 22), 10))
        put("tasks", history(4, setOf(2, 3, 9, 15, 16, 22), 4))
        put("health", history(5, setOf(1, 3, 5, 9, 16, 22, 24), 4, unknown = setOf(12, 13)))
        put("challenges", history(6, setOf(3, 22), 1))
    }

    private fun JsonArrayBuilder.comparison(key: String, label: String, emoji: String, current: Int, previous: Int, unit: String,
        detail: String, previousDetail: String) = addJsonObject {
        put("key", key); put("label", label); put("emoji", emoji)
        put("current", current); put("previous", previous); put("delta", current - previous)
        put("unit", unit); put("detail", detail); put("previousDetail", previousDetail)
    }

    private fun JsonArrayBuilder.focus(key: String, area: String, emoji: String, title: String, severity: String, severityLabel: String,
        stat: String, why: String, action: String) = addJsonObject {
        put("key", key); put("area", area); put("emoji", emoji); put("title", title)
        put("severity", severity); put("severityLabel", severityLabel); put("stat", stat)
        put("why", why); put("action", action)
    }

    val weekly: JsonObject = buildJsonObject {
        put("timezone", "Asia/Karachi")
        put("today", today.toString())
        putJsonArray("unavailable") {}
        putJsonObject("review") {
            put("content_md", "## A week of two halves\n\nMonday and Thursday show what you can do when the morning starts on time. " +
                "**Fajr is the hinge**: the days you prayed it on time, every other area followed.\n\n- Protect bedtime on weeknights\n- Keep the workout to three short sessions")
            put("model_used", "NAFS Coach")
        }
        put("history", histories)
        putJsonObject("report") {
            put("period", "weekly")
            put("label", "5 – 11 Oct 2026")
            put("shortLabel", "Week of 5 Oct")
            put("previousLabel", "28 Sep – 4 Oct 2026")
            put("start", "2026-10-05")
            put("end", "2026-10-11")
            put("generated_at", "10 Oct 2026, 21:40")
            put("days_in_period", 7)
            put("days_elapsed", 6)
            put("days_logged", 4)
            put("avg_score", 71)
            put("score_delta", 6)
            put("tasks_completed", 18)
            put("tasks_total", 24)
            put("habit_completion_pct", 68)
            put("deenEnabled", true)
            put("prayer_points", 41)
            put("prayer_points_max", 60)
            put("quran_pages", 23)
            putJsonArray("days") {
                listOf(81, 64, null, 92, 47, null).forEachIndexed { index, score ->
                    addJsonObject {
                        put("date", LocalDate.of(2026, 10, 5).plusDays(index.toLong()).toString())
                        put("logged", score != null)
                        put("score", score ?: 0)
                        put("habits_done", if (score != null) 4 else 0)
                        put("habits_total", 5)
                        put("tasks_done", if (score != null) 3 else 0)
                        put("tasks_total", 4)
                    }
                }
            }
            putJsonArray("comparison") {
                comparison("overall", "Overall score", "✨", 71, 65, "%", "71% average", "65% average")
                comparison("consistency", "Days logged", "📆", 67, 86, "%", "4 of 6 days", "6 of 7 days")
                comparison("salah", "Salah", "🕌", 78, 70, "%", "23/30 prayers · 6 jamaat", "21/30 · 4 jamaat")
                comparison("quran", "Quran", "📖", 23, 18, "pages", "23 pages over 4 days", "18 pages over 5 days")
                comparison("habits", "Habits", "🔄", 68, 72, "%", "17/25 scheduled days", "18/25 days")
                comparison("tasks", "Tasks", "✅", 75, 75, "%", "18/24 completed", "15/20 completed")
            }
            putJsonArray("focus") {
                focus("consistency", "Consistency", "📆", "Logging every day", "warning", "Watch", "4/6 days",
                    "You missed 2 days entirely, so your real numbers are probably better than this report can show.",
                    "Open the app once at night — even a 20-second check-in keeps the record honest.")
                focus("prayer", "Salah", "🕌", "Fajr prayer", "serious", "Needs work", "3/6 days",
                    "Your weakest prayer this week — missed 3 times.",
                    "Move bedtime 30 minutes earlier and put the alarm across the room.")
                focus("habit", "Habits", "🏋️", "Workout", "critical", "Critical", "1/4 days",
                    "Your least-kept habit — the longest run this week was 1 day.",
                    "This one is not sticking. Either halve the target or drop it and put the effort into a habit that is working.")
            }
            putJsonArray("habits") {
                addJsonObject { put("emoji", "📖"); put("name", "Read"); put("pct", 83); put("done_days", 5); put("scheduled_days", 6); put("current_streak", 12) }
                addJsonObject { put("emoji", "💧"); put("name", "Drink water"); put("pct", 50); put("done_days", 3); put("scheduled_days", 6); put("current_streak", 0) }
                addJsonObject { put("emoji", "🏋️"); put("name", "Workout"); put("pct", 25); put("done_days", 1); put("scheduled_days", 4); put("current_streak", 0) }
            }
            putJsonArray("challenges") {
                addJsonObject {
                    put("emoji", "🎯"); put("title", "No sugar for 30 days"); put("pct", 83)
                    put("checkins", 5); put("possible_days", 6); put("day_of", 12); put("duration_days", 30)
                }
            }
            putJsonArray("goals") {
                addJsonObject { put("emoji", "🕌"); put("title", "Memorise Surah Al-Mulk"); put("progress_pct", 70); put("milestones_done", 2); put("milestones_total", 3); put("days_left", 20) }
                addJsonObject { put("emoji", "🏃"); put("title", "Run a 10K under 55 minutes"); put("progress_pct", 45); put("milestones_done", 2); put("milestones_total", 4); put("days_left", 48) }
            }
            putJsonObject("health") {
                put("days_logged", 5); put("avg_sleep", 6.8); put("avg_steps", 7420); put("avg_water", 6.5)
                put("exercise_days", 3); put("avg_mood", 3.8); put("weight_change", -0.4)
            }
            putJsonArray("insights") {
                add("Thursday was your strongest day at 92%, with every prayer on time.")
                add("Habits slipped on the two days you slept under six hours.")
            }
        }
    }

    /** A period with nothing recorded, a partial read and an empty history. */
    val noData: JsonObject = buildJsonObject {
        put("timezone", "Asia/Karachi")
        put("today", today.toString())
        putJsonArray("unavailable") { add("healthLogs") }
        put("review", JsonNull)
        putJsonObject("history") {
            listOf("overall", "habits", "deen", "tasks", "health", "challenges").forEach { key -> putJsonArray(key) {} }
        }
        putJsonObject("report") {
            put("period", "monthly")
            put("label", "August 2026")
            put("shortLabel", "August 2026")
            put("start", "2026-08-01")
            put("end", "2026-08-31")
            put("days_in_period", 31)
            put("days_elapsed", 31)
            put("days_logged", 0)
            put("avg_score", 0)
            put("score_delta", JsonNull)
            put("tasks_completed", 0)
            put("tasks_total", 0)
            put("habit_completion_pct", 0)
            put("deenEnabled", true)
            put("prayer_points", 0)
            put("prayer_points_max", 0)
            put("quran_pages", 0)
            putJsonArray("days") {
                for (day in 1..31) addJsonObject {
                    put("date", LocalDate.of(2026, 8, day).toString())
                    put("logged", false)
                    put("score", 0)
                }
            }
            putJsonArray("comparison") {}
            putJsonArray("focus") {}
            putJsonArray("insights") {}
        }
    }
}

/** `/api/recognition` responses: a level in progress, and the very first day. */
private object BadgeSamples {
    private fun badge(id: String, name: String, description: String, emoji: String, feature: String, tier: String,
        earned: Boolean = false, awardedAt: String? = null) = buildJsonObject {
        put("id", id); put("name", name); put("description", description); put("emoji", emoji)
        put("feature", feature); put("tier", tier); put("earned", earned); put("awardedAt", awardedAt)
    }

    private fun badges(earnedIds: Set<String>): JsonArray = JsonArray(listOf(
        badge("habit-first", "First Habit", "Created your first habit", "🌱", "habits", "bronze", "habit-first" in earnedIds, "2026-09-02T08:00:00Z"),
        badge("habit-streak-7", "7-Day Streak", "Kept a habit for 7 days in a row", "🔥", "habits", "silver", "habit-streak-7" in earnedIds, "2026-09-14T21:00:00Z"),
        badge("habit-streak-30", "30-Day Streak", "Kept a habit for 30 days in a row", "⚡", "habits", "gold", "habit-streak-30" in earnedIds),
        badge("habit-streak-100", "Century Streak", "Kept a habit for 100 days in a row", "💎", "habits", "platinum"),
        badge("deen-first", "First Prayer", "Logged your first prayer", "🕌", "deen", "bronze", "deen-first" in earnedIds, "2026-09-02T05:10:00Z"),
        badge("deen-fajr-7", "Fajr Champion", "Prayed Fajr 7 days in a row", "☀️", "deen", "silver", "deen-fajr-7" in earnedIds),
        badge("deen-jamat", "In Jamat", "Prayed any prayer in jamat", "🌟", "deen", "silver"),
        badge("deen-perfect-30", "30 Perfect Days", "30 days with all 5 prayers prayed", "📿", "deen", "platinum"),
        badge("chall-first", "First Challenge", "Started your first challenge", "🎯", "challenges", "bronze", "chall-first" in earnedIds),
        badge("chall-conquer", "Conqueror", "Completed a full challenge", "🏆", "challenges", "gold"),
        badge("task-first", "First Task Done", "Completed your first task", "✅", "tasks", "bronze", "task-first" in earnedIds, "2026-09-03T12:00:00Z"),
        badge("task-perfect", "Perfect Day", "Completed all 3+ daily tasks in one day", "🌟", "tasks", "silver", "task-perfect" in earnedIds),
        badge("task-100", "100 Tasks Done", "Completed 100 tasks lifetime", "💪", "tasks", "gold"),
        badge("goal-first", "First Goal", "Set your first goal", "⭐", "goals", "bronze", "goal-first" in earnedIds),
        badge("goal-completed", "Goal Achiever", "Completed a goal", "🎉", "goals", "gold"),
        badge("health-water-7", "Hydration Hero", "Hit 8 glasses of water 7 days in a row", "💧", "health", "silver"),
        badge("overall-king", "Consistency King", "7 straight days at 80%+ score", "✨", "overall", "platinum"),
    ))

    val recognition: JsonObject = buildJsonObject {
        putJsonObject("level") {
            putJsonObject("current") { put("number", 4); put("name", "Two weeks"); put("at", 14) }
            putJsonObject("next") { put("number", 5); put("name", "One month"); put("at", 30) }
            put("daysLogged", 22)
            put("daysToNext", 8)
            put("pctToNext", 50)
        }
        put("badges", badges(setOf("habit-first", "habit-streak-7", "deen-first", "deen-fajr-7", "chall-first", "task-first", "task-perfect", "goal-first")))
        putJsonArray("unavailable") {}
    }

    val firstDay: JsonObject = buildJsonObject {
        putJsonObject("level") {
            put("current", JsonNull)
            putJsonObject("next") { put("number", 1); put("name", "Day one"); put("at", 1) }
            put("daysLogged", 0)
            put("daysToNext", 1)
            put("pctToNext", 0)
        }
        put("badges", badges(emptySet()))
        putJsonArray("unavailable") { add("habitLogs") }
    }
}

/** Saved rows and drafts as the journal ViewModel holds them. */
private object RecordSamples {
    private fun row(id: String, build: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): JsonObject = buildJsonObject {
        put("id", id)
        put("user_id", SampleData.USER)
        build()
    }

    // Photos use plain http so the render never waits on the network; the tiles show their placeholder.
    private const val PHOTO_A = "http://photos.invalid/log-a.jpg"
    private const val PHOTO_B = "http://photos.invalid/log-b.jpg"

    val dreams = listOf(row("0f2c7a52-6d1e-4b8a-9b52-0a3c1d2e3f40") {
        put("statement", "Ship my own product and make it my full-time work")
        put("why", "So my days are spent on work I chose, and my family has the time with me they deserve.")
        put("dream_date", "2028-06-30")
        put("total_hours_required", 3000)
        put("created_at", "2026-09-01T10:00:00Z")
    })

    val dreamDraft = mapOf(
        "statement" to "Ship my own product and make it my full-time work",
        "why" to "So my days are spent on work I chose, and my family has the time with me they deserve.",
        "dream_date" to "2028-06-30",
        "total_hours_required" to "3000",
        "screenshot_url" to "",
        "photo_urls" to "",
    )

    val logs = listOf(
        row("6b1f0d3e-2a4c-4e5f-8a9b-1c2d3e4f5a61") {
            put("date", "2026-10-10"); put("dream_work_hours", 3.5); put("study_hours", 1.5); put("screen_time_mins", 145)
            put("sleep_start", "23:30"); put("sleep_end", "06:15"); put("mood", 4)
            put("reflection_text", "Deep work before Dhuhr went well. Lost an hour to the phone after lunch; the evening walk reset me.")
            put("weighted_hours_today", 12.75); put("todays_pull_days", 0.2683)
            putJsonArray("photo_urls") { add(PHOTO_A); add(PHOTO_B) }
        },
        row("6b1f0d3e-2a4c-4e5f-8a9b-1c2d3e4f5a62") {
            put("date", "2026-10-09"); put("dream_work_hours", 2); put("study_hours", 0); put("screen_time_mins", 260)
            put("sleep_start", "01:10"); put("sleep_end", "07:40"); put("mood", 2)
            put("reflection_text", "Late night, slow start. Kept the one promise that mattered.")
            put("weighted_hours_today", 6); put("todays_pull_days", -0.3684)
            putJsonArray("photo_urls") {}
        },
    )

    val logDraft = mapOf(
        "dream_work_hours" to "3.5",
        "study_hours" to "1.5",
        "screen_time_mins" to "145",
        "sleep_start" to "23:30",
        "sleep_end" to "06:15",
        "mood" to "4",
        "reflection_text" to "Deep work before Dhuhr went well. Lost an hour to the phone after lunch.",
        "photo_urls" to "[\"$PHOTO_A\",\"$PHOTO_B\"]",
        "screenshot_url" to "",
    )

    val quran = listOf(
        row("7c2a1e4f-3b5d-4f6a-9b0c-2d3e4f5a6b71") {
            put("date", "2026-10-10"); put("pages_read", 6); put("surah", "Al-Kahf"); put("ayah_from", 1); put("ayah_to", 31)
            put("notes", "The story of the companions of the cave: holding on when the world around you will not.")
        },
        row("7c2a1e4f-3b5d-4f6a-9b0c-2d3e4f5a6b72") {
            put("date", "2026-10-09"); put("pages_read", 4); put("surah", "Al-Isra"); put("ayah_from", 78); put("ayah_to", 111)
        },
    )

    val quranDraft = mapOf(
        "pages_read" to "6",
        "surah" to "Al-Kahf",
        "ayah_from" to "1",
        "ayah_to" to "31",
        "notes" to "",
        "screenshot_url" to "",
        "photo_urls" to "",
    )

    val screen = listOf(
        row("8d3b2f5a-4c6e-4a7b-8c1d-3e4f5a6b7c81") {
            put("date", "2026-10-10"); put("total_mins", 214)
            putJsonArray("apps") {
                addJsonObject { put("app", "YouTube"); put("minutes", 72); put("category", "other") }
                addJsonObject { put("app", "WhatsApp"); put("minutes", 41); put("category", "other") }
                addJsonObject { put("app", "Instagram"); put("minutes", 38); put("category", "other") }
            }
            put("screenshot_url", JsonNull)
            put("ai_summary", "**Video is the big one.** YouTube took a third of today's screen time, mostly after 10 pm. " +
                "Move it off the home screen and set a 30-minute limit after Isha.")
        },
    )

    val analysis = "**Video is the big one.** YouTube took a third of today's screen time, mostly after 10 pm.\n\n" +
        "- Move it off the home screen\n- Set a 30-minute limit after Isha"

    val screenDraft = mapOf(
        "total_mins" to "214",
        "apps" to "YouTube: 72\nWhatsApp: 41\nInstagram: 38",
        "screenshot_url" to "",
        "photo_urls" to "",
    )

    val notes = listOf(
        row("9e4c3a6b-5d7f-4b8c-9d2e-4f5a6b7c8d91") {
            put("kind", "bug"); put("title", "Habit streak resets after midnight in Karachi"); put("status", "open")
            put("detail", "Logged Quran at 11:50 pm and the streak showed 0 the next morning.")
            put("where_seen", "Habits"); put("created_at", "2026-10-08T19:20:00Z")
        },
        row("9e4c3a6b-5d7f-4b8c-9d2e-4f5a6b7c8d92") {
            put("kind", "idea"); put("title", "Weekly report as a home-screen widget"); put("status", "done")
            put("detail", "One glance at the score without opening the app.")
            put("created_at", "2026-09-28T09:05:00Z")
        },
    )

    val noteDraft = mapOf(
        "kind" to "idea",
        "title" to "Let me snooze a habit reminder for an hour",
        "detail" to "",
        "where_seen" to "Notifications",
        "status" to "open",
        "screenshot_url" to "",
        "photo_urls" to "",
    )
}
