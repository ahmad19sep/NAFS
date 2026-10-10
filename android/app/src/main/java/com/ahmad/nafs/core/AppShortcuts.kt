package com.ahmad.nafs.core

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.ahmad.nafs.MainActivity
import com.ahmad.nafs.R

/**
 * Long-press shortcuts on the launcher icon. Each opens a screen through MainActivity's destination extra, which
 * NafsApp only honours for allowed routes once the user is signed in.
 */
object AppShortcuts {
    private data class Shortcut(val id: String, val label: String, val longLabel: String, @param:DrawableRes val icon: Int, val destination: String)

    private val shortcuts = listOf(
        Shortcut("new-task", "New task", "Add a task", R.drawable.ic_shortcut_task, "tasks/new"),
        Shortcut("habits", "Habits", "Log today's habits", R.drawable.ic_shortcut_habit, "habits"),
        Shortcut("check-in", "Check-in", "Daily check-in", R.drawable.ic_shortcut_checkin, "checkin"),
        Shortcut("coach", "Coach", "Talk to your coach", R.drawable.ic_shortcut_coach, "coach"),
    )

    fun publish(context: Context) {
        val infos = shortcuts.map { shortcut ->
            ShortcutInfoCompat.Builder(context, shortcut.id)
                .setShortLabel(shortcut.label)
                .setLongLabel(shortcut.longLabel)
                .setIcon(IconCompat.createWithResource(context, shortcut.icon))
                .setIntent(
                    Intent(context, MainActivity::class.java)
                        .setAction(Intent.ACTION_VIEW)
                        .putExtra(MainActivity.EXTRA_DESTINATION, shortcut.destination)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                )
                .build()
        }
        // Launchers cap the count; a failure here must never affect app start.
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, infos.take(ShortcutManagerCompat.getMaxShortcutCountPerActivity(context).coerceAtLeast(1))) }
    }
}
