package com.ahmad.nafs.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ahmad.nafs.core.network.NafsApiClient
import com.ahmad.nafs.core.profile.UserProfile
import com.ahmad.nafs.core.records.PrivateRecordStore
import com.ahmad.nafs.core.records.RecordRules
import com.ahmad.nafs.ui.components.NafsBanner
import com.ahmad.nafs.ui.components.NafsBannerKind
import com.ahmad.nafs.ui.components.NafsCard
import com.ahmad.nafs.ui.components.NafsEmptyState
import com.ahmad.nafs.ui.components.NafsEyebrow
import com.ahmad.nafs.ui.components.NafsHeroSurface
import com.ahmad.nafs.ui.components.NafsPill
import com.ahmad.nafs.ui.components.NafsProgressBar
import com.ahmad.nafs.ui.components.NafsSectionTitle
import com.ahmad.nafs.ui.components.NafsSpace
import com.ahmad.nafs.ui.components.NafsTopBar
import com.ahmad.nafs.ui.theme.Nafs
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

data class AchievementsState(val data: JsonObject? = null, val busy: Boolean = false, val error: String? = null, val offline: Boolean = false)
@HiltViewModel
class AchievementsViewModel @Inject constructor(private val api: NafsApiClient, private val store: PrivateRecordStore) : ViewModel() {
    private val _state = MutableStateFlow(AchievementsState()); val state = _state.asStateFlow()
    private var account = ""
    fun initialize(user: String) { if (account != user) { account = user; refresh() } }
    fun refresh() {
        if (_state.value.busy || account.isBlank()) return
        val user = account; _state.value = _state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val data = api.post(user, "/api/recognition", buildJsonObject {})
                require(data["badges"] is JsonArray && data["level"] is JsonObject)
                if (account != user) return@launch
                _state.value = AchievementsState(data = data)
                try { store.write(user, "recognition", data) }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { _state.value = _state.value.copy(error = "Current achievements loaded; the offline copy could not be saved.") }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) {
                val saved = runCatching { store.read(user, "recognition") }.getOrNull()
                _state.value = AchievementsState(data = saved, offline = saved != null, error = "Achievements could not be refreshed. Reconnect and retry.")
            }
        }
    }
}

@Composable
fun AchievementsRoute(profile: UserProfile, onBack: () -> Unit, viewModel: AchievementsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(profile.userId) { viewModel.initialize(profile.userId) }
    AchievementsScreen(state, onBack = onBack, onRefresh = viewModel::refresh)
}

