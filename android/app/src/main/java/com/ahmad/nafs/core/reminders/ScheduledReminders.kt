package com.ahmad.nafs.core.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.work.*
import com.ahmad.nafs.core.auth.KeystoreSessionManager
import com.ahmad.nafs.core.checkin.CheckInLoadResult
import com.ahmad.nafs.core.checkin.CheckInRepository
import com.ahmad.nafs.core.deen.Coordinates
import com.ahmad.nafs.core.deen.DeenLoadResult
import com.ahmad.nafs.core.deen.DeenRepository
import com.ahmad.nafs.core.habits.HabitLoadResult
import com.ahmad.nafs.core.habits.HabitRepository
import com.ahmad.nafs.core.network.NafsApiClient
import com.ahmad.nafs.core.network.SupabaseApiSessionTokens
import com.ahmad.nafs.core.network.SupabaseClientProvider
import com.ahmad.nafs.core.records.PrivateRecordStore
import com.ahmad.nafs.core.records.RecordRules
import com.ahmad.nafs.core.tasks.TaskLoadResult
import com.ahmad.nafs.core.tasks.TaskRepository
import com.ahmad.nafs.core.time.AccountDate
import com.ahmad.nafs.domain.habits.HabitStreak
import com.ahmad.nafs.domain.reminders.HabitReminderRules
import com.ahmad.nafs.domain.reminders.PrayerTrigger
import com.ahmad.nafs.domain.tasks.TaskPeriod
import com.ahmad.nafs.domain.tasks.TaskPeriodType
import com.ahmad.nafs.core.tasks.TaskPriority
import androidx.core.app.NotificationCompat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.ahmad.nafs.domain.reminders.ScheduledReminderRules
import com.ahmad.nafs.ui.dashboard.DashboardUiState
import com.ahmad.nafs.ui.dashboard.metrics
import com.ahmad.nafs.ui.deen.PrayerTimeRules
import com.ahmad.nafs.ui.deen.PrayerTimesRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.auth.auth
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

@EntryPoint @InstallIn(SingletonComponent::class)
interface ScheduledReminderDependencies {
    fun scheduler(): ReminderAlarmScheduler
    fun tasks(): TaskRepository
    fun habits(): HabitRepository
    fun deen(): DeenRepository
    fun api(): NafsApiClient
    fun records(): PrivateRecordStore
    fun prayerTimes(): PrayerTimesRepository
    fun provider(): SupabaseClientProvider
    fun sessions(): SupabaseApiSessionTokens
    fun storedSession(): KeystoreSessionManager
    fun taskReminders(): TaskReminderScheduler
    fun habitReminders(): HabitReminderScheduler
    fun checkIns(): CheckInRepository
}

internal fun Context.reminderDependencies() =
    EntryPointAccessors.fromApplication(applicationContext, ScheduledReminderDependencies::class.java)

/**
 * Local reminders need to know this account is still signed in on this device, not a fresh token.
 * An expired access token cannot be refreshed offline, so the encrypted stored session is the fallback;
 * signing out or a revoked refresh token deletes it.
 */
internal suspend fun ScheduledReminderDependencies.signedIn(user: String): Boolean {
    val auth = provider().client?.auth ?: return false
    withTimeoutOrNull(5_000L) { auth.awaitInitialization() }
    if (sessions().isCurrentUser(user)) return true
    return storedSession().storedUserId() == user
}

/** Task alarms do not survive a reboot; rebuild them from the tasks, using the offline copy if needed. */
internal suspend fun ScheduledReminderDependencies.rearmTasks(context: Context, account: ReminderAccount) {
    if (!ReminderPreferences.read(context, account.user).tasks) return
    val today = runCatching { AccountDate.today(account.zone) }.getOrNull() ?: return
    val loaded = withTimeoutOrNull(15_000L) { tasks().load(account.user, today) } as? TaskLoadResult.Success ?: return
    if (ReminderAccount.read(context)?.user == account.user) taskReminders().reconcile(account.user, loaded.tasks, account.zone)
}

