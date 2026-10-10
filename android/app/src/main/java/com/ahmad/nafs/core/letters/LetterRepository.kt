package com.ahmad.nafs.core.letters

import java.time.LocalDate

interface LetterRepository {
    suspend fun load(userId: String, cursor: LetterCursor? = null, detailId: String? = null): LetterLoadResult
    suspend fun save(userId: String, intent: LetterCreateIntent, today: LocalDate): LetterSaveResult
    suspend fun storeDraft(userId: String, draft: LetterDraft?)
    suspend fun resumeDraft(userId: String, intent: LetterCreateIntent): LetterResumeResult
    suspend fun acceptSaved(userId: String, intent: LetterCreateIntent): LetterSaveResult
    suspend fun generateReply(userId: String, intent: LetterReplyIntent, today: LocalDate): LetterReplyResult
    suspend fun clear(userId: String)
}
interface LetterRemote {
    suspend fun list(userId: String, cursor: LetterCursor?): List<FutureLetter>
    suspend fun get(userId: String, letterId: String): FutureLetter?
    suspend fun create(intent: LetterCreateIntent): FutureLetter
}
interface LetterCache {
    suspend fun read(userId: String): LetterSnapshot?
    suspend fun write(userId: String, snapshot: LetterSnapshot)
    suspend fun clear(userId: String)
}
