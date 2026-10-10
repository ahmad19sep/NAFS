package com.ahmad.nafs.screenshots

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ahmad.nafs.core.habits.HabitLog
import com.ahmad.nafs.core.tasks.TaskPriority
import com.ahmad.nafs.domain.habits.HabitType
import com.ahmad.nafs.domain.habits.ScheduleKind
import com.ahmad.nafs.domain.tasks.TaskPeriod
import com.ahmad.nafs.domain.tasks.TaskPeriodType
import com.ahmad.nafs.ui.NafsBottomBar
import com.ahmad.nafs.ui.habits.HabitActions
import com.ahmad.nafs.ui.habits.HabitDefinitionDraftState
import com.ahmad.nafs.ui.habits.HabitEditorActions
import com.ahmad.nafs.ui.habits.HabitEditorContent
import com.ahmad.nafs.ui.habits.HabitHistoryContent
import com.ahmad.nafs.ui.habits.HabitLogContent
import com.ahmad.nafs.ui.habits.HabitScreen
import com.ahmad.nafs.ui.habits.HabitUiState
import com.ahmad.nafs.ui.tasks.TaskActions
import com.ahmad.nafs.ui.tasks.TaskDraftState
import com.ahmad.nafs.ui.tasks.TaskEditorContent
import com.ahmad.nafs.ui.tasks.TaskScreen
import com.ahmad.nafs.ui.tasks.TaskUiState
import java.time.DayOfWeek
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Tasks and Habits rendered with fixed sample state, in dark and light.
 * Images are written only by `./gradlew :app:recordRoborazziDebug`; plain unit test runs skip them.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A plain Application: the real one schedules WorkManager jobs that have no place in a render test.
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xhdpi", application = Application::class)
class TasksHabitsScreenshotTest : ScreenshotTestBase() {

    private val today = SampleData.today

    // ─── Tasks ───────────────────────────────────────────────────────────────

    private val dailyTasks = SampleData.tasks + listOf(
        SampleData.task("t6", "Send invoice to Hamza", TaskPriority.HIGH, done = false, date = today.minusDays(1)),
        SampleData.task("t7", "Back up phone photos", TaskPriority.LOW, done = true, date = today.minusDays(1)),
    )

    private val weeklyTasks = listOf(
        SampleData.task("w1", "Plan next week's meals", TaskPriority.MEDIUM, done = true,
            type = TaskPeriodType.WEEKLY, date = TaskPeriod.key(TaskPeriodType.WEEKLY, today)),
        SampleData.task("w2", "Deep-clean the kitchen", TaskPriority.LOW, done = false,
            type = TaskPeriodType.WEEKLY, date = TaskPeriod.key(TaskPeriodType.WEEKLY, today), note = "Oven and fridge"),
        SampleData.task("w3", "Finish system design chapter", TaskPriority.HIGH, done = false,
            type = TaskPeriodType.WEEKLY, date = TaskPeriod.key(TaskPeriodType.WEEKLY, today).minusWeeks(1)),
    )

    private val taskState = TaskUiState(
        userId = SampleData.USER,
        timezone = "Asia/Karachi",
        today = today,
        tasks = dailyTasks,
        isLoading = false,
        pendingTaskIds = setOf("t4"),
    )

    @Test fun tasksToday() = captureScreen("20_tasks_today") {
        WithTabBar("tasks") { TaskScreen(taskState, TaskActions()) }
    }

    @Test fun tasksEmpty() = captureScreen("20_tasks_empty") {
        WithTabBar("tasks") {
            TaskScreen(TaskUiState(userId = SampleData.USER, today = today, isLoading = false), TaskActions())
        }
    }

    @Test fun tasksWeeklyOffline() = captureScreen("20_tasks_weekly_offline") {
        TaskScreen(
            taskState.copy(
                selectedType = TaskPeriodType.WEEKLY,
                tasks = weeklyTasks,
                isOffline = true,
                actionError = "Task saved, but notifications are disabled. You can enable them in Android settings.",
                pendingTaskIds = emptySet(),
            ),
            TaskActions(),
        )
    }

    @Test fun tasksLoadError() = captureScreen("20_tasks_load_error") {
        TaskScreen(
            TaskUiState(
                userId = SampleData.USER,
                today = today,
                isLoading = false,
                loadError = "We couldn't reach NAFS. Check your connection and try again.",
            ),
            TaskActions(),
        )
    }

    @Test fun tasksLoading() = captureScreen("20_tasks_loading") {
        TaskScreen(TaskUiState(userId = SampleData.USER, today = today, isLoading = true), TaskActions())
    }