/** The signed-in account whose reminders are scheduled; read after reboots and clock changes, cleared on sign-out. */
data class ReminderAccount(val user: String, val zone: String, val deen: Boolean) {
    companion object {
        private const val FILE = "scheduled_reminder_account"
        fun read(context: Context): ReminderAccount? {
            val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            val user = preferences.getString("user", null) ?: return null
            val zone = preferences.getString("zone", null) ?: return null
            return ReminderAccount(user, zone, preferences.getBoolean("deen", false))
        }
        fun save(context: Context, account: ReminderAccount) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit(commit = true) { putString("user", account.user); putString("zone", account.zone); putBoolean("deen", account.deen) }
        fun clear(context: Context, user: String) {
            if (read(context)?.user == user) context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit(commit = true) { clear() }
        }
    }
}

/** Exact when the user allows "Alarms & reminders"; otherwise Android may deliver within about an hour. */
internal object ExactAlarms {
    fun canSchedule(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun intent(context: Context, action: String, key: String): Intent =
        Intent(context, ReminderAlarmReceiver::class.java).setAction(action).setData("nafs-reminder://$key".toUri())

    fun set(context: Context, intent: Intent, at: Instant) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try {
            if (canSchedule(context)) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pending)
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pending)
        } catch (_: SecurityException) {
            // The exact-alarm permission was revoked between the check and the call.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pending)
        }
    }

    fun cancel(context: Context, intent: Intent) {
        PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            ?.let { context.getSystemService(AlarmManager::class.java).cancel(it); it.cancel() }
    }
}

