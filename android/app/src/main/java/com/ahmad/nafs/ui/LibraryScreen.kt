package com.ahmad.nafs.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Healing
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.core.records.RecordFeature
import com.ahmad.nafs.ui.components.NafsCard
import com.ahmad.nafs.ui.components.NafsEyebrow
import com.ahmad.nafs.ui.components.NafsIconBadge
import com.ahmad.nafs.ui.components.NafsSpace
import com.ahmad.nafs.ui.components.NafsTopBar
import com.ahmad.nafs.ui.theme.Nafs

private data class ExploreItem(val label: String, val route: String, val detail: String, val icon: ImageVector, val tint: Color)

/** Everything in NAFS that isn't a tab, grouped by what it helps with. */
@Composable
fun LibraryScreen(profile: UserProfile, onBack: (() -> Unit)? = null, onNavigate: (String) -> Unit) {
    val c = Nafs.colors
    fun record(feature: RecordFeature, detail: String, icon: ImageVector, tint: Color) =
        ExploreItem(feature.title, "records/${feature.name.lowercase()}", detail, icon, tint)

    val sections = buildList {
        add("Your day" to buildList {
            add(ExploreItem("Home", "dashboard", "Today at a glance", Icons.Rounded.Home, c.habits))
            add(ExploreItem("Daily check-in", "checkin", "Plan, then reflect", Icons.Rounded.WbTwilight, c.reflect))
            add(ExploreItem("Challenges", "challenges", "Streaks with stakes", Icons.Rounded.Whatshot, c.challenges))
            add(ExploreItem("Health", "health", "Sleep, water, meals", Icons.Rounded.Favorite, c.health))
            if (profile.deenEnabled) {
                add(ExploreItem("Deen", "deen", "Prayers and jamaat", Icons.Rounded.NightsStay, c.deen))
                add(ExploreItem("Prayer times", "prayer-times", "Today's times", Icons.Rounded.AccessTime, c.deen))
            }
        })
        add("Coach" to listOf(
            ExploreItem("Coach", "coach", "Talk it through", Icons.AutoMirrored.Rounded.Chat, c.coach),
            ExploreItem("Recovery plan", "coach/plans", "A way back from a slip", Icons.Rounded.Healing, c.coach),
            ExploreItem("Future-self letters", "coach/letters", "Write to who you'll be", Icons.Rounded.MailOutline, c.coach),
            ExploreItem("Coach archive", "coach/reports", "Saved verdicts, reviews", Icons.Rounded.Inventory2, c.coach),
            ExploreItem("Coach advice", "advice", "Suggestions to act on", Icons.Rounded.AutoAwesome, c.coach),
        ))
        add("Progress" to listOf(
            ExploreItem("30-day history", "history", "Every area, day by day", Icons.Rounded.Insights, c.tasks),
            ExploreItem("Weekly & monthly reports", "progress", "Printable reports", Icons.Rounded.CalendarMonth, c.tasks),
            ExploreItem("Badges and levels", "achievements", "What you've earned", Icons.Rounded.MilitaryTech, c.gold),
        ))
        add("Journals" to buildList {
            add(record(RecordFeature.DREAM, "Your long game", Icons.Rounded.Stars, c.goals))
            add(record(RecordFeature.LOG, "Hours, mood, reflection", Icons.AutoMirrored.Rounded.EventNote, c.reflect))
            if (profile.deenEnabled) add(record(RecordFeature.QURAN, "Pages and ayahs", Icons.AutoMirrored.Rounded.MenuBook, c.deen))
            add(record(RecordFeature.SCREEN, "Minutes by app", Icons.Rounded.PhoneAndroid, c.challenges))
            add(record(RecordFeature.NOTE, "Things to fix or try", Icons.Rounded.BugReport, c.health))
        })
    }

    // A plain scrolling column (not lazy) so every tile exists for accessibility tools and tests.
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        NafsTopBar(title = "Explore", onBack = onBack, subtitle = "Everything in NAFS, in one place")
        Column(
            Modifier.padding(horizontal = NafsSpace.screen),
            verticalArrangement = Arrangement.spacedBy(NafsSpace.section),
        ) {
            Spacer(Modifier.height(4.dp))
            sections.forEach { (title, items) ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    NafsEyebrow(title, Modifier.padding(start = 4.dp))
                    items.chunked(2).forEach { pair ->
                        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            pair.forEach { item -> ExploreTile(item, Modifier.weight(1f).fillMaxHeight()) { onNavigate(item.route) } }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ExploreTile(item: ExploreItem, modifier: Modifier, onClick: () -> Unit) {
    NafsCard(modifier = modifier, onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NafsIconBadge(item.icon, item.tint, size = 40.dp, iconSize = 20.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.label, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(item.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
