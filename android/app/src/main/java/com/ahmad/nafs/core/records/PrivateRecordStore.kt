package com.ahmad.nafs.core.records

import com.ahmad.nafs.core.database.RecordCacheDao
import com.ahmad.nafs.core.database.RecordCacheEntity
import com.ahmad.nafs.core.plans.EncryptedRecoveryValue
import com.ahmad.nafs.core.plans.RecoveryCacheCipher
import com.ahmad.nafs.core.network.ApiSessionTokens
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class PrivateRecordStore @Inject constructor(private val dao: RecordCacheDao, private val cipher: RecoveryCacheCipher,
    private val sessions: ApiSessionTokens) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    suspend fun read(userId: String, key: String): JsonObject? = mutex.withLock {
        check(sessions.isCurrentUser(userId))
        val value = dao.get(userId, key)?.let { json.parseToJsonElement(cipher.decrypt(EncryptedRecoveryValue(it.ciphertext, it.iv))) as JsonObject }
        check(sessions.isCurrentUser(userId))
        value
    }
    suspend fun write(userId: String, key: String, value: JsonObject) = mutex.withLock {
        check(sessions.isCurrentUser(userId))
        val encrypted = cipher.encrypt(value.toString())
        dao.put(RecordCacheEntity(userId, key, encrypted.ciphertext, encrypted.iv))
        if (!sessions.isCurrentUser(userId)) { dao.clear(userId); error("Account changed") }
    }
    suspend fun clear(userId: String) = mutex.withLock { dao.clear(userId) }
}
