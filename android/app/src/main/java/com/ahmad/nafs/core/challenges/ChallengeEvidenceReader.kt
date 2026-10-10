package com.ahmad.nafs.core.challenges

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.database.getLongOrNull
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface ChallengeEvidenceReadResult {
    data class Success(val evidence: ChallengeEvidence) : ChallengeEvidenceReadResult
    data class Failure(val message: String) : ChallengeEvidenceReadResult
}

/**
 * Reads a photo the user picked for upload (challenge proof, journal photos, avatar) and re-encodes it with
 * [PhotoSanitizer], so camera metadata such as the GPS location never leaves the phone.
 */
class ChallengeEvidenceReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun read(uri: Uri): ChallengeEvidenceReadResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri)?.lowercase()
            ?: return@withContext ChallengeEvidenceReadResult.Failure(
                "Choose a JPEG, PNG, WebP, HEIC, or HEIF image.",
            )
        if (mimeType !in SUPPORTED_IMAGE_TYPES) {
            return@withContext ChallengeEvidenceReadResult.Failure(
                "Choose a JPEG, PNG, WebP, HEIC, or HEIF image.",
            )
        }
        val reportedSize = runCatching {
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (!cursor.moveToFirst()) null else cursor.getLongOrNull(0)
                }
        }.getOrNull()
        if (reportedSize != null && reportedSize > MAX_INPUT_BYTES) {
            return@withContext ChallengeEvidenceReadResult.Failure(TOO_LARGE)
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
                    if (total > MAX_INPUT_BYTES) {
                        return@withContext ChallengeEvidenceReadResult.Failure(TOO_LARGE)
                    }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } ?: return@withContext ChallengeEvidenceReadResult.Failure(
                "That photo could not be opened. Choose another image.",
            )
            if (bytes.isEmpty()) {
                return@withContext ChallengeEvidenceReadResult.Failure("That photo is empty. Choose another image.")
            }
            val cleaned = PhotoSanitizer.reencode(bytes)
                ?: return@withContext ChallengeEvidenceReadResult.Failure(
                    "This phone can't open that photo format. Choose a JPEG or PNG image.",
                )
            ChallengeEvidenceReadResult.Success(cleaned)
        } catch (_: SecurityException) {
            ChallengeEvidenceReadResult.Failure("Photo access expired. Choose the image again.")
        } catch (_: OutOfMemoryError) {
            ChallengeEvidenceReadResult.Failure("That photo is too large to prepare. Choose a smaller image.")
        } catch (_: Exception) {
            ChallengeEvidenceReadResult.Failure("That photo could not be read. Choose another image.")
        }
    }

    private companion object {
        // Phone cameras can produce 15-20 MB files; the re-encoded upload is far smaller.
        const val MAX_INPUT_BYTES = 25 * 1024 * 1024
        const val TOO_LARGE = "Photos must be 25 MB or smaller."
        val SUPPORTED_IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif")
    }
}

/**
 * Decodes and re-encodes an image. The new file carries no EXIF block (no GPS location, device or time), is turned
 * upright using the camera's orientation tag, and its long edge is capped at [MAX_EDGE] pixels. Images with
 * transparency stay PNG; everything else becomes JPEG.
 */
internal object PhotoSanitizer {
    const val MAX_EDGE = 2048
    private const val JPEG_QUALITY = 85

    fun reencode(bytes: ByteArray, maxEdge: Int = MAX_EDGE): ChallengeEvidence? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val longEdge = max(bounds.outWidth, bounds.outHeight)
        var sample = 1
        // Decode close to the target size so a 48 MP photo never needs a full-size bitmap in memory.
        while (longEdge / sample > maxEdge * 5 / 4) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val rotation = orientationDegrees(bytes)
        val decodedEdge = max(decoded.width, decoded.height)
        val matrix = Matrix().apply {
            if (decodedEdge > maxEdge) (maxEdge.toFloat() / decodedEdge).let { postScale(it, it) }
            if (rotation != 0) postRotate(rotation.toFloat())
        }
        val upright = if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        val transparent = upright.hasAlpha()
        val output = ByteArrayOutputStream()
        val written = upright.compress(if (transparent) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        if (upright !== decoded) decoded.recycle()
        upright.recycle()
        if (!written || output.size() == 0) return null
        return if (transparent) ChallengeEvidence(output.toByteArray(), "image/png", "png")
        else ChallengeEvidence(output.toByteArray(), "image/jpeg", "jpg")
    }

    private fun orientationDegrees(bytes: ByteArray): Int = try {
        when (ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270
            else -> 0
        }
    } catch (_: Exception) {
        0
    }
}
