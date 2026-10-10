package com.ahmad.nafs.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.core.reminders.*
import com.ahmad.nafs.core.tasks.TaskRepository
import com.ahmad.nafs.core.tasks.TaskLoadResult
import com.ahmad.nafs.core.time.AccountDate
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class ReminderOptionsViewModel @Inject constructor(private val preferences: ReminderPreferences, private val tasks: TaskRepository,
    private val taskScheduler: TaskReminderScheduler, private val activityScheduler: ActivityReminderScheduler) : ViewModel() {
    fun read(user: String) = preferences.read(user)
    fun save(profile: UserProfile, options: ReminderOptions) {
        preferences.save(profile.userId, options)
        activityScheduler.schedule(profile.userId, profile.timezone)
        viewModelScope.launch {
            if (!options.tasks) taskScheduler.cancelAll(profile.userId)
            else { val result = tasks.load(profile.userId, AccountDate.today(profile.timezone)); if (result is TaskLoadResult.Success) taskScheduler.reconcile(profile.userId, result.tasks, profile.timezone) }
        }
    }
}
@Composable
fun ReminderOptionsCard(profile: UserProfile, viewModel: ReminderOptionsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var options by remember(profile.userId) { mutableStateOf(viewModel.read(profile.userId)) }
    var message by remember { mutableStateOf<String?>(null) }
    fun update(value: ReminderOptions) {
        runCatching { viewModel.save(profile, value) }
            .onSuccess { options = value; message = "Reminder settings saved on this device." }
            .onFailure { message = "Reminder settings could not be saved. Retry." }
    }
    Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Reminders on this Android device", style = MaterialTheme.typography.titleMedium)
        Row { Switch(options.tasks, { update(options.copy(tasks = it)) }, Modifier.semantics { contentDescription = "Task due times" }); Text("Task due times") }
        Row { Switch(options.habits, { update(options.copy(habits = it)) }, Modifier.semantics { contentDescription = "Unfinished scheduled habits" }); Text("Unfinished scheduled habits") }
        Row { Switch(options.letters, { update(options.copy(letters = it)) }, Modifier.semantics { contentDescription = "Letters ready to revisit" }); Text("Letters ready to revisit") }
        Text("Habit and letter reminders are checked several times daily when connected. Android battery settings can delay delivery.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { message = if (LocalReminderNotifications.show(context, "nafs-test", "NAFS test notification", "Reminders are available on this device.", "profile")) "Test notification sent." else "Allow notifications in Android settings, then retry." }) { Text("Send test notification") }
        message?.let { Text(it) }
    } }
}
