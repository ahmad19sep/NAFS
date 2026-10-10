package com.ahmad.nafs.ui.coach

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent

/** Copies plain text with the platform clipboard. Returns false when the clipboard is unavailable. */
internal fun copyPlainText(context: Context, label: String, text: String): Boolean = try {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    if (clipboard == null) {
        false
    } else {
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        true
    }
} catch (_: RuntimeException) {
    // Some devices throw when the clipboard service is busy or the clip is too large.
    false
}

/** Opens the system share sheet for plain text. Returns false when no app can receive it. */
internal fun sharePlainText(context: Context, title: String, text: String): Boolean = try {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, title))
    true
} catch (_: ActivityNotFoundException) {
    false
}
