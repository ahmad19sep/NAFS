package com.ahmad.nafs.core.challenges

import android.content.Context
import android.net.Uri
import androidx.core.database.getLongOrNull
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface ChallengeEvidenceReadResult {
    data class Success(val evidence: ChallengeEvidence) : ChallengeEvidenceReadResult
    data class Failure(val message: String) : ChallengeEvidenceReadResult
}

class ChallengeEvidenceReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun read(uri: Uri): ChallengeEvidenceReadResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri)?.lowercase()
            ?: return@withContext ChallengeEvidenceReadResult.Failure(
                "Choose a JPEG, PNG, WebP, HEIC, or HEIF image.",
            )
        val extension = SUPPORTED_IMAGE_TYPES[mimeType]
            ?: return@withContext ChallengeEvidenceReadResult.Failure(
                "Choose a JPEG, PNG, WebP, HEIC, or HEIF image.",
            )
        val reportedSize = runCatching {
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (!cursor.moveToFirst()) null else cursor.getLongOrNull(0)
                }
        }.getOrNull()
        if (reportedSize != null && reportedSize > MAX_EVIDENCE_BYTES) {
            return@withContext ChallengeEvidenceReadResult.Failure("Photo proof must be 6 MB or smaller.")
        }

        try {
            val bytes = resolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_EVIDENCE_BYTES) {
                        return@withContext ChallengeEvidenceReadResult.Failure(
                            "Photo proof must be 6 MB or smaller.",
                        )
                    }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } ?: return@withContext ChallengeEvidenceReadResult.Failure(
                "That photo could not be opened. Choose another image.",
            )
            if (bytes.isEmpty()) {
                ChallengeEvidenceReadResult.Failure("That photo is empty. Choose another image.")
            } else {
                ChallengeEvidenceReadResult.Success(ChallengeEvidence(bytes, mimeType, extension))
            }
        } catch (_: SecurityException) {
            ChallengeEvidenceReadResult.Failure("Photo access expired. Choose the image again.")
        } catch (_: Exception) {
            ChallengeEvidenceReadResult.Failure("That photo could not be read. Choose another image.")
        }
    }

    private companion object {
        const val MAX_EVIDENCE_BYTES = 6 * 1024 * 1024
        val SUPPORTED_IMAGE_TYPES = mapOf(
            "image/jpeg" to "jpg",
            "image/png" to "png",
            "image/webp" to "webp",
            "image/heic" to "heic",
            "image/heif" to "heif",
        )
    }
}
