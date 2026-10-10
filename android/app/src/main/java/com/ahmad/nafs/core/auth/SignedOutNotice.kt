package com.ahmad.nafs.core.auth

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A message for the sign-in screen when NAFS signs someone out on purpose, such as right after deleting the
 * account. The screen that caused it is gone by then, so the message waits here until the sign-in screen shows it.
 */
@Singleton
class SignedOutNotice @Inject constructor() {
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun post(text: String) {
        _message.value = text
    }

    fun clear() {
        _message.value = null
    }
}
