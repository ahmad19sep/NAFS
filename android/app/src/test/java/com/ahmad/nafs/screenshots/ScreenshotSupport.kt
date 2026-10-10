package com.ahmad.nafs.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ahmad.nafs.core.goals.Goal
import com.ahmad.nafs.core.goals.GoalMilestone
import com.ahmad.nafs.core.habits.Habit
import com.ahmad.nafs.core.habits.HabitLog
import com.ahmad.nafs.core.preferences.ThemeMode
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.core.tasks.NafsTask
import com.ahmad.nafs.core.tasks.TaskPriority
import com.ahmad.nafs.domain.goals.GoalCategory
import com.ahmad.nafs.domain.goals.GoalType
import com.ahmad.nafs.domain.habits.HabitType
import com.ahmad.nafs.domain.habits.ScheduleKind
import com.ahmad.nafs.domain.tasks.TaskCompletionStatus
import com.ahmad.nafs.domain.tasks.TaskPeriodType
import com.ahmad.nafs.ui.theme.NafsTheme
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Renders [content] in dark and light themes to `build/outputs/roborazzi/<name>_dark|light.png`.
 * Test classes using it need `@RunWith(AndroidJUnit4::class)`, `@GraphicsMode(NATIVE)` and
 * `@Config(sdk = [34], qualifiers = "w393dp-h852dp-xhdpi", application = Application::class)`.
 */
internal fun captureScreen(name: String, content: @Composable () -> Unit) {
    listOf(ThemeMode.DARK to "dark", ThemeMode.LIGHT to "light").forEach { (mode, suffix) ->
        captureRoboImage("build/outputs/roborazzi/${name}_$suffix.png") {
            NafsTheme(mode) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize()) { content() }
                }
            }
        }
    }
}

/** Fixed, realistic sample records shared by screenshot tests. */
internal object SampleData {
    val today: LocalDate = LocalDate.of(2026, 10, 10)
    const val USER = "user-1"

    val profile = UserProfile(
        userId = USER, name = "Ahmad Siddique", email = "ahmad@example.com", timezone = "Asia/Karachi",
        avatarUrl = null, onboardingComplete = true, deenEnabled = true,
    )

    fun task(id: String, title: String, priority: TaskPriority, done: Boolean, due: String? = null, note: String? = null,
        type: TaskPeriodType = TaskPeriodType.DAILY, date: LocalDate = today) = NafsTask(
        id = id, userId = USER, title = title, note = note, type = type, priority = priority,
        status = if (done) TaskCompletionStatus.COMPLETED else TaskCompletionStatus.ACTIVE,
        periodDate = date, dueTime = due, completedAt = null, createdAt = "2026-10-10T06:00:00Z", updatedAt = "2026-10-10T06:00:00Z",
    )

    val tasks = listOf(
        task("t1", "Finish quarterly report draft", TaskPriority.HIGH, done = true, due = "17:00:00"),
        task("t2", "Call Ammi", TaskPriority.MEDIUM, done = false, due = "20:00:00"),
        task("t3", "Pay electricity bill", TaskPriority.HIGH, done = true, due = "14:00:00"),
        task("t4", "Review PR from Bilal", TaskPriority.MEDIUM, done = false),
        task("t5", "Buy groceries", TaskPriority.LOW, done = false, note = "Milk, eggs, dates, rice"),
    )

    fun habit(id: String, name: String, emoji: String, type: HabitType = HabitType.SIMPLE, target: Double = 1.0, unit: String = "",
        minutes: Int = 0, streak: Int = 0, best: Int = 0, kind: ScheduleKind = ScheduleKind.DAILY, days: Set<DayOfWeek> = emptySet(),
        subject: String? = null, subjectTotal: Int? = null, position: Int = 0, paused: Boolean = false, order: Int = 0) = Habit(
        id = id, userId = USER, name = name, emoji = emoji, type = type, targetValue = target, unit = unit,
        timeTargetMinutes = minutes, subjectName = subject, subjectTotal = subjectTotal, subjectPosition = position,
        subjectUnit = if (subject != null) "ayahs" else null, scheduleKind = kind, scheduleDays = days, weeklyTarget = null,
        isPaused = paused, sortOrder = order, currentStreak = streak, longestStreak = best,
    )

