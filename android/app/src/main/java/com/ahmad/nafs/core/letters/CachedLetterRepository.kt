package com.ahmad.nafs.core.letters

import com.ahmad.nafs.core.network.ApiSessionTokens
import com.ahmad.nafs.core.network.ApiFailure
import com.ahmad.nafs.core.network.NafsApiException
import com.ahmad.nafs.core.reports.ReportRules
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class CachedLetterRepository @Inject constructor(
    private val remote: LetterRemote,
    private val cache: LetterCache,
    private val sessions: ApiSessionTokens,
    private val replies: LetterReplyRemote,
) : LetterRepository {
    private val mutex = Mutex()

    override suspend fun load(userId: String, cursor: LetterCursor?, detailId: String?): LetterLoadResult {
        val initial = mutex.withLock { readSafe(userId) }
        return try {
            checkAccount(userId)
            require(detailId == null || ReportRules.validId(detailId))
            val page = LetterRules.page(userId, cursor, remote.list(userId, cursor))
            checkAccount(userId)
            if (cursor != null) return LetterLoadResult.Loaded(page)
            val checkedIntent = initial?.pending
            val current = checkedIntent?.let { remote.get(userId, it.id) }
            checkAccount(userId)
            require(current == null || (LetterRules.validLetter(userId, current) && current.id == checkedIntent.id))
            val checkedReply = initial?.pendingReply
            val replyLetter = checkedReply?.let { remote.get(userId, it.letter.id) }
            val wantedId = detailId ?: checkedReply?.letter?.id
            val detail = when {
                wantedId == null -> null
                wantedId == checkedReply?.letter?.id -> replyLetter
                else -> remote.get(userId, wantedId)
            }
            checkAccount(userId)
            require(replyLetter == null || (LetterRules.validLetter(userId, replyLetter) && replyLetter.id == checkedReply.letter.id))
            require(detail == null || (LetterRules.validLetter(userId, detail) && detail.id == wantedId))
            mutex.withLock {
                checkAccount(userId)
                val latest = readSafe(userId) ?: LetterSnapshot()
                // A create or reply confirmed during the list request must survive its older response.
                val inserted = latest.recent?.letters.orEmpty().filter { candidate ->
                    initial?.recent?.letters.orEmpty().none { it == candidate }
                }
                val effectiveDetail = inserted.find { it.id == wantedId } ?: detail
                val effectiveReply = inserted.find { it.id == checkedReply?.letter?.id } ?: replyLetter
                val removed = listOfNotNull(wantedId.takeIf { effectiveDetail == null }, checkedReply?.letter?.id.takeIf { effectiveReply == null })
                val merged = (inserted + listOfNotNull(effectiveDetail, effectiveReply) + page.letters).filterNot { it.id in removed }
                    .distinctBy { it.id }.sortedWith(LetterRules.newestFirst)
                val recent = LetterPage(merged.take(LetterRules.PAGE_SIZE), merged.take(LetterRules.PAGE_SIZE).lastOrNull()
                    ?.takeIf { page.next != null || merged.size > LetterRules.PAGE_SIZE }?.let { LetterCursor(it.writtenAt, it.id) })
                val recovered = current?.takeIf { latest.pending == checkedIntent && LetterRules.matches(userId, checkedIntent, it) }
                val different = current?.takeIf { latest.pending == checkedIntent && !LetterRules.matches(userId, checkedIntent, it) }
                var snapshot = latest.copy(recent = recent)
                if (recovered != null) snapshot = snapshot.copy(pending = null, draft = null)
                if (checkedReply != null && latest.pendingReply == checkedReply &&
                    (effectiveReply == null || LetterReplyRules.fingerprint(effectiveReply) != LetterReplyRules.fingerprint(checkedReply.letter) || LetterReplyRules.saved(effectiveReply))) {
                    snapshot = snapshot.copy(pendingReply = null)
                }
                try { cache.write(userId, snapshot) } catch (cancellation: CancellationException) { throw cancellation }
                catch (_: Exception) { snapshot = latest.copy(recent = recent) }
                checkAccount(userId)
                LetterLoadResult.Loaded(recent, snapshot.draft, snapshot.pending,
                    pendingMissing = checkedIntent != null && snapshot.pending == checkedIntent && current == null,
                    recovered = recovered, different = different, pendingReply = snapshot.pendingReply,
                    detail = effectiveDetail, detailMissing = wantedId != null && effectiveDetail == null)
            }
        } catch (cancellation: CancellationException) { throw cancellation }
        catch (_: Exception) {
            if (!sessions.isCurrentUser(userId)) return LetterLoadResult.Failure("Sign in again to load your letters.")
            val snapshot = if (cursor == null) mutex.withLock { readSafe(userId) } else null
            if (!sessions.isCurrentUser(userId)) LetterLoadResult.Failure("Sign in again to load your letters.")
            else if (snapshot != null) LetterLoadResult.Loaded(snapshot.recent?.copy(next = null) ?: LetterPage(emptyList()), snapshot.draft, snapshot.pending,
                isOffline = true, pendingReply = snapshot.pendingReply, detail = snapshot.recent?.letters?.find { it.id == detailId }
                    ?: snapshot.pendingReply?.letter?.takeIf { detailId == null || it.id == detailId })
            else LetterLoadResult.Failure("Unable to load letters. Check your connection and retry.")
        }
    }

    override suspend fun save(userId: String, intent: LetterCreateIntent, today: LocalDate): LetterSaveResult {
        var persisted = false
        return try {
            checkAccount(userId)
            require(LetterRules.validIntent(userId, intent))
            mutex.withLock {
                val snapshot = readSafe(userId) ?: LetterSnapshot()
                require(snapshot.pending == null || snapshot.pending == intent)
                require(snapshot.draft == null || snapshot.draft.id == intent.id)
                if (snapshot.pending == null) require(LetterRules.error(intent.draft(), today) == null)
                checkAccount(userId)
                cache.write(userId, snapshot.copy(pending = intent, draft = intent.draft()))
                persisted = true
            }
            checkAccount(userId)
            val existing = remote.get(userId, intent.id)
            val saved = existing ?: try { remote.create(intent) } catch (cancellation: CancellationException) { throw cancellation }
                catch (error: Exception) { remote.get(userId, intent.id) ?: throw error }
            checkAccount(userId)
            if (!LetterRules.matches(userId, intent, saved)) {
                LetterSaveResult.Failure("The saved letter differs from this draft. Reload your letters before continuing.", pending = true)
            } else complete(userId, intent, saved)
        } catch (cancellation: CancellationException) { throw cancellation }
        catch (_: Exception) {
            LetterSaveResult.Failure(when {
                !sessions.isCurrentUser(userId) -> "Sign in again before saving your letter."
                !persisted -> "Unable to protect this draft on your device. Check your date and available storage, then retry."
                else -> "The save could not be confirmed. Your letter is retained; retry or check your saved letters."
            }, pending = persisted)
        }
    }

    override suspend fun storeDraft(userId: String, draft: LetterDraft?) = mutex.withLock {
        checkAccount(userId)
        require(draft == null || LetterRules.validDraft(userId, draft))
        val snapshot = readSafe(userId) ?: LetterSnapshot()
        check(snapshot.pending == null)
        checkAccount(userId)
        cache.write(userId, snapshot.copy(draft = draft))
    }

    override suspend fun resumeDraft(userId: String, intent: LetterCreateIntent): LetterResumeResult = try {
        checkAccount(userId)
        require(LetterRules.validIntent(userId, intent))
        val current = remote.get(userId, intent.id)
        checkAccount(userId)
        if (current != null) {
            if (LetterRules.matches(userId, intent, current)) LetterResumeResult.Saved(complete(userId, intent, current))
            else LetterResumeResult.Failure("This letter already exists. Reload to read the saved letter.")
        } else mutex.withLock {
            val snapshot = readSafe(userId) ?: error("Missing retained save")
            check(snapshot.pending == intent)
            checkAccount(userId)
            cache.write(userId, snapshot.copy(pending = null))
            checkAccount(userId)
            LetterResumeResult.Editing(intent.draft())
        }
    } catch (cancellation: CancellationException) { throw cancellation }
    catch (_: Exception) { LetterResumeResult.Failure("Unable to check the saved letter. Reconnect and retry; your draft is retained.") }

    override suspend fun clear(userId: String) = mutex.withLock { cache.clear(userId) }

    override suspend fun generateReply(userId: String, intent: LetterReplyIntent, today: LocalDate): LetterReplyResult {
        var persisted = false
        return try {
            checkAccount(userId)
            persisted = mutex.withLock { readSafe(userId)?.pendingReply == intent }
            require(LetterReplyRules.validIntent(userId, intent) && LetterReplyRules.eligible(intent.letter, today))
            mutex.withLock {
                val snapshot = readSafe(userId) ?: LetterSnapshot()
                check(snapshot.pending == null && (snapshot.pendingReply == null || snapshot.pendingReply == intent))
                checkAccount(userId)
                cache.write(userId, snapshot.copy(pendingReply = intent))
                persisted = true
            }
            checkAccount(userId)
            val current = remote.get(userId, intent.letter.id) ?: error("Missing letter")
            checkAccount(userId)
            require(LetterRules.validLetter(userId, current) && LetterReplyRules.fingerprint(current) == LetterReplyRules.fingerprint(intent.letter))
            val response = if (LetterReplyRules.saved(current)) LetterReplyResponse(current.aiReplyText!!, current)
                else {
                    require(current.aiReplyText == null && current.deliveredAt == null)
                    replies.generate(userId, intent)
                }
            checkAccount(userId)
            require(LetterReplyRules.matches(userId, intent, response.letter) && response.reply == response.letter.aiReplyText)
            var cleanupFailed = false
            try {
                mutex.withLock {
                    val snapshot = readSafe(userId) ?: LetterSnapshot()
                    val recent = (listOf(response.letter) + snapshot.recent?.letters.orEmpty().filterNot { it.id == response.letter.id })
                        .sortedWith(LetterRules.newestFirst).take(LetterRules.PAGE_SIZE)
                    checkAccount(userId)
                    cache.write(userId, snapshot.copy(recent = LetterPage(recent),
                        pendingReply = snapshot.pendingReply.takeUnless { it == intent }))
                    cleanupFailed = snapshot.pendingReply != null && snapshot.pendingReply != intent
                }
            } catch (cancellation: CancellationException) { throw cancellation } catch (_: Exception) { cleanupFailed = true }
            checkAccount(userId)
            LetterReplyResult.Saved(response.letter, cleanupFailed, response.contextPartial, response.historySaved)
        } catch (cancellation: CancellationException) { throw cancellation }
        catch (error: Exception) {
            LetterReplyResult.Failure(when {
                !sessions.isCurrentUser(userId) -> LetterReplyRules.message(ApiFailure.AUTHENTICATION)
                !persisted -> "Unable to protect this reply request on your device. Check its revisit date and available storage, then retry."
                error is NafsApiException -> LetterReplyRules.message(error.failure)
                else -> "Your saved letter could not be checked. Reload its current version before retrying the reply."
            }, pending = persisted)
        }
    }

    override suspend fun acceptSaved(userId: String, intent: LetterCreateIntent): LetterSaveResult = try {
        checkAccount(userId)
        require(LetterRules.validIntent(userId, intent))
        mutex.withLock { check(readSafe(userId)?.pending == intent) }
        val current = remote.get(userId, intent.id) ?: error("Letter no longer exists")
        checkAccount(userId)
        require(LetterRules.validLetter(userId, current) && current.id == intent.id)
        complete(userId, intent, current)
    } catch (cancellation: CancellationException) { throw cancellation }
    catch (_: Exception) { LetterSaveResult.Failure("Unable to load the saved version. Your retained draft is still here; reconnect and retry.", pending = true) }

    private suspend fun complete(userId: String, intent: LetterCreateIntent, letter: FutureLetter): LetterSaveResult.Saved {
        var cleanupFailed = false
        try {
            mutex.withLock {
                val snapshot = readSafe(userId) ?: LetterSnapshot()
                val recent = (listOf(letter) + snapshot.recent?.letters.orEmpty().filterNot { it.id == letter.id })
                    .sortedWith(LetterRules.newestFirst).take(LetterRules.PAGE_SIZE)
                val canClear = snapshot.pending == intent || (snapshot.pending == null && snapshot.draft?.intent() == intent)
                checkAccount(userId)
                cache.write(userId, snapshot.copy(recent = LetterPage(recent),
                    pending = if (canClear) null else snapshot.pending, draft = if (canClear) null else snapshot.draft))
                cleanupFailed = !canClear && snapshot.pending != null
            }
        } catch (cancellation: CancellationException) { throw cancellation } catch (_: Exception) { cleanupFailed = true }
        checkAccount(userId)
        return LetterSaveResult.Saved(letter, localCleanupFailed = cleanupFailed)
    }

    private suspend fun readSafe(userId: String): LetterSnapshot? = try {
        checkAccount(userId)
        cache.read(userId)?.also { checkAccount(userId); require(LetterRules.validSnapshot(userId, it)) }
    } catch (cancellation: CancellationException) { throw cancellation } catch (_: Exception) { null }
    private fun checkAccount(userId: String) = check(sessions.isCurrentUser(userId))
}
