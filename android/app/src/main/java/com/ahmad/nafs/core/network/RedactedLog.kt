package com.ahmad.nafs.core.network

import android.util.Log
import com.ahmad.nafs.BuildConfig

enum class NetworkEvent { REQUEST_SUCCEEDED, REQUEST_FAILED }
/** Only closed enums reach logcat. No body, URL, user ID, token, exception, or domain text. */
object RedactedLog {
    fun event(event: NetworkEvent, failure: ApiFailure? = null) {
        if (BuildConfig.DEBUG) runCatching { Log.d("NAFS", "event=${event.name} failure=${failure?.name ?: "none"}") }
    }
}