    val habits = listOf(
        habit("h1", "Read", "📖", HabitType.DURATION, minutes = 30, streak = 12, best = 18, order = 0),
        habit("h2", "Drink water", "💧", HabitType.COUNTER, target = 8.0, unit = "glasses", streak = 5, best = 14, order = 1),
        habit("h3", "Quran", "🕌", HabitType.SUBJECT, target = 10.0, unit = "ayahs", streak = 21, best = 21,
            subject = "Surah Al-Baqarah", subjectTotal = 286, position = 142, order = 2),
        habit("h4", "Workout", "🏋️", streak = 3, best = 9, kind = ScheduleKind.WEEKDAYS,
            days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY), order = 3),
        habit("h5", "Journal", "✍️", streak = 2, best = 7, order = 4),
    )

    val habitLogs = listOf(
        HabitLog(USER, "h1", today, completed = true, value = 35.0, durationMinutes = 35, notes = "Atomic Habits — Ch. 4"),
        HabitLog(USER, "h2", today, completed = false, value = 5.0, durationMinutes = 0, notes = null),
        HabitLog(USER, "h3", today, completed = true, value = 10.0, durationMinutes = 0, notes = null),
    )

    private fun milestone(goal: String, id: String, title: String, done: Boolean) =
        GoalMilestone(id, goal, title, done, if (done) "2026-10-01T00:00:00Z" else null, null, "2026-09-01T00:00:00Z")

    val goals = listOf(
        Goal(
            id = "g1", userId = USER, title = "Memorise Surah Al-Mulk", emoji = "🕌", description = null,
            type = GoalType.MONTHLY, category = GoalCategory.DEEN, startDate = today.minusDays(10), deadline = today.plusDays(20),
            storedProgressPercent = 70, targetValue = 30.0, currentValue = 21.0, unit = "ayahs", linkedHabitIds = emptyList(),
            milestones = listOf(milestone("g1", "m1", "Ayahs 1–10", true), milestone("g1", "m2", "Ayahs 11–20", true),
                milestone("g1", "m3", "Ayahs 21–30", false)),
            createdAt = "2026-09-30T00:00:00Z", updatedAt = "2026-10-08T00:00:00Z",
        ),
        Goal(
            id = "g2", userId = USER, title = "Run a 10K under 55 minutes", emoji = "🏃", description = "Build up from 5K with three runs a week.",
            type = GoalType.QUARTERLY, category = GoalCategory.HEALTH, startDate = today.minusDays(42), deadline = today.plusDays(48),
            storedProgressPercent = 45, targetValue = 10.0, currentValue = 6.5, unit = "km", linkedHabitIds = listOf("h4"),
            milestones = listOf(milestone("g2", "m4", "Run 5K without stopping", true), milestone("g2", "m5", "Run 7.5K", true),
                milestone("g2", "m6", "Run 10K", false), milestone("g2", "m7", "10K under 55 min", false)),
            createdAt = "2026-08-20T00:00:00Z", updatedAt = "2026-10-01T00:00:00Z",
        ),
        Goal(
            id = "g3", userId = USER, title = "Land a senior engineering role", emoji = "💼", description = "Portfolio, system design, 20 applications.",
            type = GoalType.YEARLY, category = GoalCategory.CAREER, startDate = today.minusDays(60), deadline = today.plusDays(140),
            storedProgressPercent = 20, targetValue = null, currentValue = null, unit = null, linkedHabitIds = emptyList(),
            milestones = listOf(milestone("g3", "m8", "Update portfolio", true), milestone("g3", "m9", "System design course", false),
                milestone("g3", "m10", "20 applications", false), milestone("g3", "m11", "Five mock interviews", false)),
            createdAt = "2026-08-10T00:00:00Z", updatedAt = "2026-10-05T00:00:00Z",
        ),
    )
}
