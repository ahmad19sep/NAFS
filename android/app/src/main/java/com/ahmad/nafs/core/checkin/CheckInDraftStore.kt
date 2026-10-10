package com.ahmad.nafs.core.checkin

import com.ahmad.nafs.core.database.RecordCacheDao
import com.ahmad.nafs.core.database.RecordCacheEntity
import com.ahmad.nafs.core.network.ApiSessionTokens
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Unsaved check-in text for one account day: the plan, the half-typed priority and the evening notes.
 * [baseUpdatedAt] is the saved record's version the draft was written on, so a restore can tell when the
 * saved check-in has changed elsewhere since.
 */
@Serializable
data class CheckInDraft(
    val date: String,
    val recordId: String,
    val baseUpdatedAt: String? = null,
    val tasks: List<CheckInTask> = emptyList(),
    val eveningText: String? = null,
    val newTask: String = "",
) {
    val hasText: Boolean
        get() = tasks.isNotEmpty() || !eveningText.isNullOrBlank() || newTask.isNotBlank()
}

/** The current day's draft, plus one older draft that was never saved before the account day changed. */
data class CheckInDrafts(val current: CheckInDraft? = null, val previous: CheckInDraft? = null)

interface CheckInDraftStore {
    suspend fun read(userId: String): CheckInDrafts
    suspend fun writeCurrent(userId: String, draft: CheckInDraft?)
    suspend fun writePrevious(userId: String, draft: CheckInDraft?)
}

object CheckInDraftRules {
    const val MAX_TEXT = 10_000

    /** Only drafts for this account's own valid day, inside the same limits a save allows, are restored. */
    fun valid(userId: String, draft: CheckInDraft): Boolean = userId.isNotBlank() && draft.recordId.isNotBlank() &&
        draft.recordId.length <= 80 && runCatching { java.time.LocalDate.parse(draft.date) }.isSuccess &&
        draft.tasks.size <= CheckInRules.MAX_TASKS && draft.tasks.all { it.text.length <= 200 } &&
        (draft.eveningText?.length ?: 0) <= MAX_TEXT && draft.newTask.length <= 200

    /**
     * Sorts a stored current draft against [today]: same day → restore it; another day → keep it aside as
     * the previous draft so today's autosave can never overwrite it.
     */
    fun sort(drafts: CheckInDrafts, today: String): CheckInDrafts {
        val current = drafts.current?.takeIf { it.hasText }
        return when {
            current == null -> CheckInDrafts(previous = drafts.previous?.takeIf { it.hasText && it.date != today })
            current.date == today -> CheckInDrafts(current, drafts.previous?.takeIf { it.hasText && it.date != today })
            else -> CheckInDrafts(previous = current)
        }
    }

    /** Whether [draft] still differs from the saved record (or from an empty day when none is saved). */
    fun differs(draft: CheckInDraft, saved: DailyCheckIn?): Boolean {
        val savedTasks = saved?.tasks.orEmpty().map { it.copy(text = it.text.trim()) }
        val draftTasks = draft.tasks.map { it.copy(text = it.text.trim()) }
        val savedText = saved?.eveningText?.trim().orEmpty()
        val draftText = draft.eveningText?.trim().orEmpty()
        return draftTasks != savedTasks || draftText != savedText || draft.newTask.isNotBlank()
    }
}

/**
 * Drafts are encrypted with the check-in cache key and kept in the shared encrypted record table, so they
 * survive the app being killed and are removed with the rest of the account's private data at sign-out.
 * Clearing writes an empty payload, since that table has no single-row delete.
 */
@Singleton
class EncryptedCheckInDraftStore @Inject constructor(
    private val dao: RecordCacheDao,
    private val cipher: CheckInCacheCipher,
    private val sessions: ApiSessionTokens,
) : CheckInDraftStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()

    override suspend fun read(userId: String): CheckInDrafts = mutex.withLock {
        check(sessions.isCurrentUser(userId))
        CheckInDrafts(readSlot(userId, CURRENT), readSlot(userId, PREVIOUS))
    }

    override suspend fun writeCurrent(userId: String, draft: CheckInDraft?) = write(userId, CURRENT, draft)

    override suspend fun writePrevious(userId: String, draft: CheckInDraft?) = write(userId, PREVIOUS, draft)

    private suspend fun readSlot(userId: String, key: String): CheckInDraft? {
        val row = dao.get(userId, key) ?: return null
        val payload = json.decodeFromString<StoredDraft>(cipher.decrypt(EncryptedCheckInValue(row.ciphertext, row.iv)))
        return payload.draft?.takeIf { CheckInDraftRules.valid(userId, it) }
    }

    private suspend fun write(userId: String, key: String, draft: CheckInDraft?) = mutex.withLock {
        check(sessions.isCurrentUser(userId))
        require(draft == null || CheckInDraftRules.valid(userId, draft))
        val encrypted = cipher.encrypt(json.encodeToString(StoredDraft(draft?.takeIf { it.hasText })))
        check(sessions.isCurrentUser(userId))
        dao.put(RecordCacheEntity(userId, key, encrypted.ciphertext, encrypted.iv))
    }

    private companion object {
        const val CURRENT = "checkin/draft/current-v1"
        const val PREVIOUS = "checkin/draft/previous-v1"
    }
}

@Serializable
private data class StoredDraft(val draft: CheckInDraft?)
