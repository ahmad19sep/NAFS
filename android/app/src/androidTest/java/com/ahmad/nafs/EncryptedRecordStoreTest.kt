package com.ahmad.nafs

import com.ahmad.nafs.core.database.RecordCacheDao
import com.ahmad.nafs.core.database.RecordCacheEntity
import com.ahmad.nafs.core.network.ApiSessionTokens
import com.ahmad.nafs.core.plans.RecoveryCacheCipher
import com.ahmad.nafs.core.records.PrivateRecordStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test

/** Uses Android Keystore with synthetic data and an in-memory DAO; no Auth or account writes. */
class EncryptedRecordStoreTest {
    private class Tokens(var current: String = "test-account-a") : ApiSessionTokens {
        override suspend fun accessToken(userId: String, forceRefresh: Boolean): String = error("No network expected")
        override fun isCurrentUser(userId: String) = userId == current
    }
    private class Dao : RecordCacheDao {
        val rows = mutableMapOf<Pair<String, String>, RecordCacheEntity>()
        var afterPut: () -> Unit = {}
        override suspend fun get(userId: String, key: String) = rows[userId to key]
        override suspend fun put(entry: RecordCacheEntity) { rows[entry.userId to entry.cacheKey] = entry; afterPut() }
        override suspend fun clear(userId: String) { rows.keys.removeAll { it.first == userId } }
    }
    @Test fun privateDraftsRoundTripThroughKeystoreWithoutPlaintextStorage() = runBlocking {
        val dao = Dao(); val sessions = Tokens(); val store = PrivateRecordStore(dao, RecoveryCacheCipher(), sessions)
        val draft = buildJsonObject { put("draft", "synthetic private reflection"); put("pending", "frozen test intent") }
        store.write(sessions.current, "test-record", draft)
        val encrypted = dao.rows.values.single()
        assertFalse(encrypted.ciphertext.contains("synthetic"))
        assertEquals(draft, store.read(sessions.current, "test-record"))
        assertTrue(encrypted.iv.isNotBlank())
    }
    @Test fun accountChangeDuringPersistenceClearsTheLateWrite() = runBlocking {
        val dao = Dao(); val sessions = Tokens(); val store = PrivateRecordStore(dao, RecoveryCacheCipher(), sessions)
        dao.afterPut = { sessions.current = "test-account-b" }
        assertTrue(runCatching { store.write("test-account-a", "test-record", buildJsonObject { put("draft", "test") }) }.isFailure)
        assertTrue(dao.rows.isEmpty())
        assertTrue(runCatching { store.read("test-account-a", "test-record") }.isFailure)
    }
    @Test fun unreadableDraftsFailWithoutSilentlyReplacingTheRetainedIntent() = runBlocking {
        val dao = Dao(); val sessions = Tokens(); val store = PrivateRecordStore(dao, RecoveryCacheCipher(), sessions)
        store.write(sessions.current, "test-record", buildJsonObject { put("pending", "frozen test intent") })
        val original = dao.rows.values.single()
        val corrupt = original.copy(ciphertext = "invalid ciphertext")
        dao.rows[sessions.current to "test-record"] = corrupt
        assertTrue(runCatching { store.read(sessions.current, "test-record") }.isFailure)
        assertEquals(corrupt, dao.rows.values.single())
    }
}