    @Test fun tasksEditor() = captureScreen("20_tasks_editor") {
        SheetFrame {
            TaskEditorContent(
                state = TaskUiState(
                    userId = SampleData.USER,
                    today = today,
                    isLoading = false,
                    showCreateDialog = true,
                    draft = TaskDraftState(
                        title = "Call Ammi",
                        note = "Ask about Sunday lunch",
                        priority = TaskPriority.HIGH,
                        dueTime = "20:00",
                    ),
                ),
                actions = TaskActions(),
                onCancel = {},
            )
        }
    }

    // ─── Habits ──────────────────────────────────────────────────────────────

    private val habits = SampleData.habits + listOf(
        SampleData.habit("h6", "Meal prep", "🥗", streak = 4, kind = ScheduleKind.WEEKDAYS,
            days = setOf(DayOfWeek.SUNDAY), order = 5),
        SampleData.habit("h7", "Cold shower", "🚿", paused = true, order = 6),
    )

    private fun pastLog(habitId: String, daysAgo: Long, value: Double, minutes: Int = 0, notes: String? = null) = HabitLog(
        userId = SampleData.USER,
        habitId = habitId,
        date = today.minusDays(daysAgo),
        completed = true,
        value = value,
        durationMinutes = minutes,
        notes = notes,
    )

    private val history: List<HabitLog> =
        (1L..11L).map { days -> pastLog("h1", days, value = 30.0, minutes = 30, notes = if (days == 2L) "Deep Work — Ch. 2" else null) } +
            (1L..20L).map { days -> pastLog("h3", days, value = 10.0) } +
            (1L..4L).map { days -> pastLog("h2", days, value = 8.0) }

    private val habitState = HabitUiState(
        userId = SampleData.USER,
        timezone = "Asia/Karachi",
        today = today,
        habits = habits,
        logs = SampleData.habitLogs + history,
        isLoading = false,
    )

    @Test fun habitsToday() = captureScreen("30_habits_today") {
        WithTabBar("habits") { HabitScreen(habitState, HabitActions(), HabitEditorActions()) }
    }

    @Test fun habitsRestDay() = captureScreen("30_habits_rest_day_offline") {
        HabitScreen(
            habitState.copy(
                habits = habits.filter { it.id == "h6" || it.id == "h7" },
                isOffline = true,
                pendingHabitIds = setOf("h7"),
            ),
            HabitActions(),
            HabitEditorActions(),
        )
    }

    @Test fun habitsEmpty() = captureScreen("30_habits_empty") {
        WithTabBar("habits") {
            HabitScreen(HabitUiState(userId = SampleData.USER, today = today, isLoading = false), HabitActions(), HabitEditorActions())
        }
    }

    @Test fun habitsLoadError() = captureScreen("30_habits_load_error") {
        HabitScreen(
            HabitUiState(userId = SampleData.USER, today = today, isLoading = false, error = "Habits could not be loaded."),
            HabitActions(),
            HabitEditorActions(),
        )
    }

    @Test fun habitsEditor() = captureScreen("30_habits_editor") {
        SheetFrame {
            HabitEditorContent(
                draft = HabitDefinitionDraftState(
                    name = "Drink water",
                    emoji = "💧",
                    type = HabitType.COUNTER,
                    targetValue = "8",
                    unit = "glasses",
                    scheduleKind = ScheduleKind.WEEKDAYS,
                    scheduleDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                ),
                isSaving = false,
                error = null,
                actions = HabitEditorActions(),
                onCancel = {},
            )
        }
    }

    @Test fun habitsLog() = captureScreen("30_habits_log") {
        SheetFrame {
            HabitLogContent(
                habit = habits.first { it.id == "h2" },
                state = habitState.copy(editingHabitId = "h2", editingDate = today, amount = "5"),
                actions = HabitActions(),
                onCancel = {},
            )
        }
    }

    @Test fun habitsHistory() = captureScreen("30_habits_history") {
        SheetFrame {
            HabitHistoryContent(
                habit = habits.first { it.id == "h1" },
                logs = habitState.logs.filter { it.habitId == "h1" }.sortedByDescending { it.date },
                onEditEntry = {},
                onClose = {},
            )
        }
    }
}

/** Tab screens sit above the bottom bar in the app shell. */
@Composable
private fun WithTabBar(route: String, content: @Composable () -> Unit) {
    Column {
        Box(Modifier.weight(1f)) { content() }
        NafsBottomBar(currentRoute = route, onSelect = {}, onQuickAdd = {})
    }
}

/** A stand-in for ModalBottomSheet (which opens its own window) so sheet content can be captured. */
@Composable
private fun SheetFrame(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)).padding(top = 24.dp)) {
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .padding(vertical = 14.dp)
                        .size(width = 32.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                )
                content()
            }
        }
    }
}
