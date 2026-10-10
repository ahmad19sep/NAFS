package com.ahmad.nafs.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ahmad.nafs.ui.theme.Nafs

/** Shared spacing so every screen breathes the same way. */
object NafsSpace {
    val screen = 20.dp
    val section = 28.dp
    val item = 12.dp
    val card = 18.dp
}

// ─── Surfaces ────────────────────────────────────────────────────────────────

/** The standard NAFS card: tonal surface, hairline border, no shadow. */
@Composable
fun NafsCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    borderColor: Color = Nafs.colors.cardBorder,
    shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.large,
    contentPadding: PaddingValues = PaddingValues(NafsSpace.card),
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val border = BorderStroke(1.dp, borderColor)
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), enabled = enabled, shape = shape, color = color, border = border) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    } else {
        Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = color, border = border) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}

/** Deep gradient surface with a soft gold glow — the one hero moment per screen. */
@Composable
fun NafsHeroSurface(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(22.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = Nafs.colors
    Box(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(Brush.verticalGradient(listOf(colors.heroTop, colors.heroBottom)))
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(listOf(colors.heroGlow, Color.Transparent), center = Offset(size.width * 0.92f, 0f), radius = size.width * 0.7f),
                    radius = size.width * 0.7f,
                    center = Offset(size.width * 0.92f, 0f),
                )
            }
            .border(1.dp, Color.White.copy(alpha = 0.08f), MaterialTheme.shapes.extraLarge)
            .padding(contentPadding),
        content = content,
    )
}

@Composable
fun NafsHeroCard(
    eyebrow: String,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    content: @Composable (() -> Unit)? = null,
) {
    NafsHeroSurface(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NafsEyebrow(eyebrow, color = Nafs.colors.heroGold)
            Text(title, color = Nafs.colors.onHero, style = MaterialTheme.typography.headlineSmall)
            Text(body, color = Nafs.colors.onHeroMuted, style = MaterialTheme.typography.bodyMedium)
            content?.invoke()
        }
    }
}

// ─── Headers ─────────────────────────────────────────────────────────────────

/** Small uppercase label that sits above a title. */
@Composable
fun NafsEyebrow(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text.uppercase(), modifier, color = color, style = MaterialTheme.typography.labelSmall)
}

/**
 * Large page title that scrolls with the content, with optional actions on the right.
 * Used as the first item of a tab screen.
 */
@Composable
fun NafsPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            eyebrow?.let { NafsEyebrow(it, color = MaterialTheme.colorScheme.primary) }
            Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Compact pinned bar for pushed screens: back arrow, title, actions. */
@Composable
fun NafsTopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
        } else Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() })
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

@Composable
fun NafsSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            supporting?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 10.dp)) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

// ─── Icons, avatars, chips ───────────────────────────────────────────────────

/** Icon in a softly tinted rounded square. */
@Composable
fun NafsIconBadge(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(tint.copy(alpha = if (Nafs.colors.isDark) 0.16f else 0.12f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize)) }
}

/** Emoji in a softly tinted rounded square, for user-chosen habit and goal emoji. */
@Composable
fun NafsEmojiBadge(emoji: String, tint: Color, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(tint.copy(alpha = if (Nafs.colors.isDark) 0.16f else 0.12f)),
        contentAlignment = Alignment.Center,
    ) { Text(emoji.ifBlank { "•" }, style = MaterialTheme.typography.titleLarge) }
}

/** Initials avatar on a gradient, or the photo when one is set. */
@Composable
fun NafsAvatar(name: String, photoUrl: String?, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val initials = name.trim().split(Regex("\\s+")).mapNotNull { it.firstOrNull()?.uppercaseChar() }.take(2).joinToString("").ifEmpty { "N" }
    Box(
        modifier.size(size).clip(CircleShape)
            .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, Nafs.colors.gold))),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, color = MaterialTheme.colorScheme.onPrimary,
            style = if (size >= 64.dp) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleSmall)
        if (!photoUrl.isNullOrBlank()) PublicPhoto(photoUrl, "Profile photo", Modifier.fillMaxSize().clip(CircleShape), crop = true)
    }
}

