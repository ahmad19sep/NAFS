package com.ahmad.nafs

import android.app.Application
import com.ahmad.nafs.core.reminders.TaskReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NafsApplication : Application() {
    @Inject lateinit var taskReminderScheduler: TaskReminderScheduler

    override fun onCreate() {
        super.onCreate()
        taskReminderScheduler.ensureNotificationChannel()
    }
}

