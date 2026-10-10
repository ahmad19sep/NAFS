package com.ahmad.nafs.core.records

import android.net.Uri
import com.ahmad.nafs.core.challenges.ChallengeEvidenceReader
import com.ahmad.nafs.core.challenges.ChallengeEvidenceReadResult
import com.ahmad.nafs.core.network.SupabaseClientProvider
import com.ahmad.nafs.core.network.SupabaseApiSessionTokens
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import java.util.UUID
import javax.inject.Inject

class RecordMediaUploader @Inject constructor(private val reader: ChallengeEvidenceReader,
    private val provider: SupabaseClientProvider, private val sessions: SupabaseApiSessionTokens) {
    suspend fun upload(userId: String, bucketId: String, uri: Uri): String {
        require(bucketId in setOf("avatars", "log-photos", "screentime-shots"))
        check(sessions.isCurrentUser(userId))
        val photo = when (val result = reader.read(uri)) {
            is ChallengeEvidenceReadResult.Success -> result.evidence
            is ChallengeEvidenceReadResult.Failure -> throw IllegalArgumentException(result.message)
        }
        val bucket = checkNotNull(provider.client).storage.from(bucketId)
        val path = "$userId/${UUID.randomUUID()}.${photo.extension}"
        bucket.upload(path, photo.bytes) { upsert = false; contentType = ContentType.parse(photo.mimeType) }
        check(sessions.isCurrentUser(userId))
        return bucket.publicUrl(path)
    }
}
