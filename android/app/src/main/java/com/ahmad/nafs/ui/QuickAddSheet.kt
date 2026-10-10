package com.ahmad.nafs.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ahmad.nafs.ui.components.NafsCard
import com.ahmad.nafs.ui.components.NafsIconBadge
import com.ahmad.nafs.ui.theme.Nafs

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
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        QuickAddContent(onNavigate)
    }
}

@Composable
internal fun QuickAddContent(onNavigate: (String) -> Unit) {
    val c = Nafs.colors
    val look: Map<String, Pair<ImageVector, Color>> = mapOf(
        "tasks/new" to (Icons.Rounded.TaskAlt to c.tasks),
        "habits/new" to (Icons.Rounded.Autorenew to c.habits),
        "goals/new" to (Icons.Rounded.Flag to c.goals),
        "challenges/new" to (Icons.Rounded.Whatshot to c.challenges),
        "checkin" to (Icons.Rounded.WbTwilight to c.reflect),
        "coach" to (Icons.AutoMirrored.Rounded.Chat to c.coach),
    )
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Add to your day", style = MaterialTheme.typography.headlineSmall)
        Text("Capture it now — you can refine it later.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
        quickAddActions.chunked(3).forEach { row ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { action ->
                    val (icon, tint) = look.getValue(action.route)
                    NafsCard(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = { onNavigate(action.route) },
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        contentPadding = PaddingValues(vertical = 16.dp, horizontal = 8.dp),
                    ) {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            NafsIconBadge(icon, tint, size = 46.dp)
                            Text(action.label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2)
                        }
                    }
                }
            }
        }
    }
}