@Singleton
class ReminderAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val habitReminders: HabitReminderScheduler,
) {
    private val work = WorkManager.getInstance(context)

    fun canScheduleExact(): Boolean = ExactAlarms.canSchedule(context)

    fun reschedule(user: String, zone: String, deen: Boolean) {
        // An account zone java.time rejects cannot place any alarm; drop them instead of throwing on the main thread.
        if (runCatching { ZoneId.of(zone) }.isFailure) { cancelAll(user); return }
        ReminderAccount.save(context, ReminderAccount(user, zone, deen))
        rearmSummaries(user, zone)
        habitReminders.rearm(user, zone)
        if (ReminderPreferences.read(context, user).prayers && deen) {
            val input = workDataOf("user" to user, "zone" to zone)
            val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            // Fetching today and tomorrow every few hours keeps alarms set across midnight without a connection.
            work.enqueueUniquePeriodicWork(prayerWork(user), ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<PrayerPlanWorker>(6, TimeUnit.HOURS).setConstraints(network).setInputData(input).build())
            work.enqueueUniqueWork("${prayerWork(user)}-now", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<PrayerPlanWorker>().setConstraints(network).setInputData(input).build())
        } else cancelPrayers(user)
    }

    /**
     * Sets the next end-of-day and weekly alarms. Safe to call often. An alarm whose time has passed within the last
     * hour is still waiting for delivery (Android may hold inexact alarms back), so it is left alone unless it is the
     * [delivered] one that just fired.
     */
    fun rearmSummaries(user: String, zone: String, delivered: String? = null) {
        if (runCatching { ZoneId.of(zone) }.isFailure) return
        val options = ReminderPreferences.read(context, user)
        val now = Instant.now()
        val slots = context.getSharedPreferences(SLOTS, Context.MODE_PRIVATE)
        fun arm(key: String, action: String, enabled: Boolean, next: Instant, extras: Intent.() -> Unit) {
            val intent = ExactAlarms.intent(context, action, key)
            if (!enabled) { ExactAlarms.cancel(context, intent); slots.edit(commit = true) { remove(key) }; return }
            val armed = Instant.ofEpochMilli(slots.getLong(key, 0L))
            if (key != delivered && !armed.isAfter(now) && armed.isAfter(now.minus(1, ChronoUnit.HOURS))) return
            ExactAlarms.set(context, intent.putExtra(EXTRA_USER, user).apply(extras), next)
            slots.edit(commit = true) { putLong(key, next.toEpochMilli()) }
        }
        arm("morning", ACTION_MORNING, options.morning, ScheduledReminderRules.nextDaily(now, zone, options.morningTime)) { }
        arm("habit-check", ACTION_HABIT_CHECK, options.habits, ScheduledReminderRules.nextDaily(now, zone, options.habitCheckTime)) { }
        arm("daily", ACTION_DAILY, options.daily, ScheduledReminderRules.nextDaily(now, zone, options.dailyTime)) { }
        val weeklyAt = ScheduledReminderRules.nextWeekly(now, zone)
        // The week the report closes, so a tap after midnight still opens that week.
        val weekStart = weeklyAt.atZone(ZoneId.of(zone)).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        arm("weekly", ACTION_WEEKLY, options.weekly, weeklyAt) { putExtra(EXTRA_WEEK_START, weekStart.toString()) }
    }

    /** Removes alarms, background work and notifications already on screen for this account. */
    fun cancelAll(user: String) {
        ExactAlarms.cancel(context, ExactAlarms.intent(context, ACTION_DAILY, "daily"))
        ExactAlarms.cancel(context, ExactAlarms.intent(context, ACTION_WEEKLY, "weekly"))
        ExactAlarms.cancel(context, ExactAlarms.intent(context, ACTION_MORNING, "morning"))
        ExactAlarms.cancel(context, ExactAlarms.intent(context, ACTION_HABIT_CHECK, "habit-check"))
        habitReminders.cancelAll(user)
        context.getSharedPreferences(SLOTS, Context.MODE_PRIVATE).edit(commit = true) { clear() }
        cancelPrayers(user)
        NotificationManagerCompat.from(context).let { shown -> listOf("prayer", "daily", "weekly", "morning", "habits", "letters").forEach { shown.cancel("$it-$user", 0) } }
        ReminderAccount.clear(context, user)
    }

    /** The account whose reminders are scheduled, if any; used to clean up after a sign-out while the app was closed. */
    fun storedUser(): String? = ReminderAccount.read(context)?.user

    /**
     * Replaces prayer alarms for [resolved] dates only, so a failed fetch never removes alarms that are still valid.
     * Dates outside [planned] (today and tomorrow in the account zone) are stale, for example after a timezone change.
     * Alarms in [keep] (started within the last hour, possibly not yet delivered in inexact mode) are left alone.
     */
    internal fun setPrayers(user: String, triggers: List<PrayerTrigger>, planned: Set<LocalDate>, resolved: Set<LocalDate>, keep: Set<String>) {
        val today = LocalDate.now(ZoneOffset.UTC)
        val stale = (-2L..2L).map(today::plusDays).filter { it !in planned }
        (resolved + stale).forEach { date ->
            ScheduledReminderRules.prayers.map { prayerKey(date, it) }.filter { it !in keep }
                .forEach { ExactAlarms.cancel(context, ExactAlarms.intent(context, ACTION_PRAYER, it)) }
        }
        // A fresh clock reading: a prayer that started while times were being fetched must not fire twice.
        val now = Instant.now()
        triggers.filter { it.at.isAfter(now) && prayerKey(it.date, it.prayer) !in keep }.forEach { trigger ->
            ExactAlarms.set(context, ExactAlarms.intent(context, ACTION_PRAYER, prayerKey(trigger.date, trigger.prayer))
                .putExtra(EXTRA_USER, user).putExtra(EXTRA_PRAYER, trigger.prayer).putExtra(EXTRA_TIME, trigger.time.toString())
                .putExtra(EXTRA_DATE, trigger.date.toString()), trigger.at)
        }
    }

    private fun cancelPrayers(user: String) {
        work.cancelUniqueWork(prayerWork(user)); work.cancelUniqueWork("${prayerWork(user)}-now")
        val today = LocalDate.now(ZoneOffset.UTC)
        // Alarms exist only for today and tomorrow in some account zone, so a five-day UTC window covers them all.
        for (offset in -2L..2L) ScheduledReminderRules.prayers.forEach {
            ExactAlarms.cancel(context, ExactAlarms.intent(context, ACTION_PRAYER, prayerKey(today.plusDays(offset), it)))
        }
    }

    private fun prayerWork(user: String) = "prayer-plan-$user"

    companion object {
        const val ACTION_PRAYER = "com.ahmad.nafs.reminders.PRAYER"
        const val ACTION_DAILY = "com.ahmad.nafs.reminders.DAILY"
        const val ACTION_WEEKLY = "com.ahmad.nafs.reminders.WEEKLY"
        const val ACTION_TASK = "com.ahmad.nafs.reminders.TASK"
        const val ACTION_HABIT = "com.ahmad.nafs.reminders.HABIT"
        const val ACTION_MORNING = "com.ahmad.nafs.reminders.MORNING"
        const val ACTION_HABIT_CHECK = "com.ahmad.nafs.reminders.HABIT_CHECK"
        const val EXTRA_DATE = "date"
        const val EXTRA_USER = "user"
        const val EXTRA_PRAYER = "prayer"
        const val EXTRA_TIME = "time"
        const val EXTRA_WEEK_START = "week_start"
        internal fun prayerKey(date: LocalDate, prayer: String) = "prayer/$date/$prayer"
        private const val SLOTS = "scheduled_summary_slots"
    }
}

