package com.ahmad.nafs.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.core.records.RecordFeature

@Composable
fun LibraryScreen(profile: UserProfile, onNavigate: (String) -> Unit) {
    val links = listOf("Home" to "dashboard", "Daily check-in" to "checkin", "Challenges" to "challenges", "Health" to "health",
        "Coach" to "coach", "Recovery plan" to "coach/plans", "Future-self letters" to "coach/letters", "Coach archive" to "coach/reports",
        "Coach advice" to "advice", "Badges and levels" to "achievements", "30-day history" to "history", "Weekly & monthly reports" to "progress") +
        RecordFeature.entries.filter { it != RecordFeature.QURAN || profile.deenEnabled }.map { it.title to "records/${it.name.lowercase()}" } +
        if (profile.deenEnabled) listOf("Deen" to "deen", "Prayer times" to "prayer-times") else emptyList()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Explore NAFS", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() }) }
        items(links) { (label, route) -> OutlinedButton(onClick = { onNavigate(route) }, modifier = Modifier.fillMaxWidth()) { Text(label) } }
        item { Spacer(Modifier.height(80.dp)) }
    }
}
