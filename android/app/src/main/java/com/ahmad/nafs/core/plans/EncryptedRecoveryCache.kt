package com.ahmad.nafs.core.plans

import com.ahmad.nafs.core.database.CachedRecoveryDao
import com.ahmad.nafs.core.database.CachedRecoveryEntity
import com.ahmad.nafs.core.network.ApiSessionTokens
import javax.inject.Inject
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class EncryptedRecoveryCache @Inject constructor(private val dao: CachedRecoveryDao,
    private val cipher: RecoveryCacheCipher, private val sessions: ApiSessionTokens) : RecoveryCache {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    override suspend fun read(userId: String): RecoverySnapshot? {
        check(sessions.isCurrentUser(userId))
        val row = dao.get(userId) ?: return null
        val snapshot = json.decodeFromString<RecoverySnapshot>(cipher.decrypt(EncryptedRecoveryValue(row.payloadCiphertext, row.payloadIv)))
        check(sessions.isCurrentUser(userId)); require(RecoveryRules.validSnapshot(userId, snapshot))
        return snapshot
    }
    override suspend fun write(userId: String, snapshot: RecoverySnapshot) {
        check(sessions.isCurrentUser(userId)); require(RecoveryRules.validSnapshot(userId, snapshot))
        val value = cipher.encrypt(json.encodeToString(snapshot))
        check(sessions.isCurrentUser(userId)); dao.upsert(CachedRecoveryEntity(userId, value.ciphertext, value.iv))
    }
    override suspend fun clear(userId: String) = dao.clearForUser(userId)
}
