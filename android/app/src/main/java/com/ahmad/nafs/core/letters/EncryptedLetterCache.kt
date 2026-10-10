package com.ahmad.nafs.core.letters

import com.ahmad.nafs.core.database.CachedLetterDao
import com.ahmad.nafs.core.database.CachedLetterEntity
import com.ahmad.nafs.core.network.ApiSessionTokens
import javax.inject.Inject
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class EncryptedLetterCache @Inject constructor(
    private val dao: CachedLetterDao,
    private val cipher: LetterCacheCipher,
    private val sessions: ApiSessionTokens,
) : LetterCache {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    override suspend fun read(userId: String): LetterSnapshot? {
        check(sessions.isCurrentUser(userId))
        val row = dao.get(userId) ?: return null
        val snapshot = json.decodeFromString<LetterSnapshot>(cipher.decrypt(EncryptedLetterValue(row.payloadCiphertext, row.payloadIv)))
        check(sessions.isCurrentUser(userId))
        require(LetterRules.validSnapshot(userId, snapshot))
        return snapshot
    }
    override suspend fun write(userId: String, snapshot: LetterSnapshot) {
        check(sessions.isCurrentUser(userId))
        require(LetterRules.validSnapshot(userId, snapshot))
        val encrypted = cipher.encrypt(json.encodeToString(snapshot))
        check(sessions.isCurrentUser(userId))
        dao.upsert(CachedLetterEntity(userId, encrypted.ciphertext, encrypted.iv))
    }
    override suspend fun clear(userId: String) = dao.clearForUser(userId)
}
