package com.ahmad.nafs.core.reminders

import android.content.Context
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ahmad.nafs.core.habits.HabitLoadResult
import com.ahmad.nafs.core.habits.HabitRepository
import com.ahmad.nafs.core.network.SupabaseApiSessionTokens
import com.ahmad.nafs.core.network.SupabaseClientProvider
import com.ahmad.nafs.core.records.RecordRules
import com.ahmad.nafs.core.time.AccountDate
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject

@EntryPoint @InstallIn(SingletonComponent::class)
interface ActivityReminderDependencies {
    fun scheduler(): ReminderAlarmScheduler
    fun habits(): HabitRepository
    fun habitReminders(): HabitReminderScheduler
    fun provider(): SupabaseClientProvider
    fun sessions(): SupabaseApiSessionTokens
}

@Singleton
class ActivityReminderScheduler @Inject constructor(@ApplicationContext context: Context) {
    private val work = WorkManager.getInstance(context)
    fun schedule(user: String, zone: String) {
        work.enqueueUniquePeriodicWork("activity-reminders-$user", ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<ActivityReminderWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(workDataOf("user" to user, "zone" to zone)).build())
    }
    fun cancel(user: String) { work.cancelUniqueWork("activity-reminders-$user") }
}

/**
 * The background safety net, every few hours while online: re-arms alarms the system dropped (revoked exact-alarm
 * access, aggressive battery savers), keeps habit reminders in step with edits made on the web, and announces
 * future-self letters that are ready.
 */
class ActivityReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val user = inputData.getString("user") ?: return Result.failure()
        val zone = inputData.getString("zone") ?: return Result.failure()
        val dependencies = EntryPointAccessors.fromApplication(applicationContext, ActivityReminderDependencies::class.java)
        val reminders = applicationContext.reminderDependencies()
        ReminderAccount.read(applicationContext)?.takeIf { it.user == user && reminders.signedIn(user) }?.let { account ->
            reminders.scheduler().rearmSummaries(account.user, account.zone)
            reminders.rearmTasks(applicationContext, account)
        }
        withTimeoutOrNull(10_000L) { dependencies.provider().client?.auth?.awaitInitialization() }
        if (!dependencies.sessions().isCurrentUser(user)) return Result.success()
        val preferences = applicationContext.getSharedPreferences("activity_reminder_delivery", Context.MODE_PRIVATE)
        try {
            val today = AccountDate.today(zone)
            // Only an online copy can say a reminder was removed; an offline one leaves the alarms as they are.
            val habits = withTimeoutOrNull(20_000L) { dependencies.habits().load(user, today) } as? HabitLoadResult.Success
            if (habits != null && !habits.isOffline && dependencies.sessions().isCurrentUser(user)) {
                dependencies.habitReminders().reconcile(user, habits.habits, zone)
            }
            if (ReminderPreferences.read(applicationContext, user).letters) {
                val rows = checkNotNull(dependencies.provider().client).from("future_self_letters").select {
                    filter { eq("user_id", user); lte("target_deliver_date", today.toString()) }; limit(1000)
                }.decodeList<JsonObject>()
                val notified = preferences.getStringSet("$user/letters", emptySet()).orEmpty()
                val due = rows.filter { RecordRules.text(it, "ai_reply_text").isBlank() && RecordRules.text(it, "id") !in notified }
                if (due.isNotEmpty() && dependencies.sessions().isCurrentUser(user) && ReminderPreferences.read(applicationContext, user).letters &&
                    LocalReminderNotifications.show(applicationContext, "letters-$user", "A letter is ready to revisit",
                        "Open your future-self letters when you have a quiet moment.", "coach/letters")) {
                    preferences.edit(commit = true) { putStringSet("$user/letters", notified + due.map { RecordRules.text(it, "id") }) }
                }
            }
            return Result.success()
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { return Result.retry() }
    }
}
