package com.ahmad.nafs.ui.deen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmad.nafs.core.deen.HijriPreferences
import com.ahmad.nafs.core.deen.NextPrayerLookup
import com.ahmad.nafs.core.deen.NextPrayerReader
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The next prayer from saved times (null until read) and the user's Hijri adjustment. */
data class PrayerClockState(val next: NextPrayerLookup? = null, val hijriAdjustment: Int = 0)

/** Next prayer and Hijri adjustment for any screen that shows them, such as Deen and Home. Reads only local copies. */
@HiltViewModel
class PrayerClockViewModel @Inject constructor(
    private val reader: NextPrayerReader,
    private val hijri: HijriPreferences,
) : ViewModel() {
    private val _state = MutableStateFlow(PrayerClockState())
    val state = _state.asStateFlow()
    private var account: String? = null
    private var zone = "UTC"
    private var readJob: Job? = null
    private var hijriJob: Job? = null

    fun initialize(userId: String, timezone: String) {
        if (account == userId && zone == timezone) return
        account = userId
        zone = timezone
        _state.value = PrayerClockState(hijriAdjustment = hijri.adjustment(userId))
        hijriJob?.cancel()
        hijriJob = viewModelScope.launch {
            hijri.adjustments(userId).collect { days -> _state.update { it.copy(hijriAdjustment = days) } }
        }
        refresh()
    }

    /** Reads the saved times again, for example when a prayer has begun or Prayer times saved new ones. */
    fun refresh() {
        val user = account ?: return
        val timezone = zone
        readJob?.cancel()
        readJob = viewModelScope.launch {
            val lookup = reader.lookup(user, timezone, ZonedDateTime.now(accountZone(timezone)))
            if (account == user && zone == timezone) _state.update { it.copy(next = lookup) }
        }
    }
}

internal fun accountZone(timezone: String): ZoneId = runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneOffset.UTC)

/** The current time in the account timezone, updated every second while on screen. */
@Composable
internal fun rememberAccountNow(timezone: String): ZonedDateTime {
    var now by remember(timezone) { mutableStateOf(ZonedDateTime.now(accountZone(timezone))) }
    LaunchedEffect(timezone) {
        while (true) {
            now = ZonedDateTime.now(accountZone(timezone))
            delay(1_000)
        }
    }
    return now
}

/** Asks for the next prayer again once the shown one has begun, so the card moves on by itself. */
@Composable
internal fun RefreshWhenPrayerBegins(lookup: NextPrayerLookup?, onRefresh: () -> Unit) {
    val refresh by rememberUpdatedState(onRefresh)
    val at = (lookup as? NextPrayerLookup.Found)?.next?.at
    LaunchedEffect(at) {
        if (at == null) return@LaunchedEffect
        val wait = Duration.between(ZonedDateTime.now(at.zone), at).toMillis()
        delay(wait.coerceAtLeast(0L) + 1_000L)
        refresh()
    }
}
