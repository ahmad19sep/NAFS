package com.ahmad.nafs.core.reports

import com.ahmad.nafs.core.database.CachedReportDao
import com.ahmad.nafs.core.database.CachedReportEntity
import com.ahmad.nafs.core.network.ApiSessionTokens
import javax.inject.Inject
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class EncryptedReportCache @Inject constructor(
    private val dao: CachedReportDao,
    private val cipher: ReportCacheCipher,
    private val sessions: ApiSessionTokens,
) : ReportCache {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun read(userId: String): ReportSnapshot? {
        check(sessions.isCurrentUser(userId))
        val row = dao.get(userId) ?: return null
        val snapshot = json.decodeFromString<ReportSnapshot>(cipher.decrypt(EncryptedReportValue(row.payloadCiphertext, row.payloadIv)))
        check(sessions.isCurrentUser(userId))
        require(ReportRules.validSnapshot(userId, snapshot))
        return snapshot
    }

    override suspend fun write(userId: String, snapshot: ReportSnapshot) {
        check(sessions.isCurrentUser(userId))
        require(ReportRules.validSnapshot(userId, snapshot))
        val encrypted = cipher.encrypt(json.encodeToString(snapshot))
        check(sessions.isCurrentUser(userId))
        dao.upsert(CachedReportEntity(userId, encrypted.ciphertext, encrypted.iv))
    }

    override suspend fun clear(userId: String) = dao.clearForUser(userId)
}