@Composable
fun NafsStatusPill(
    text: String,
    modifier: Modifier = Modifier,
    accent: Boolean = true,
) {
    NafsPill(
        text = text,
        modifier = modifier,
        color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Small rounded label in a tint of its own colour. */
@Composable
fun NafsPill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    icon: ImageVector? = null,
) {
    Row(
        modifier.clip(CircleShape).background(color.copy(alpha = if (Nafs.colors.isDark) 0.16f else 0.11f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        icon?.let { Icon(it, contentDescription = null, tint = color, modifier = Modifier.size(13.dp)) }
        Text(text, color = color, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

/** Selectable chip. Selected uses the primary colour; idle is a quiet outline. */
@Composable
fun NafsChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.semantics { contentDescription = if (selected) "$text, selected" else text },
        shape = CircleShape,
        color = if (selected) scheme.primary else scheme.surfaceContainer,
        contentColor = if (selected) scheme.onPrimary else scheme.onSurface,
        border = if (selected) null else BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            leading?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

/** Pill-style segmented control for 2–4 mutually exclusive views. */
@Composable
fun NafsSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().clip(CircleShape).background(scheme.surfaceContainerHigh).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Surface(
                onClick = { onSelect(index) },
                modifier = Modifier.weight(1f).semantics { contentDescription = if (selected) "$label, selected" else label },
                shape = CircleShape,
                color = if (selected) scheme.surfaceContainerLowest else Color.Transparent,
                contentColor = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
                shadowElevation = if (selected) 1.dp else 0.dp,
            ) {
                Text(label, Modifier.padding(vertical = 10.dp), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

// ─── Progress ────────────────────────────────────────────────────────────────

/** Rounded progress bar that animates to its value. */
@Composable
fun NafsProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    height: Dp = 8.dp,
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(700), label = "progress")
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(trackColor)) {
        if (animated > 0f) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(animated).clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.75f), color))))
        }
    }
}

/** Circular progress ring with content in the middle. */
@Composable
fun NafsProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 112.dp,
    stroke: Dp = 10.dp,
    colors: List<Color> = listOf(Nafs.colors.gold, MaterialTheme.colorScheme.primary),
    trackColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    content: @Composable BoxScope.() -> Unit = {},
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(900), label = "ring")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val width = stroke.toPx()
            val inset = width / 2
            val arcSize = Size(this.size.width - width, this.size.height - width)
            rotate(-90f) {
                drawArc(trackColor, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(width, cap = StrokeCap.Round))
                if (animated > 0f) {
                    drawArc(
                        Brush.sweepGradient(colors), 0f, 360f * animated, false, Offset(inset, inset), arcSize,
                        style = Stroke(width, cap = StrokeCap.Round),
                    )
                }
            }
        }
        content()
    }
}

/** A labelled number with a slim progress bar underneath. */
@Composable
fun NafsStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = valueColor, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelMedium, color = labelColor, maxLines = 1)
        progress?.let { NafsProgressBar(it, color = color, height = 5.dp, trackColor = labelColor.copy(alpha = 0.18f)) }
    }
}

// ─── Rows ────────────────────────────────────────────────────────────────────

@Composable
fun NafsQuickAction(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    NafsCard(modifier = modifier, onClick = onClick, contentPadding = PaddingValues(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            NafsIconBadge(icon, tint)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Settings/list row: icon, title, optional subtitle, trailing slot (switch, value or chevron). */
@Composable
fun NafsListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = onClick != null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val content: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let { NafsIconBadge(it, tint, size = 38.dp, iconSize = 20.dp) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = titleColor)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            trailing?.invoke()
            if (showChevron) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (onClick != null) Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), color = Color.Transparent) { content() }
    else Box(modifier.fillMaxWidth()) { content() }
}

