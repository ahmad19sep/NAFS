package com.ahmad.nafs.core.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.work.*
import com.ahmad.nafs.MainActivity
import com.ahmad.nafs.R
import com.ahmad.nafs.core.habits.*
import com.ahmad.nafs.core.network.SupabaseClientProvider
import com.ahmad.nafs.core.network.SupabaseApiSessionTokens
import com.ahmad.nafs.core.records.RecordRules
import com.ahmad.nafs.core.time.AccountDate
import com.ahmad.nafs.domain.habits.HabitResult
import com.ahmad.nafs.domain.habits.HabitStreak
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.postgrest.from
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject

@EntryPoint @InstallIn(SingletonComponent::class)
interface ActivityReminderDependencies {
    fun habits(): HabitRepository
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
object LocalReminderNotifications {
    @SuppressLint("MissingPermission")
    fun show(context: Context, key: String, title: String, text: String, destination: String): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        val channel = "nafs_activity"
        if (Build.VERSION.SDK_INT >= 26) context.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(channel, "Habit and letter reminders", NotificationManager.IMPORTANCE_DEFAULT))
        val intent = Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_DESTINATION, destination).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, key.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(key, 0, NotificationCompat.Builder(context, channel).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText(text).setContentIntent(pending).setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build())
        return true
    }
}
class ActivityReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val user = inputData.getString("user") ?: return Result.failure()
        val zone = inputData.getString("zone") ?: return Result.failure()
        val dependencies = EntryPointAccessors.fromApplication(applicationContext, ActivityReminderDependencies::class.java)
        if (!dependencies.sessions().isCurrentUser(user)) return Result.success()
        val preferences = applicationContext.getSharedPreferences("activity_reminder_delivery", Context.MODE_PRIVATE)
        try {
            val today = AccountDate.today(zone)
            if (ReminderPreferences.read(applicationContext, user).habits && preferences.getString("$user/habit-day", null) != today.toString()) {
                val result = dependencies.habits().load(user, today)
                if (result is HabitLoadResult.Success && !result.isOffline) {
                    val logs = result.logs.filter { it.date == today }.associateBy { it.habitId }
                    val remaining = result.habits.any { habit -> !habit.isPaused && HabitStreak.isScheduled(habit.schedule(), today) && !HabitStreak.isComplete(habit.schedule(), logs[habit.id]?.let { HabitResult(it.date, it.completed, it.value, it.durationMinutes) }) }
                    if (remaining && dependencies.sessions().isCurrentUser(user) && ReminderPreferences.read(applicationContext, user).habits &&
                        LocalReminderNotifications.show(applicationContext, "habits-$user", "Your daily rhythm", "You have a scheduled habit left to log today.", "habits")) {
                        preferences.edit(commit = true) { putString("$user/habit-day", today.toString()) }
                    }
                }
            }
            if (ReminderPreferences.read(applicationContext, user).letters) {
                val rows = checkNotNull(dependencies.provider().client).from("future_self_letters").select {
                    filter { eq("user_id", user); lte("target_deliver_date", today.toString()) }; limit(1000)
                }.decodeList<JsonObject>()
                val notified = preferences.getStringSet("$user/letters", emptySet()).orEmpty()
                val due = rows.filter { RecordRules.text(it, "ai_reply_text").isBlank() && RecordRules.text(it, "id") !in notified }
                if (due.isNotEmpty() && dependencies.sessions().isCurrentUser(user) && ReminderPreferences.read(applicationContext, user).letters &&
                    LocalReminderNotifications.show(applicationContext, "letters-$user", "A letter is ready to revisit", "Open your future-self letters when you have a quiet moment.", "coach/letters")) {
                    preferences.edit(commit = true) { putStringSet("$user/letters", notified + due.map { RecordRules.text(it, "id") }) }
                }
            }
            return Result.success()
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { return Result.retry() }
    }
}
