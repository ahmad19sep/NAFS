package com.ahmad.nafs.core.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

/**
 * OkHttp closes response bodies on its engine dispatcher. The Android engine can close an HTTPS
 * response on the main thread when a session change cancels a request, crashing session restoration.
 */
internal fun nafsHttpEngine(): HttpClientEngine = OkHttp.create {
    config {
        // Repository retry intents decide whether to repeat a request that might already have saved.
        retryOnConnectionFailure(false)
    }
}