/** Prayer times for today and tomorrow, from Aladhan with the area or city saved in Prayer times, or their encrypted copies. */
internal object PrayerPlan {
    private const val TODAY_KEY = "prayer-times"
    private const val NEXT_KEY = "prayer-times-next"

    suspend fun schedule(deps: ScheduledReminderDependencies, user: String, zone: String, fetch: Boolean): Boolean {
        val records = deps.records()
        // Restoring after a reboot runs before a token refresh is possible; signedIn() has confirmed the stored session.
        if (!fetch && !deps.signedIn(user)) return true
        val read: suspend (String) -> JsonObject? = if (fetch) { key -> records.read(user, key) } else { key -> records.readForStoredSession(user, key) }
        // The Prayer times screen writes the latest chosen source (area or city) to TODAY_KEY.
        val saved = read(TODAY_KEY) ?: read(NEXT_KEY) ?: return true // No area or city saved yet.
        val area = Coordinates.from(saved)
        val city = RecordRules.text(saved, "city"); val country = RecordRules.text(saved, "country")
        val method = RecordRules.text(saved, "method").toIntOrNull()?.takeIf { it in PrayerTimeRules.methods } ?: 1
        val hanafi = (saved["hanafi"] as? JsonPrimitive)?.booleanOrNull ?: (RecordRules.text(saved, "hanafi") == "true")
        if (area == null && (city.isBlank() || country.isBlank())) return true
        val today = try { AccountDate.today(zone) } catch (_: DateTimeException) { return true }
        // An hour back: in inexact mode a prayer that started recently may not have been delivered yet.
        val since = Instant.now().minus(1, ChronoUnit.HOURS)
        val resolved = mutableSetOf<LocalDate>()
        val planned = setOf(today, today.plusDays(1))
        val triggers = listOf(today to TODAY_KEY, today.plusDays(1) to NEXT_KEY).flatMap { (date, key) ->
            val fetched = if (!fetch) null else try {
                (if (area != null) deps.prayerTimes().fetchAt(area, method, hanafi, zone, date)
                    else deps.prayerTimes().fetch(city, country, method, hanafi, zone, date)).also { records.write(user, key, it) }
            } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { null }
            val data = fetched ?: cached(read, date, zone, saved) ?: return@flatMap emptyList()
            resolved += date
            ScheduledReminderRules.prayerTriggers(date, times(data), zone, since)
        }
        if (resolved.isEmpty() || !deps.signedIn(user)) return resolved.size == 2
        val now = Instant.now()
        // Just after midnight, yesterday's late prayer (for example Isha) may still be waiting for delivery.
        val yesterday = cached(read, today.minusDays(1), zone, saved)?.let { ScheduledReminderRules.prayerTriggers(today.minusDays(1), times(it), zone, since) }.orEmpty()
        val keep = (triggers + yesterday).filter { !it.at.isAfter(now) }.map { ReminderAlarmScheduler.prayerKey(it.date, it.prayer) }.toSet()
        deps.scheduler().setPrayers(user, triggers, planned, resolved, keep)
        return resolved.size == 2
    }

