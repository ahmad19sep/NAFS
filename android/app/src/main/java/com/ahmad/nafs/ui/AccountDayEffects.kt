package com.ahmad.nafs.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.delay

@Composable
fun AccountDayRefreshEffects(
    timezone: String,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnRefresh by rememberUpdatedState(onRefresh)

    DisposableEffect(context, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) currentOnRefresh()
        }
        val clockReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) = currentOnRefresh()
        }
        val clockFilter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        ContextCompat.registerReceiver(
            context,
            clockReceiver,
            clockFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            context.unregisterReceiver(clockReceiver)
        }
    }

    LaunchedEffect(timezone) {
        while (true) {
            val zone = runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.of("UTC"))
            val now = ZonedDateTime.now(zone)
            val nextDay = now.toLocalDate().plusDays(1).atStartOfDay(zone)
            delay(Duration.between(now, nextDay).toMillis().coerceAtLeast(1L) + 1_000L)
            currentOnRefresh()
        }
    }
}