/** Levels count recorded days, never score, so the hero only ever moves forward; badges sit below by area. */
@Composable
internal fun AchievementsScreen(
    state: AchievementsState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    val data = state.data
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        NafsTopBar(
            title = "Badges and levels",
            onBack = onBack,
            subtitle = "Earned by showing up, and never taken away",
            actions = {
                IconButton(onClick = onRefresh, enabled = !state.busy) {
                    if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = NafsSpace.screen, end = NafsSpace.screen, top = 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(NafsSpace.item),
        ) {
            if (data == null) {
                item(key = "status") {
                    val error = state.error
                    if (error != null && !state.busy) {
                        NafsEmptyState(
                            icon = Icons.Rounded.CloudOff,
                            title = "Achievements didn't load",
                            message = error,
                            tint = MaterialTheme.colorScheme.error,
                            actionLabel = "Try again",
                            onAction = onRefresh,
                        )
                    } else {
                        LoadingBlocks()
                    }
                }
            } else {
                if (state.offline) {
                    item(key = "offline") {
                        NafsBanner(
                            state.error ?: "Showing the achievements saved on this phone.",
                            kind = NafsBannerKind.OFFLINE,
                            title = "Saved offline achievements",
                            actionLabel = "Retry",
                            onAction = onRefresh.takeIf { !state.busy },
                        )
                    }
                } else {
                    state.error?.let { error -> item(key = "error") { NafsBanner(error, kind = NafsBannerKind.WARNING) } }
                }
                val badges = (data["badges"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
                val earned = badges.count { it.isEarned() }
                (data["level"] as? JsonObject)?.let { level ->
                    item(key = "level") { LevelHero(level, earned, badges.size) }
                }
                if ((data["unavailable"] as? JsonArray)?.isNotEmpty() == true) item(key = "unavailable") {
                    NafsBanner("Some recent activity was unavailable; the consistency badge was not reassessed.", kind = NafsBannerKind.WARNING)
                }
                item(key = "badges-title") {
                    NafsSectionTitle("Badges", Modifier.padding(top = 16.dp), supporting = "$earned of ${badges.size} earned")
                }
                if (badges.isEmpty()) {
                    item(key = "badges-empty") {
                        NafsEmptyState(
                            icon = Icons.Rounded.MilitaryTech,
                            title = "No badges to show",
                            message = "Refresh to load the badges you can earn.",
                            tint = Nafs.colors.gold,
                        )
                    }
                }
                badges.groupBy { RecordRules.text(it, "feature") }.forEach { (feature, group) ->
                    item(key = "badges-$feature") { BadgeGroup(feature, group) }
                }
            }
        }
    }
}

@Composable
private fun LoadingBlocks() {
    val block = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    Column(
        Modifier.fillMaxWidth().semantics { contentDescription = "Loading achievements" },
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(220.dp).clip(MaterialTheme.shapes.extraLarge).background(block))
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(2) { Box(Modifier.weight(1f).height(150.dp).clip(MaterialTheme.shapes.large).background(block)) }
            }
        }
    }
}

@Composable
private fun LevelHero(level: JsonObject, earned: Int, total: Int) {
    val c = Nafs.colors
    val current = level["current"] as? JsonObject
    val next = level["next"] as? JsonObject
    val days = RecordRules.text(level, "daysLogged")
    val progress = (RecordRules.text(level, "pctToNext").toFloatOrNull() ?: 0f) / 100
    NafsHeroSurface {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                LevelMedal(RecordRules.text(current, "number"))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    NafsEyebrow(if (current == null) "No level yet" else "Level ${RecordRules.text(current, "number")}", color = c.heroGold)
                    Text(
                        if (current == null) "Your first recorded day starts your level." else RecordRules.text(current, "name"),
                        style = if (current == null) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                        color = c.onHero,
                    )
                    Text(
                        "$days distinct days recorded across habits, prayer, and health.",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.onHeroMuted,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        next?.let { "${RecordRules.text(level, "daysToNext")} more recorded ${if (RecordRules.text(level, "daysToNext") == "1") "day" else "days"} to ${RecordRules.text(it, "name")}" }
                            ?: "Every level reached. Keep showing up.",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge,
                        color = c.onHero,
                    )
                    Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = c.heroGold)
                }
                NafsProgressBar(progress, color = c.heroGold, trackColor = Color.White.copy(alpha = 0.12f), height = 8.dp)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.10f)))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HeroFigure(days.ifBlank { "0" }, "Days recorded", Modifier.weight(1f))
                HeroFigure("$earned/$total", "Badges earned", Modifier.weight(1f))
                HeroFigure(next?.let { RecordRules.text(it, "at").ifBlank { "—" } } ?: "—", "Days for next level", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LevelMedal(number: String) {
    val c = Nafs.colors
    Box(
        Modifier
            .size(92.dp)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(c.heroGold.copy(alpha = 0.34f), c.heroGold.copy(alpha = 0.06f))))
            .border(2.dp, c.heroGold.copy(alpha = 0.75f), CircleShape)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(number.ifBlank { "—" }, style = MaterialTheme.typography.headlineLarge, color = c.heroGold)
            Text("LEVEL", style = MaterialTheme.typography.labelSmall, color = c.onHeroMuted)
        }
    }
}

@Composable
private fun HeroFigure(value: String, label: String, modifier: Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = Nafs.colors.onHero, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Nafs.colors.onHeroMuted, maxLines = 2)
    }
}