    private suspend fun cached(read: suspend (String) -> JsonObject?, date: LocalDate, zone: String, saved: JsonObject) =
        listOf(TODAY_KEY, NEXT_KEY).firstNotNullOfOrNull { key ->
            read(key)?.takeIf { RecordRules.text(it, "date") == date.toString() && RecordRules.text(it, "zone") == zone && sameSource(it, saved) }
        }

    private fun times(data: JsonObject) = (data["times"] as? JsonObject)?.mapValues { (it.value as? JsonPrimitive)?.contentOrNull.orEmpty() }.orEmpty()

    /** A cached day is reused only if it was calculated for the same area or city and the same method. */
    private fun sameSource(a: JsonObject, b: JsonObject) = Coordinates.from(a) == Coordinates.from(b) &&
        listOf("city", "country", "method", "hanafi").all { RecordRules.text(a, it) == RecordRules.text(b, it) }
}

class PrayerPlanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val user = inputData.getString("user") ?: return Result.failure()
        val zone = inputData.getString("zone") ?: return Result.failure()
        val deps = applicationContext.reminderDependencies()
        val account = ReminderAccount.read(applicationContext)
        if (account?.user != user || !account.deen || !ReminderPreferences.read(applicationContext, user).prayers) return Result.success()
        if (!deps.signedIn(user)) { deps.scheduler().cancelAll(user); return Result.success() }
        return try {
            if (PrayerPlan.schedule(deps, user, zone, fetch = true)) Result.success() else Result.retry()
        } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { Result.retry() }
    }
}

