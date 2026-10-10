package com.ahmad.nafs.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.ahmad.nafs.MainActivity
import com.ahmad.nafs.core.habits.HabitLoadResult
import com.ahmad.nafs.core.reminders.ReminderAccount
import com.ahmad.nafs.core.reminders.reminderDependencies
import com.ahmad.nafs.core.reminders.signedIn
import com.ahmad.nafs.core.tasks.TaskLoadResult
import com.ahmad.nafs.core.time.AccountDate
import com.ahmad.nafs.domain.habits.HabitResult
import com.ahmad.nafs.domain.habits.HabitStreak
import com.ahmad.nafs.domain.tasks.TaskPeriod
import com.ahmad.nafs.domain.tasks.TaskPeriodType
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** What the Today widget shows, read from the same repositories as Home (their offline copies when needed). */
internal sealed interface TodaySnapshot {
    data object SignedOut : TodaySnapshot
    data object Unavailable : TodaySnapshot
    data class Ready(
        val tasksDone: Int,
        val tasksTotal: Int,
        val habitsDone: Int,
        val habitsTotal: Int,
        val open: List<Pair<String, String?>>,
        val offline: Boolean,
    ) : TodaySnapshot

    companion object {
        suspend fun load(context: Context): TodaySnapshot {
            val account = ReminderAccount.read(context) ?: return SignedOut
            val deps = context.reminderDependencies()
            if (!deps.signedIn(account.user)) return SignedOut
            val today = runCatching { AccountDate.today(account.zone) }.getOrNull() ?: return Unavailable
            val tasks = withTimeoutOrNull(10_000L) { deps.tasks().load(account.user, today) } as? TaskLoadResult.Success
            val habits = withTimeoutOrNull(10_000L) { deps.habits().load(account.user, today) } as? HabitLoadResult.Success
            if (tasks == null && habits == null) return Unavailable
            val period = TaskPeriod.key(TaskPeriodType.DAILY, today)
            val todayTasks = tasks?.tasks.orEmpty().filter { it.type == TaskPeriodType.DAILY && it.periodDate == period }
            val clock = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
            val open = todayTasks.filter { !it.isCompleted }
                .sortedWith(compareBy({ it.dueTime == null }, { it.dueTime }, { -it.priority.rank }))
                .map { task -> task.title to task.dueTime?.let { due -> runCatching { LocalTime.parse(due.take(5)).format(clock) }.getOrNull() } }
            val scheduled = habits?.habits.orEmpty().filter { !it.isPaused && HabitStreak.isScheduled(it.schedule(), today) }
            val logs = habits?.logs.orEmpty().filter { it.date == today }.associateBy { it.habitId }
            val habitsDone = scheduled.count { habit ->
                HabitStreak.isComplete(habit.schedule(), logs[habit.id]?.let { HabitResult(it.date, it.completed, it.value, it.durationMinutes) })
            }
            return Ready(
                tasksDone = todayTasks.count { it.isCompleted },
                tasksTotal = todayTasks.size,
                habitsDone = habitsDone,
                habitsTotal = scheduled.size,
                open = open,
                offline = tasks?.isOffline != false || habits?.isOffline != false,
            )
        }
    }
}

class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = runCatching { TodaySnapshot.load(context) }.getOrDefault(TodaySnapshot.Unavailable)
        provideContent { TodayContent(snapshot) }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

/** Refreshes placed widgets, for example when the user leaves the app or completes something from a notification. */
object NafsWidgets {
    fun refresh(context: Context) {
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { runCatching { TodayWidget().updateAll(app) } }
    }
}

private val Surface = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF14211F))
private val OnSurface = ColorProvider(day = Color(0xFF10201E), night = Color(0xFFE8F0EE))
private val Muted = ColorProvider(day = Color(0xFF5B6B68), night = Color(0xFFA9BAB6))
private val Accent = ColorProvider(day = Color(0xFF0F7466), night = Color(0xFF5EDBC0))
private val Track = ColorProvider(day = Color(0xFFE3ECE9), night = Color(0xFF263634))
private val Gold = ColorProvider(day = Color(0xFFB07A12), night = Color(0xFFE9C46A))

private fun open(destination: String) =
    actionStartActivity<MainActivity>(actionParametersOf(ActionParameters.Key<String>(MainActivity.EXTRA_DESTINATION) to destination))

@Composable
private fun TodayContent(snapshot: TodaySnapshot) {
    Column(
        modifier = GlanceModifier.fillMaxSize().background(Surface).cornerRadius(24.dp).padding(16.dp).clickable(open("dashboard")),
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("NAFS · Today", style = TextStyle(color = OnSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.defaultWeight())
            if (snapshot is TodaySnapshot.Ready) Box(
                modifier = GlanceModifier.size(32.dp).background(Accent).cornerRadius(16.dp).clickable(open("tasks/new")),
                contentAlignment = Alignment.Center,
            ) {
                Text("+", style = TextStyle(color = ColorProvider(day = Color.White, night = Color(0xFF0B1A18)), fontSize = 18.sp, fontWeight = FontWeight.Bold))
            }
        }
        Spacer(GlanceModifier.height(8.dp))
        when (snapshot) {
            TodaySnapshot.SignedOut -> Text("Sign in to NAFS to see today here.", style = TextStyle(color = Muted, fontSize = 13.sp))
            TodaySnapshot.Unavailable -> Text("Open NAFS to load today.", style = TextStyle(color = Muted, fontSize = 13.sp))
            is TodaySnapshot.Ready -> ReadyContent(snapshot)
        }
    }
}

@Composable
private fun ReadyContent(snapshot: TodaySnapshot.Ready) {
    val total = snapshot.tasksTotal + snapshot.habitsTotal
    val done = snapshot.tasksDone + snapshot.habitsDone
    val counts = buildList {
        if (snapshot.tasksTotal > 0) add("${snapshot.tasksDone}/${snapshot.tasksTotal} tasks")
        if (snapshot.habitsTotal > 0) add("${snapshot.habitsDone}/${snapshot.habitsTotal} habits")
    }.joinToString(" · ").ifEmpty { "Nothing planned yet" }
    Text(counts + if (snapshot.offline) " · saved copy" else "", style = TextStyle(color = Muted, fontSize = 13.sp))
    if (total > 0) {
        Spacer(GlanceModifier.height(8.dp))
        LinearProgressIndicator(
            progress = done.toFloat() / total,
            modifier = GlanceModifier.fillMaxWidth().height(6.dp),
            color = Accent,
            backgroundColor = Track,
        )
    }
    Spacer(GlanceModifier.height(10.dp))
    when {
        total > 0 && done == total -> Text("Everything planned is done today ✓", style = TextStyle(color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Medium))
        snapshot.open.isEmpty() -> Text("Tap + to plan a task.", style = TextStyle(color = Muted, fontSize = 13.sp))
        else -> snapshot.open.take(3).forEach { (title, time) ->
            Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp).clickable(open("tasks")), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = GlanceModifier.size(8.dp).background(Accent).cornerRadius(4.dp)) {}
                Spacer(GlanceModifier.width(8.dp))
                Text(title, modifier = GlanceModifier.defaultWeight(), style = TextStyle(color = OnSurface, fontSize = 13.sp), maxLines = 1)
                time?.let { Text(it, style = TextStyle(color = Muted, fontSize = 12.sp)) }
            }
        }
    }
}
