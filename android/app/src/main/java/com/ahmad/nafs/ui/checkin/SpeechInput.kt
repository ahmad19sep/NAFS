package com.ahmad.nafs.ui.checkin

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/**
 * Dictation through the system speech recognizer (ACTION_RECOGNIZE_SPEECH). The recognizer app records the
 * audio, so NAFS needs no microphone permission. [isAvailable] is false when no recognizer is installed;
 * screens hide their voice buttons then.
 */
@Stable
internal class SpeechInput(val isAvailable: Boolean, private val launch: () -> Unit) {
    fun start() = launch()
}

@Composable
internal fun rememberSpeechInput(prompt: String, onResult: (String) -> Unit, onUnavailable: () -> Unit): SpeechInput {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnUnavailable by rememberUpdatedState(onUnavailable)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull { !it.isNullOrBlank() }
                ?.let { heard -> currentOnResult(heard) }
        }
    }
    val available = remember(context) {
        runCatching { speechIntent(prompt).resolveActivity(context.packageManager) != null }.getOrDefault(false)
    }
    return remember(launcher, available, prompt) {
        SpeechInput(available) {
            try {
                launcher.launch(speechIntent(prompt))
            } catch (_: ActivityNotFoundException) {
                currentOnUnavailable()
            }
        }
    }
}

private fun speechIntent(prompt: String): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
}

/** Joins recognised speech onto text already typed. */
internal object DictationText {
    private val SENTENCE_END = setOf('.', '!', '?', '\n')

    /**
     * Appends [heard] after [existing] with a single space, starting a new sentence with a capital, and stops
     * at [maxLength] without splitting a character pair.
     */
    fun append(existing: String, heard: String, maxLength: Int = Int.MAX_VALUE): String {
        val words = heard.trim().replace(Regex("\\s+"), " ")
        if (words.isEmpty()) return existing
        val separator = if (existing.isEmpty() || existing.last().isWhitespace()) "" else " "
        val startsSentence = existing.isBlank() || existing.trimEnd(' ', '\t').lastOrNull() in SENTENCE_END
        val spoken = if (startsSentence) words.replaceFirstChar { it.titlecase() } else words
        val room = maxLength.toLong() - existing.length - separator.length
        if (room <= 0) return if (existing.length > maxLength) existing.take(maxLength) else existing
        if (spoken.length <= room) return existing + separator + spoken
        var clipped = spoken.take(room.toInt())
        if (clipped.isNotEmpty() && clipped.last().isHighSurrogate()) clipped = clipped.dropLast(1)
        return if (clipped.isBlank()) existing else existing + separator + clipped
    }
}
