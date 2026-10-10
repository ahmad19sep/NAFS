package com.ahmad.nafs.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.core.records.RecordRules
import com.ahmad.nafs.ui.AccountDayRefreshEffects
import com.ahmad.nafs.ui.components.NafsCard
import com.ahmad.nafs.ui.components.NafsDivider
import com.ahmad.nafs.ui.components.NafsEyebrow
import com.ahmad.nafs.ui.components.NafsIconBadge
import com.ahmad.nafs.ui.components.NafsPill
import com.ahmad.nafs.ui.components.NafsSecondaryButton
import com.ahmad.nafs.ui.progress.ProgressViewModel
import com.ahmad.nafs.ui.theme.Nafs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

@Composable
fun DashboardInsightCard(profile: UserProfile, onExplore: () -> Unit, onCoach: () -> Unit, viewModel: ProgressViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(profile.userId) { viewModel.initialize(profile.userId) }
    AccountDayRefreshEffects(profile.timezone, viewModel::reload)

    val response = state.response
    val report = response?.get("report") as? JsonObject
    val today = (report?.get("days") as? JsonArray)?.mapNotNull { it as? JsonObject }
        ?.find { RecordRules.text(it, "date") == RecordRules.text(response, "today") }
    val logged = today != null && RecordRules.text(today, "logged") == "true"
    val challenges = (report?.get("challenges") as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
    val focus = (report?.get("focus") as? JsonArray)?.firstOrNull() as? JsonObject

    NafsCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NafsIconBadge(Icons.Rounded.Insights, Nafs.colors.tasks, size = 40.dp, iconSize = 20.dp)
                Column(Modifier.weight(1f)) {
                    Text("Recorded progress", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (logged) "Today: ${RecordRules.text(today, "score")}% from what you recorded"
                        else "Today is still pending — record your day to see its score.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())

            val pills = buildList {
                if (profile.deenEnabled && today != null) add("Prayers ${RecordRules.text(today, "prayers_prayed")}/5")
                if (challenges.isNotEmpty()) {
                    add("${challenges.count { RecordRules.text(it, "status") == "active" }} active challenges")
                    add("${challenges.count { RecordRules.text(it, "status") == "completed" }} completed")
                }
                if (state.offline) add("Saved offline figures")
            }
            if (pills.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pills.take(3).forEach { NafsPill(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            focus?.let {
                NafsDivider(inset = 0.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NafsIconBadge(Icons.Rounded.Lightbulb, Nafs.colors.gold, size = 32.dp, iconSize = 16.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        NafsEyebrow("Focus", color = Nafs.colors.gold)
                        Text(RecordRules.text(it, "title"), style = MaterialTheme.typography.titleSmall)
                        Text(RecordRules.text(it, "action"), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (state.error != null) {
                Text("Progress couldn't refresh. Open reports to retry.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NafsSecondaryButton("Ask Coach", onCoach, Modifier.weight(1f))
                NafsSecondaryButton("Reports", onExplore, Modifier.weight(1f))
            }
        }
    }
}
