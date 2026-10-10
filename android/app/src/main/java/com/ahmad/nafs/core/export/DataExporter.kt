package com.ahmad.nafs.core.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.ahmad.nafs.BuildConfig
import com.ahmad.nafs.core.network.SupabaseClientProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Count
import io.github.jan.supabase.postgrest.query.Order
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

sealed interface DataExportResult {
    data class Ready(val file: File, val sizeBytes: Long, val summary: ExportSummary) : DataExportResult

    data class Failed(val message: String) : DataExportResult
}

/**
 * "Download my data": reads every table in [ExportTables] for the signed-in user through the Supabase client
 * (row-level security applies) and writes one JSON file to `cacheDir/exports/nafs-export-<date>.json`, which
 * [ExportShare] hands to the system share sheet. Only the newest export is kept on the phone.
 */
@Singleton
class DataExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val provider: SupabaseClientProvider,
) {
    private val directory: File get() = File(context.cacheDir, EXPORT_DIRECTORY)

    suspend fun export(userId: String, onProgress: (ExportProgress) -> Unit): DataExportResult = withContext(Dispatchers.IO) {
        val client = provider.client ?: return@withContext DataExportResult.Failed("NAFS isn't connected to its server in this build.")
        if (client.auth.currentUserOrNull()?.id != userId) return@withContext DataExportResult.Failed(SIGN_IN_AGAIN)
        val file = try {
            prepareFile()
        } catch (_: IOException) {
            return@withContext DataExportResult.Failed(STORAGE)
        }
        try {
            val summary = file.bufferedWriter().use { writer ->
                ExportRunner(SupabaseExportSource(client))
                    .run(userId, ExportTables.all, Instant.now(), BuildConfig.VERSION_NAME, writer, onProgress)
            }
            if (client.auth.currentUserOrNull()?.id != userId) {
                // The account changed while the export ran; don't hand over a file that mixes accounts.
                file.delete()
                return@withContext DataExportResult.Failed(SIGN_IN_AGAIN)
            }
            DataExportResult.Ready(file, file.length(), summary)
        } catch (cancellation: CancellationException) {
            file.delete()
            throw cancellation
        } catch (error: ExportFailedException) {
            file.delete()
            DataExportResult.Failed(error.message)
        } catch (_: ExportConnectionException) {
            file.delete()
            DataExportResult.Failed("Your connection dropped before the export finished. Check it and try again.")
        } catch (_: IOException) {
            file.delete()
            DataExportResult.Failed(STORAGE)
        } catch (_: Exception) {
            file.delete()
            DataExportResult.Failed("The export didn't finish. Try again.")
        }
    }

    /** Deletes earlier exports. Each holds a full copy of the account's records, so none is kept longer than needed. */
    suspend fun clearExports() = withContext(Dispatchers.IO) {
        directory.listFiles()?.forEach { it.delete() }
        Unit
    }

    private fun prepareFile(): File {
        val folder = directory
        folder.listFiles()?.forEach { it.delete() }
        if (!folder.isDirectory && !folder.mkdirs()) throw IOException("The export folder could not be created.")
        return File(folder, "nafs-export-${LocalDate.now()}.json")
    }

    private companion object {
        const val EXPORT_DIRECTORY = "exports"
        const val SIGN_IN_AGAIN = "Sign in again, then download your data."
        const val STORAGE = "This phone couldn't save the export. Free up some space and try again."
    }
}

/** Opens the system share sheet for a finished export, through the app's FileProvider (`res/xml/share_paths.xml`). */
object ExportShare {
    fun chooser(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "My NAFS data")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri(file.name, uri)
        }
        return Intent.createChooser(send, "Save or share your NAFS data")
    }
}

private class SupabaseExportSource(private val client: SupabaseClient) : ExportSource {
    override suspend fun page(table: ExportTable, ownerValues: List<String>, from: Long, to: Long, withCount: Boolean): ExportPage {
        val result = try {
            client.from(table.name).select {
                if (withCount) count(Count.EXACT)
                filter {
                    if (ownerValues.size == 1) eq(table.ownerColumn, ownerValues.single()) else isIn(table.ownerColumn, ownerValues)
                }
                table.orderColumns.forEach { order(it, Order.ASCENDING) }
                range(from, to)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: RestException) {
            throw ExportTableUnavailableException(unavailableReason(error.statusCode))
        } catch (error: Exception) {
            throw ExportConnectionException(error)
        }
        val rows = try {
            result.decodeList<JsonObject>()
        } catch (_: Exception) {
            throw ExportTableUnavailableException("The server sent rows NAFS couldn't read.")
        }
        return ExportPage(rows, if (withCount) result.countOrNull() else null)
    }

    private fun unavailableReason(status: Int): String = when (status) {
        401, 403 -> "NAFS isn't allowed to read this table."
        404 -> "This table isn't set up on the server."
        400 -> "This table is set up differently on the server."
        else -> "The server couldn't return it (error $status)."
    }
}
