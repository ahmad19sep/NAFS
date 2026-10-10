package com.ahmad.nafs.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ahmad.nafs.ui.theme.Gold300
import com.ahmad.nafs.ui.theme.Gold500
import com.ahmad.nafs.ui.theme.Nafs
import com.ahmad.nafs.ui.theme.Night900

internal data class TopDestination(
    val label: String,
    val route: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

/** Daily-use areas get a tab; Profile sits behind the avatar on Home and in Explore. */
internal val topDestinations = listOf(
    TopDestination("Home", "dashboard", Icons.Outlined.Home, Icons.Rounded.Home),
    TopDestination("Tasks", "tasks", Icons.Outlined.TaskAlt, Icons.Rounded.TaskAlt),
    TopDestination("Habits", "habits", Icons.Rounded.Autorenew, Icons.Rounded.Autorenew),
    TopDestination("Goals", "goals", Icons.Outlined.Flag, Icons.Rounded.Flag),
)

/** Routes that may be opened as a tab (deep links, notifications), including Profile. */
internal val tabRoutes = topDestinations.map { it.route }.toSet() + "profile"

@Composable
internal fun NafsBottomBar(
    currentRoute: String?,
    onSelect: (String) -> Unit,
    onQuickAdd: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(color = scheme.surfaceContainerLow, tonalElevation = 0.dp) {
        Column {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Nafs.colors.cardBorder))
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().height(68.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                topDestinations.take(2).forEach { BarItem(it, currentRoute == it.route, onSelect) }
                AddButton(onQuickAdd)
                topDestinations.drop(2).forEach { BarItem(it, currentRoute == it.route, onSelect) }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BarItem(item: TopDestination, selected: Boolean, onSelect: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val tint by animateColorAsState(if (selected) scheme.primary else scheme.onSurfaceVariant, label = "tab")
    val pill by animateColorAsState(if (selected) scheme.primary.copy(alpha = 0.14f) else Color.Transparent, label = "pill")
    Surface(
        onClick = { onSelect(item.route) },
        modifier = Modifier.weight(1f).semantics {
            role = Role.Tab
            this.selected = selected
            contentDescription = item.label
        },
        color = Color.Transparent,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(Modifier.width(54.dp).height(30.dp).clip(CircleShape).background(pill), contentAlignment = Alignment.Center) {
                Icon(if (selected) item.selectedIcon else item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            }
            Text(
                item.label,
                color = tint,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.AddButton(onClick: () -> Unit) {
    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(20.dp),
            color = Color.Transparent,
            shadowElevation = 6.dp,
            modifier = Modifier.semantics { contentDescription = "Quick Add" },
        ) {
            Box(
                Modifier.size(56.dp).background(Brush.verticalGradient(listOf(Gold300, Gold500))),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Add, contentDescription = null, tint = Night900, modifier = Modifier.size(28.dp)) }
        }
    }
}
