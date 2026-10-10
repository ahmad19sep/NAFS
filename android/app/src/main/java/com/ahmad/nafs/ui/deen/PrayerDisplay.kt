package com.ahmad.nafs.ui.deen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahmad.nafs.core.deen.HijriDay
import com.ahmad.nafs.core.deen.Prayer
import com.ahmad.nafs.ui.theme.AmiriArabic
import com.ahmad.nafs.ui.theme.Nafs
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Arabic names, icons and countdown wording shared by Deen, Prayer times and the next-prayer card. */
internal object PrayerDisplay {
    val ArabicLarge = TextStyle(fontFamily = AmiriArabic, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 34.sp)
    val ArabicSmall = TextStyle(fontFamily = AmiriArabic, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp)

    private val clock = DateTimeFormatter.ofPattern("HH:mm")

    fun arabic(prayer: Prayer): String = when (prayer) {
        Prayer.FAJR -> "الفجر"
        Prayer.DHUHR -> "الظهر"
        Prayer.ASR -> "العصر"
        Prayer.MAGHRIB -> "المغرب"
        Prayer.ISHA -> "العشاء"
    }

    fun arabic(name: String): String = Prayer.entries.firstOrNull { it.label == name }?.let { arabic(it) }.orEmpty()

    fun icon(prayer: Prayer): ImageVector = when (prayer) {
        Prayer.FAJR, Prayer.MAGHRIB -> Icons.Rounded.WbTwilight
        Prayer.DHUHR -> Icons.Rounded.WbSunny
        Prayer.ASR -> Icons.Rounded.LightMode
        Prayer.ISHA -> Icons.Rounded.NightsStay
    }

    fun icon(name: String): ImageVector = Prayer.entries.firstOrNull { it.label == name }?.let { icon(it) } ?: Icons.Rounded.NightsStay

    fun time(time: LocalTime): String = time.format(clock)

    /** "2h 14m", "42 min" or "under a minute". */
    fun countdown(remaining: Duration): String {
        val hours = remaining.toHours()
        val minutes = remaining.toMinutes() % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "$minutes min"
            else -> "under a minute"
        }
    }

    /** The countdown in words for screen readers: "2 hours 14 minutes". */
    fun spokenCountdown(remaining: Duration): String {
        val hours = remaining.toHours()
        val minutes = remaining.toMinutes() % 60
        fun unit(value: Long, word: String) = "$value $word${if (value == 1L) "" else "s"}"
        return when {
            hours > 0 && minutes > 0 -> "${unit(hours, "hour")} ${unit(minutes, "minute")}"
            hours > 0 -> unit(hours, "hour")
            minutes > 0 -> unit(minutes, "minute")
            else -> "less than a minute"
        }
    }
}

/** The Hijri date with its Arabic month in Amiri, for the Deen header, the Prayer times hero and Home. */
@Composable
fun HijriDateLine(
    hijri: HijriDay,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    accent: Color = Nafs.colors.deen,
) {
    Row(
        modifier.clearAndSetSemantics { contentDescription = "Hijri date ${hijri.label}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Rounded.NightsStay, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
        Text(hijri.label, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
        Text(hijri.monthArabic, style = PrayerDisplay.ArabicSmall, color = accent, maxLines = 1)
    }
}
