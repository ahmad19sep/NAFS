package com.ahmad.nafs.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class QuickAddAction(val label: String, val route: String)

val quickAddActions = listOf(
    QuickAddAction("New task", "tasks/new"),
    QuickAddAction("New habit", "habits/new"),
    QuickAddAction("New goal", "goals/new"),
    QuickAddAction("New challenge", "challenges/new"),
    QuickAddAction("Daily check-in", "checkin"),
    QuickAddAction("Ask Coach", "coach"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(onDismiss: () -> Unit, onNavigate: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            quickAddActions.forEach { action ->
                TextButton(onClick = { onNavigate(action.route) }, modifier = Modifier.fillMaxWidth()) {
                    ListItem(headlineContent = { Text(action.label) })
                }
            }
        }
    }
}