private data class FeatureStyle(val label: String, val tint: Color)

@Composable
private fun featureStyle(feature: String): FeatureStyle {
    val c = Nafs.colors
    return when (feature) {
        "habits" -> FeatureStyle("Habits", c.habits)
        "deen" -> FeatureStyle("Deen", c.deen)
        "challenges" -> FeatureStyle("Challenges", c.challenges)
        "tasks" -> FeatureStyle("Tasks", c.tasks)
        "goals" -> FeatureStyle("Goals", c.goals)
        "health" -> FeatureStyle("Health", c.health)
        "overall" -> FeatureStyle("Overall", c.gold)
        else -> FeatureStyle(ProgressText.label(feature).ifBlank { "Other" }, MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BadgeGroup(feature: String, badges: List<JsonObject>) {
    val style = featureStyle(feature)
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(style.tint))
            NafsEyebrow(style.label, Modifier.weight(1f))
            Text(
                "${badges.count { it.isEarned() }}/${badges.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        badges.chunked(2).forEach { pair ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { badge -> BadgeTile(badge, Modifier.weight(1f).fillMaxHeight()) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BadgeTile(badge: JsonObject, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val earned = badge.isEarned()
    val tier = RecordRules.text(badge, "tier")
    val tint = tierColor(tier)
    val awarded = awardedLabel(RecordRules.text(badge, "awardedAt"))
    NafsCard(
        modifier.semantics(mergeDescendants = true) {},
        color = if (earned) scheme.surfaceContainer else scheme.surfaceContainerLow,
        borderColor = if (earned) tint.copy(alpha = 0.45f) else Nafs.colors.cardBorder,
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            BadgeMedallion(RecordRules.text(badge, "emoji"), tint, earned)
            Spacer(Modifier.weight(1f))
            if (earned) Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Nafs.colors.success, modifier = Modifier.size(18.dp))
            else Icon(Icons.Rounded.Lock, contentDescription = null, tint = scheme.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            RecordRules.text(badge, "name"),
            style = MaterialTheme.typography.titleSmall,
            color = if (earned) scheme.onSurface else scheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        Text(RecordRules.text(badge, "description"), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Spacer(Modifier.weight(1f))
        NafsPill(ProgressText.label(tier), color = if (earned) tint else scheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Text(
            if (earned) awarded?.let { "Earned $it" } ?: "Earned" else "Keep going",
            style = MaterialTheme.typography.labelMedium,
            color = if (earned) Nafs.colors.success else scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BadgeMedallion(emoji: String, tint: Color, earned: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val fill = if (earned) Brush.linearGradient(listOf(tint.copy(alpha = 0.34f), tint.copy(alpha = 0.08f)))
    else SolidColor(scheme.onSurface.copy(alpha = 0.06f))
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(fill)
            .border(1.5.dp, if (earned) tint.copy(alpha = 0.85f) else scheme.outlineVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji.ifBlank { "•" }, style = MaterialTheme.typography.titleLarge, modifier = Modifier.alpha(if (earned) 1f else 0.35f))
    }
}

/** Metal tones for badge tiers, tuned for contrast on both the night and the paper theme. */
@Composable
private fun tierColor(tier: String): Color {
    val dark = Nafs.colors.isDark
    return when (tier) {
        "bronze" -> if (dark) Color(0xFFE3A57A) else Color(0xFF9C5A2C)
        "silver" -> if (dark) Color(0xFFC5D0D8) else Color(0xFF5F6F7E)
        "gold" -> Nafs.colors.gold
        "platinum" -> if (dark) Color(0xFFA8E6EF) else Color(0xFF2E7F8F)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

private val awardedFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

private fun awardedLabel(value: String): String? =
    if (value.length < 10) null else runCatching { LocalDate.parse(value.take(10)).format(awardedFormat) }.getOrNull()

private fun JsonObject.isEarned() = RecordRules.text(this, "earned") == "true"
