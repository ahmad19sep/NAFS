package com.ahmad.nafs.core.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ahmad.nafs.MainActivity
import com.ahmad.nafs.R

/**
 * Every NAFS notification channel. Ids never change (Android keeps the user's per-channel settings by id);
 * names and descriptions may. All are created at app start so they can be adjusted in Android settings
 * before the first notification arrives.
 */
enum class NafsChannel(val id: String, @param:StringRes val title: Int, @param:StringRes val description: Int, val importance: Int) {
    TASKS("task_due_reminders", R.string.task_reminder_channel_name, R.string.task_reminder_channel_description, NotificationManager.IMPORTANCE_HIGH),
    HABITS("nafs_habit_reminders", R.string.channel_habits_name, R.string.channel_habits_description, NotificationManager.IMPORTANCE_HIGH),
    PRAYER("nafs_prayer_times", R.string.channel_prayer_name, R.string.channel_prayer_description, NotificationManager.IMPORTANCE_HIGH),
    SUMMARY("nafs_summaries", R.string.channel_summary_name, R.string.channel_summary_description, NotificationManager.IMPORTANCE_DEFAULT),
    ACTIVITY("nafs_activity", R.string.channel_activity_name, R.string.channel_activity_description, NotificationManager.IMPORTANCE_DEFAULT),
}

/** NAFS emerald: tints the small icon and app name in the notification shade. */
const val NOTIFICATION_ACCENT = 0xFF0F7466.toInt()

object NafsNotifications {
    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            NafsChannel.entries.map { channel ->
                NotificationChannel(channel.id, context.getString(channel.title), channel.importance).apply {
                    description = context.getString(channel.description)
                }
            },
        )
    }

    /** False when Android 13+ permission is missing or the user turned NAFS notifications off. */
    fun allowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun builder(context: Context, channel: NafsChannel): NotificationCompat.Builder {
        ensureChannels(context)
        return NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(NOTIFICATION_ACCENT)
            .setAutoCancel(true)
            // Task titles, habits and prayers are personal: hide the text on a locked screen.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(
                if (channel.importance >= NotificationManager.IMPORTANCE_HIGH) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT,
            )
    }

    /** Opens NAFS on [destination] (one of the routes allowed in NafsApp). [requestKey] keeps intents apart. */
    fun openApp(context: Context, requestKey: String, destination: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_DESTINATION, destination)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, requestKey.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    // POST_NOTIFICATIONS is checked by allowed() first.
    @SuppressLint("MissingPermission")
    fun notify(context: Context, tag: String, builder: NotificationCompat.Builder): Boolean {
        if (!allowed(context)) return false
        NotificationManagerCompat.from(context).notify(tag, 0, builder.build())
        return true
    }

    fun cancel(context: Context, tag: String) = NotificationManagerCompat.from(context).cancel(tag, 0)
}

/** Simple informational notifications (summaries, letters, test). */
object LocalReminderNotifications {
    fun show(
        context: Context,
        key: String,
        title: String,
        text: String,
        destination: String,
        channel: NafsChannel = NafsChannel.ACTIVITY,
    ): Boolean = NafsNotifications.notify(
        context,
        key,
        NafsNotifications.builder(context, channel)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(NafsNotifications.openApp(context, key, destination)),
    )
}
