package com.ahmad.nafs.core.letters

import com.ahmad.nafs.core.time.ServerTimestamp
import com.ahmad.nafs.core.reports.ReportRules
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FutureLetter(
    val id: String,
    @SerialName("user_id") val userId: String,
    val content: String,
    @SerialName("written_at") val writtenAt: String,
    @SerialName("target_deliver_date") val targetDate: String,
    @SerialName("delivered_at") val deliveredAt: String? = null,
    @SerialName("ai_reply_text") val aiReplyText: String? = null,
) {
    fun writtenLabel(timezone: String): String = ServerTimestamp.parse(writtenAt)
        .atZone(runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.of("UTC")))
        .format(DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.getDefault()))
    val targetLabel: String get() = LocalDate.parse(targetDate).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
    fun status(today: LocalDate): String = when {
        !aiReplyText.isNullOrBlank() -> "Reply saved"
        deliveredAt != null -> "Delivered"
        LocalDate.parse(targetDate) <= today -> "Ready to revisit"
        else -> "Revisit on $targetLabel"
    }
}

@Serializable
data class LetterDraft(val id: String, val userId: String, val content: String, val targetDate: String) {
    fun intent(): LetterCreateIntent = LetterCreateIntent(id, userId, content.trim(), targetDate)
}

/** Only author-owned creation fields are sent. Written time, delivery and AI replies belong to the server. */
@Serializable
data class LetterCreateIntent(
    val id: String,
    @SerialName("user_id") val userId: String,
    val content: String,
    @SerialName("target_deliver_date") val targetDate: String,
) {
    fun draft(): LetterDraft = LetterDraft(id, userId, content, targetDate)
}

@Serializable
data class LetterCursor(val writtenAt: String, val id: String)
@Serializable
data class LetterPage(val letters: List<FutureLetter>, val next: LetterCursor? = null)
@Serializable
data class LetterSnapshot(val recent: LetterPage? = null, val draft: LetterDraft? = null, val pending: LetterCreateIntent? = null,
    val pendingReply: LetterReplyIntent? = null)

sealed interface LetterLoadResult {
    data class Loaded(
        val page: LetterPage,
        val draft: LetterDraft? = null,
        val pending: LetterCreateIntent? = null,
        val isOffline: Boolean = false,
        val pendingMissing: Boolean = false,
        val recovered: FutureLetter? = null,
        val different: FutureLetter? = null,
        val pendingReply: LetterReplyIntent? = null,
        val detail: FutureLetter? = null,
        val detailMissing: Boolean = false,
    ) : LetterLoadResult
    data class Failure(val message: String) : LetterLoadResult
}
sealed interface LetterSaveResult {
    data class Saved(val letter: FutureLetter, val localCleanupFailed: Boolean = false) : LetterSaveResult
    data class Failure(val message: String, val pending: Boolean) : LetterSaveResult
}
sealed interface LetterResumeResult {
    data class Editing(val draft: LetterDraft) : LetterResumeResult
    data class Saved(val result: LetterSaveResult.Saved) : LetterResumeResult
    data class Failure(val message: String) : LetterResumeResult
}

object LetterRules {
    const val PAGE_SIZE = 20
    const val MAX_CONTENT = 10_000
    val newestFirst = compareByDescending<FutureLetter> { ServerTimestamp.parse(it.writtenAt) }.thenByDescending { it.id.lowercase(Locale.ROOT) }
    fun validLetter(userId: String, letter: FutureLetter): Boolean = letter.userId == userId && ReportRules.validId(letter.id) &&
        letter.content.length <= 180_000 && (letter.aiReplyText?.length ?: 0) <= 180_000 &&
        runCatching { ServerTimestamp.parse(letter.writtenAt); LocalDate.parse(letter.targetDate); letter.deliveredAt?.let(ServerTimestamp::parse) }.isSuccess
    fun validDraft(userId: String, draft: LetterDraft): Boolean = draft.userId == userId && ReportRules.validId(draft.id) &&
        draft.content.length <= 180_000 && draft.targetDate.length <= 40
    fun validIntent(userId: String, intent: LetterCreateIntent): Boolean = intent.userId == userId && ReportRules.validId(intent.id) &&
        intent.content.isNotBlank() && intent.content == intent.content.trim() && intent.content.length <= MAX_CONTENT &&
        runCatching { LocalDate.parse(intent.targetDate) }.isSuccess
    fun error(draft: LetterDraft, today: LocalDate): String? = when {
        draft.content.isBlank() -> "Write something to your future self."
        draft.content.trim().length > MAX_CONTENT -> "Keep your letter to 10,000 characters or fewer."
        runCatching { LocalDate.parse(draft.targetDate) }.isFailure -> "Choose a valid date in YYYY-MM-DD format."
        LocalDate.parse(draft.targetDate) <= today -> "Choose a date after today in your account timezone."
        else -> null
    }
    fun matches(userId: String, intent: LetterCreateIntent, letter: FutureLetter): Boolean = validLetter(userId, letter) &&
        letter.id == intent.id && letter.content == intent.content && letter.targetDate == intent.targetDate
    fun validCursor(cursor: LetterCursor): Boolean = ReportRules.validId(cursor.id) && runCatching { ServerTimestamp.parse(cursor.writtenAt) }.isSuccess
    fun page(userId: String, cursor: LetterCursor?, rows: List<FutureLetter>): LetterPage {
        require(cursor == null || validCursor(cursor))
        require(rows.size <= PAGE_SIZE + 1 && rows.map { it.id }.distinct().size == rows.size)
        require(rows.all { letter -> validLetter(userId, letter) && (cursor == null ||
            ServerTimestamp.parse(letter.writtenAt) < ServerTimestamp.parse(cursor.writtenAt) ||
            (ServerTimestamp.parse(letter.writtenAt) == ServerTimestamp.parse(cursor.writtenAt) && letter.id.lowercase(Locale.ROOT) < cursor.id.lowercase(Locale.ROOT))) })
        val letters = rows.sortedWith(newestFirst).take(PAGE_SIZE)
        return LetterPage(letters, letters.lastOrNull()?.takeIf { rows.size > PAGE_SIZE }?.let { LetterCursor(it.writtenAt, it.id) })
    }
    fun validSnapshot(userId: String, snapshot: LetterSnapshot): Boolean =
        (snapshot.recent == null || (snapshot.recent.letters.size <= PAGE_SIZE && runCatching { page(userId, null, snapshot.recent.letters) }.isSuccess)) &&
        (snapshot.recent?.next == null || (validCursor(snapshot.recent.next) && snapshot.recent.letters.lastOrNull()?.let {
            LetterCursor(it.writtenAt, it.id) == snapshot.recent.next
        } == true)) && (snapshot.draft == null || validDraft(userId, snapshot.draft)) &&
        (snapshot.pending == null || (validIntent(userId, snapshot.pending) && snapshot.draft?.intent() == snapshot.pending)) &&
        (snapshot.pendingReply == null || LetterReplyRules.validIntent(userId, snapshot.pendingReply))
}