/** Shows prayer, task, end-of-day and weekly notifications, and restores alarms after reboots and clock changes. */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ALARM_ACTIONS && intent.action !in SYSTEM_ACTIONS) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { withTimeoutOrNull(25_000L) { handle(context.applicationContext, intent) } }
            catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { /* Re-armed on the next app start or background check. */ }
            finally { pending.finish() }
        }
    }

    private suspend fun handle(context: Context, intent: Intent) {
        if (intent.action == ReminderAlarmScheduler.ACTION_TASK) { TaskReminderDelivery.deliver(context, intent); return }
        if (intent.action == ReminderAlarmScheduler.ACTION_HABIT) { HabitReminderDelivery.deliver(context, intent); return }
        val account = ReminderAccount.read(context) ?: return
        val deps = context.reminderDependencies()
        when (intent.action) {
            ReminderAlarmScheduler.ACTION_PRAYER, ReminderAlarmScheduler.ACTION_DAILY, ReminderAlarmScheduler.ACTION_WEEKLY,
            ReminderAlarmScheduler.ACTION_MORNING, ReminderAlarmScheduler.ACTION_HABIT_CHECK -> {
                if (intent.getStringExtra(ReminderAlarmScheduler.EXTRA_USER) != account.user) return
                // Each daily/weekly alarm fires once: arm the next one before any network work,
                // so an offline evening or a slow load cannot end the series.
                when (intent.action) {
                    ReminderAlarmScheduler.ACTION_DAILY -> deps.scheduler().rearmSummaries(account.user, account.zone, delivered = "daily")
                    ReminderAlarmScheduler.ACTION_WEEKLY -> deps.scheduler().rearmSummaries(account.user, account.zone, delivered = "weekly")
                    ReminderAlarmScheduler.ACTION_MORNING -> deps.scheduler().rearmSummaries(account.user, account.zone, delivered = "morning")
                    ReminderAlarmScheduler.ACTION_HABIT_CHECK -> deps.scheduler().rearmSummaries(account.user, account.zone, delivered = "habit-check")
                }
                // Offline, the stored session still counts; only a real sign-out or revoked session ends the series.
                if (!deps.signedIn(account.user)) { deps.scheduler().cancelAll(account.user); return }
                val options = ReminderPreferences.read(context, account.user)
                // The account may sign out or switch while content loads; check again before anything is shown.
                fun stillCurrent() = ReminderAccount.read(context)?.user == account.user
                when (intent.action) {
                    ReminderAlarmScheduler.ACTION_PRAYER -> if (options.prayers && account.deen) {
                        val prayer = intent.getStringExtra(ReminderAlarmScheduler.EXTRA_PRAYER) ?: return
                        val time = intent.getStringExtra(ReminderAlarmScheduler.EXTRA_TIME).orEmpty()
                        val date = intent.getStringExtra(ReminderAlarmScheduler.EXTRA_DATE) ?: AccountDate.today(account.zone).toString()
                        PrayerNotifications.show(context, account.user, prayer, time, date)
                    }
                    ReminderAlarmScheduler.ACTION_DAILY -> if (options.daily) {
                        val text = withTimeoutOrNull(15_000L) { daily(deps, account) } ?: GENERIC_DAILY
                        if (stillCurrent()) LocalReminderNotifications.show(context, "daily-${account.user}", "How today went", text, "dashboard", NafsChannel.SUMMARY)
                    }
                    ReminderAlarmScheduler.ACTION_WEEKLY -> if (options.weekly) {
                        val weekStart = intent.getStringExtra(ReminderAlarmScheduler.EXTRA_WEEK_START)
                            ?: AccountDate.today(account.zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()
                        val text = withTimeoutOrNull(15_000L) { weekly(deps, account, weekStart) } ?: GENERIC_WEEKLY
                        if (stillCurrent()) LocalReminderNotifications.show(context, "weekly-${account.user}", "Your week in review", text,
                            "progress?weekStart=$weekStart", NafsChannel.SUMMARY)
                    }
                    ReminderAlarmScheduler.ACTION_MORNING -> if (options.morning) {
                        val (title, body, destination) = withTimeoutOrNull(15_000L) { morning(deps, account) }
                            ?: Triple("Good morning", "Open NAFS to plan your day.", "dashboard")
                        if (stillCurrent()) LocalReminderNotifications.show(context, "morning-${account.user}", title, body, destination, NafsChannel.SUMMARY)
                    }
                    ReminderAlarmScheduler.ACTION_HABIT_CHECK -> if (options.habits) {
                        val check = withTimeoutOrNull(15_000L) { habitCheck(deps, account) }
                        if (check != null && stillCurrent()) LocalReminderNotifications.show(context, "habits-${account.user}", check.first, check.second,
                            "habits", NafsChannel.HABITS)
                    }
                }
            }
            in SYSTEM_ACTIONS -> {
                // Boot, app update, clock/timezone change or exact-alarm permission change.
                if (!deps.signedIn(account.user)) { deps.scheduler().cancelAll(account.user); return }
                deps.scheduler().reschedule(account.user, account.zone, account.deen)
                // Reboots clear alarms; restore prayer alerts from the saved times before any connection is back.
                if (ReminderPreferences.read(context, account.user).prayers && account.deen) {
                    try { PrayerPlan.schedule(deps, account.user, account.zone, fetch = false) }
                    catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { /* The prayer worker re-plans once online. */ }
                }
                deps.rearmTasks(context, account)
            }
        }
    }

    /** Today's plan from fresh copies only; a source that is offline or failed is left out, never counted as zero. */
    private suspend fun morning(deps: ScheduledReminderDependencies, account: ReminderAccount): Triple<String, String, String> {
        val today = AccountDate.today(account.zone)
        val tasks = (deps.tasks().load(account.user, today) as? TaskLoadResult.Success)?.takeUnless { it.isOffline }
        val habits = (deps.habits().load(account.user, today) as? HabitLoadResult.Success)?.takeUnless { it.isOffline }
        val checkIn = (deps.checkIns().load(account.user, today) as? CheckInLoadResult.Success)?.takeUnless { it.isOffline }
        val period = TaskPeriod.key(TaskPeriodType.DAILY, today)
        val open = tasks?.tasks.orEmpty().filter { it.type == TaskPeriodType.DAILY && it.periodDate == period && !it.isCompleted }
        val clock = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
        val first = open.mapNotNull { task -> task.dueTime?.let { due -> runCatching { LocalTime.parse(due.take(5)) }.getOrNull()?.let { task to it } } }
            .minByOrNull { it.second }?.let { (task, at) -> "${task.title.take(60)} at ${at.format(clock)}" }
        val habitsDue = habits?.habits?.count { !it.isPaused && HabitStreak.isScheduled(it.schedule(), today) }
        val hasPriorities = checkIn?.let { it.record?.tasks.orEmpty().isNotEmpty() }
        val (title, body) = ScheduledReminderRules.morningPlan(
            tasksOpen = tasks?.let { open.size },
            highPriority = open.count { it.priority == TaskPriority.HIGH },
            habitsDue = habitsDue,
            firstDue = first,
            hasPriorities = hasPriorities,
        )
        return Triple(title, body, if (hasPriorities == false) "checkin" else "dashboard")
    }

    /** The evening habit check, from a fresh copy only: an offline one could nag about habits done on another device. */
    private suspend fun habitCheck(deps: ScheduledReminderDependencies, account: ReminderAccount): Pair<String, String>? {
        val today = AccountDate.today(account.zone)
        val loaded = (deps.habits().load(account.user, today) as? HabitLoadResult.Success)?.takeUnless { it.isOffline } ?: return null
        val open = loaded.habits.filter { habit -> HabitReminderGate.shouldRemind(habit, loaded.logs.filter { it.habitId == habit.id }, today) }
            .map { habit -> HabitReminderGate.facts(habit, loaded.logs.firstOrNull { it.habitId == habit.id && it.date == today }) }
        return HabitReminderRules.eveningCheck(open)
    }

    /** The same counts as Home. Offline copies and failed loads are unknown, never zero. */
    private suspend fun daily(deps: ScheduledReminderDependencies, account: ReminderAccount): String {
        val today = AccountDate.today(account.zone)
        val tasks = (deps.tasks().load(account.user, today) as? TaskLoadResult.Success)?.takeUnless { it.isOffline }
        val habits = (deps.habits().load(account.user, today) as? HabitLoadResult.Success)?.takeUnless { it.isOffline }
        val prayers = if (account.deen) (deps.deen().load(account.user, today) as? DeenLoadResult.Success)?.takeUnless { it.isOffline } else null
        if (tasks == null && habits == null && prayers == null) return GENERIC_DAILY
        val metrics = DashboardUiState(userId = account.user, timezone = account.zone, today = today,
            tasks = tasks?.tasks.orEmpty(), habits = habits?.habits.orEmpty(), habitLogs = habits?.logs.orEmpty()).metrics()
        return ScheduledReminderRules.dailySummary(
            tasksDone = tasks?.let { metrics.completedTasks }, tasksTotal = tasks?.let { metrics.totalTasks },
            habitsDone = habits?.let { metrics.completedHabits }, habitsTotal = habits?.let { metrics.totalHabits },
            // A day with no prayer record is unrecorded, not 0 of 5.
            prayersDone = prayers?.log?.prayedCount,
            incomplete = tasks == null || habits == null || (account.deen && prayers == null),
        )
    }

    /** The canonical weekly report for the week the alarm closed, also cached so the report screen opens offline. */
    private suspend fun weekly(deps: ScheduledReminderDependencies, account: ReminderAccount, weekStart: String): String = try {
        val currentMonday = AccountDate.today(account.zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val offset = ChronoUnit.WEEKS.between(LocalDate.parse(weekStart), currentMonday).toInt().coerceIn(0, 60)
        val response = deps.api().post(account.user, "/api/progress", buildJsonObject { put("period", "weekly"); put("offset", offset) })
        val report = response["report"] as JsonObject
        runCatching { deps.records().write(account.user, "progress/weekly/$offset", response) }
        val missing = (response["unavailable"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toSet()
        val deen = (report["deenEnabled"] as? JsonPrimitive)?.booleanOrNull == true
        val habitsKnown = "habits" !in missing && "habitLogs" !in missing && (report["habits"] as? JsonArray)?.isNotEmpty() == true
        val prayersKnown = deen && "prayerLogs" !in missing
        // The score averages several sources, so any failed read makes it unknown.
        val scoreKnown = missing.none { it != "review" }
        ScheduledReminderRules.weeklySummary(
            daysLogged = RecordRules.text(report, "days_logged").toIntOrNull() ?: 0,
            daysElapsed = RecordRules.text(report, "days_elapsed"),
            avgScore = RecordRules.text(report, "avg_score").takeIf { scoreKnown },
            tasksDone = RecordRules.text(report, "tasks_completed"),
            tasksTotal = RecordRules.text(report, "tasks_total").takeIf { "tasks" !in missing },
            habitPct = RecordRules.text(report, "habit_completion_pct").takeIf { habitsKnown },
            prayerPoints = RecordRules.text(report, "prayer_points").takeIf { prayersKnown },
            prayerMax = RecordRules.text(report, "prayer_points_max").takeIf { prayersKnown },
            incomplete = missing.any { it != "review" },
        )
    } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { GENERIC_WEEKLY }

    private companion object {
        const val GENERIC_DAILY = "Open NAFS to review today and plan tomorrow."
        const val GENERIC_WEEKLY = "Your weekly report is ready. Tap to open it."
        val ALARM_ACTIONS = setOf(ReminderAlarmScheduler.ACTION_PRAYER, ReminderAlarmScheduler.ACTION_DAILY,
            ReminderAlarmScheduler.ACTION_WEEKLY, ReminderAlarmScheduler.ACTION_TASK, ReminderAlarmScheduler.ACTION_HABIT,
            ReminderAlarmScheduler.ACTION_MORNING, ReminderAlarmScheduler.ACTION_HABIT_CHECK)
        val SYSTEM_ACTIONS = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED, "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED")
    }
}

/** The prayer-start alert, with buttons that record the prayer without opening the app. */
internal object PrayerNotifications {
    fun show(context: Context, user: String, prayer: String, time: String, date: String) {
        val tag = "prayer-$user"
        val clock = runCatching { LocalTime.parse(time.trim().take(5)).format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)) }.getOrDefault(time)
        fun button(status: Int, key: String) = ReminderActions.pending(context, ReminderActions.PRAYER_DONE, "$tag/$date/$prayer/$key", tag) {
            putExtra(ReminderActions.EXTRA_USER, user)
            putExtra(ReminderActions.EXTRA_ID, prayer)
            putExtra(ReminderActions.EXTRA_DATE, date)
            putExtra(ReminderActions.EXTRA_STATUS, status)
        }
        NafsNotifications.notify(context, tag, NafsNotifications.builder(context, NafsChannel.PRAYER)
            .setContentTitle("$prayer · $clock")
            .setContentText("It's time for $prayer.")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(NafsNotifications.openApp(context, tag, "deen"))
            .addAction(0, "Prayed", button(1, "alone"))
            .addAction(0, "In jamaat", button(2, "jamaat")))
    }
}
