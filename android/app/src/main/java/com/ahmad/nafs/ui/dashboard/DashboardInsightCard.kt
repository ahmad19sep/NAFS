package com.ahmad.nafs.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.core.records.RecordRules
import com.ahmad.nafs.ui.AccountDayRefreshEffects
import com.ahmad.nafs.ui.progress.ProgressViewModel
import kotlinx.serialization.json.*

@Composable
fun DashboardInsightCard(profile: UserProfile, onExplore: () -> Unit, onCoach: () -> Unit, viewModel: ProgressViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(profile.userId) { viewModel.initialize(profile.userId) }
    AccountDayRefreshEffects(profile.timezone, viewModel::reload)
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Your recorded progress", style = MaterialTheme.typography.titleLarge)
        val response = state.response; val report = response?.get("report") as? JsonObject
        val today = (report?.get("days") as? JsonArray)?.mapNotNull { it as? JsonObject }?.find { RecordRules.text(it, "date") == RecordRules.text(response, "today") }
        if (today == null || RecordRules.text(today, "logged") != "true") Text("Today is still pending. Record your day to see its score.")
        else Text("Today: ${RecordRules.text(today, "score")}% · based on recorded activities")
        if (state.offline) Text("Saved offline figures")
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.error != null) Text("Progress could not refresh. Tap Refresh in reports to retry.")
        if (profile.deenEnabled && today != null) Text("Prayers recorded: ${RecordRules.text(today, "prayers_prayed")} / 5")
        val challenges = (report?.get("challenges") as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
        if (challenges.isNotEmpty()) Text("Challenges: ${challenges.count { RecordRules.text(it, "status") == "active" }} active · ${challenges.count { RecordRules.text(it, "status") == "completed" }} completed")
        (report?.get("focus") as? JsonArray)?.firstOrNull()?.let { it as? JsonObject }?.let { focus ->
            Text(RecordRules.text(focus, "title"), style = MaterialTheme.typography.titleMedium)
            Text(RecordRules.text(focus, "action"))
        }
        TextButton(onCoach) { Text("Talk with Coach about your next step") }
        TextButton(onExplore) { Text("Open reports, history, and recovery plans") }
    } }
}