/** Groups rows into one card with hairline separators. */
@Composable
fun NafsGroup(modifier: Modifier = Modifier, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        title?.let { NafsEyebrow(it, Modifier.padding(start = 4.dp)) }
        NafsCard(contentPadding = PaddingValues(vertical = 4.dp), content = content)
    }
}

@Composable
fun NafsDivider(modifier: Modifier = Modifier, inset: Dp = 16.dp) {
    Box(modifier.fillMaxWidth().padding(horizontal = inset).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)))
}

// ─── Buttons ─────────────────────────────────────────────────────────────────

@Composable
fun NafsPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 22.dp),
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        else {
            icon?.let { Icon(it, null, Modifier.size(19.dp)); Spacer(Modifier.width(8.dp)) }
            Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
fun NafsSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        icon?.let { Icon(it, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

// ─── Feedback states ─────────────────────────────────────────────────────────

enum class NafsBannerKind { INFO, SUCCESS, WARNING, ERROR, OFFLINE }

/** Inline message with an optional action. Never blocks the screen. */
@Composable
fun NafsBanner(
    message: String,
    modifier: Modifier = Modifier,
    kind: NafsBannerKind = NafsBannerKind.INFO,
    title: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val colors = Nafs.colors
    val scheme = MaterialTheme.colorScheme
    val (container, content, icon) = when (kind) {
        NafsBannerKind.INFO -> Triple(scheme.primaryContainer, scheme.onPrimaryContainer, Icons.Rounded.Info)
        NafsBannerKind.SUCCESS -> Triple(colors.successContainer, colors.onSuccessContainer, Icons.Rounded.CheckCircle)
        NafsBannerKind.WARNING -> Triple(colors.warningContainer, colors.onWarningContainer, Icons.Rounded.WarningAmber)
        NafsBannerKind.ERROR -> Triple(scheme.errorContainer, scheme.onErrorContainer, Icons.Rounded.ErrorOutline)
        NafsBannerKind.OFFLINE -> Triple(scheme.surfaceContainerHigh, scheme.onSurface, Icons.Rounded.CloudOff)
    }
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = container, contentColor = content) {
        Row(Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                title?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
                Text(message, style = MaterialTheme.typography.bodySmall)
            }
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) { Text(actionLabel, color = content, style = MaterialTheme.typography.labelLarge) }
            }
            if (onDismiss != null) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Dismiss", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** Friendly empty state: icon, headline, one sentence and the next action. */
@Composable
fun NafsEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    NafsCard(modifier, contentPadding = PaddingValues(horizontal = 24.dp, vertical = 30.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NafsIconBadge(icon, tint, size = 64.dp, iconSize = 30.dp)
            Spacer(Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(6.dp))
                NafsPrimaryButton(actionLabel, onAction)
            }
        }
    }
}

/** Full-screen failure with a retry. */
@Composable
fun NafsErrorState(
    title: String,
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NafsIconBadge(Icons.Rounded.CloudOff, MaterialTheme.colorScheme.error, size = 72.dp, iconSize = 34.dp)
            Spacer(Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            NafsPrimaryButton("Try again", onRetry, icon = Icons.Rounded.Refresh)
        }
    }
}

/** Placeholder blocks shown while the first load is in flight. */
@Composable
fun NafsLoadingState(modifier: Modifier = Modifier, cards: Int = 3) {
    val block = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    Column(modifier.fillMaxSize().padding(NafsSpace.screen), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.width(140.dp).height(14.dp).clip(CircleShape).background(block))
        Box(Modifier.width(220.dp).height(30.dp).clip(CircleShape).background(block))
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(150.dp).clip(MaterialTheme.shapes.extraLarge).background(block))
        repeat(cards) {
            Box(Modifier.fillMaxWidth().height(76.dp).clip(MaterialTheme.shapes.large).background(block))
        }
    }
}
