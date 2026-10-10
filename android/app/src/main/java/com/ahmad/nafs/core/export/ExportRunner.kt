package com.ahmad.nafs.core.export

import java.io.Writer
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

data class ExportPage(val rows: List<JsonObject>, val total: Long?)

/**
 * Reads the export's rows. Implementations throw [ExportTableUnavailableException] for a table that can't be read
 * (missing on the server, no permission) and [ExportConnectionException] when the server can't be reached at all.
 */
interface ExportSource {
    /** Rows [from]..[to] (inclusive) of [table] whose owner column is one of [ownerValues]; [withCount] asks for the total. */
    suspend fun page(table: ExportTable, ownerValues: List<String>, from: Long, to: Long, withCount: Boolean): ExportPage
}

class ExportTableUnavailableException(val reason: String) : Exception(reason)

class ExportConnectionException(cause: Throwable? = null) : Exception("The server could not be reached.", cause)

/** The export can't be useful, for example because even the profile couldn't be read. */
class ExportFailedException(override val message: String) : Exception(message)

data class ExportProgress(val tablesDone: Int, val tablesTotal: Int, val currentLabel: String, val rowsSoFar: Long) {
    val fraction: Float get() = if (tablesTotal == 0) 0f else tablesDone.toFloat() / tablesTotal
}

data class UnavailableTable(val table: String, val label: String, val reason: String)

data class ExportSummary(val rowCounts: Map<String, Int>, val unavailable: List<UnavailableTable>) {
    val totalRows: Long get() = rowCounts.values.sumOf { it.toLong() }
}

/**
 * Writes one pretty-printed JSON document: when and by which app version it was made, then a section per table
 * with its rows, then the tables that couldn't be read and why. A table that fails is listed under "unavailable"
 * instead of failing the export; losing the connection fails it, because the rest would be missing too.
 */
class ExportRunner(private val source: ExportSource, private val pageSize: Int = PAGE_SIZE) {
    private val json = Json { prettyPrint = true }

    suspend fun run(
        userId: String,
        tables: List<ExportTable>,
        exportedAt: Instant,
        appVersion: String,
        out: Writer,
        onProgress: (ExportProgress) -> Unit = {},
    ): ExportSummary {
        val parents = tables.mapNotNullTo(mutableSetOf()) { it.parent }
        val idsByTable = mutableMapOf<String, List<String>>()
        val counts = linkedMapOf<String, Int>()
        val unavailable = mutableListOf<UnavailableTable>()
        var rowsSoFar = 0L

        out.write("{\n")
        writeField(out, "app", JsonPrimitive("NAFS"))
        writeField(out, "format_version", JsonPrimitive(FORMAT_VERSION))
        writeField(out, "exported_at", JsonPrimitive(exportedAt.toString()))
        writeField(out, "app_version", JsonPrimitive(appVersion))
        writeField(out, "user_id", JsonPrimitive(userId))
        out.write("    \"tables\": {")

        tables.forEachIndexed { index, table ->
            onProgress(ExportProgress(index, tables.size, table.label, rowsSoFar))
            val owners = when (val parent = table.parent) {
                null -> listOf(userId)
                else -> idsByTable[parent]
            }
            if (owners == null) {
                val parentLabel = tables.firstOrNull { it.name == table.parent }?.label ?: table.parent
                unavailable += UnavailableTable(table.name, table.label, "Needs $parentLabel, which couldn't be read.")
                return@forEachIndexed
            }
            val rows = try {
                read(table, owners)
            } catch (error: ExportTableUnavailableException) {
                if (table.required) {
                    throw ExportFailedException("NAFS couldn't read your ${table.label.lowercase()}, so nothing was exported. Sign in again and retry.")
                }
                unavailable += UnavailableTable(table.name, table.label, error.reason)
                return@forEachIndexed
            }
            if (table.name in parents) {
                idsByTable[table.name] = rows.mapNotNull { (it["id"] as? JsonPrimitive)?.contentOrNull }
            }
            val section = buildJsonObject {
                put("label", table.label)
                put("row_count", rows.size)
                put("rows", JsonArray(rows))
            }
            out.write(if (counts.isEmpty()) "\n" else ",\n")
            out.write("        ${JsonPrimitive(table.name)}: ")
            out.write(indent(json.encodeToString(JsonObject.serializer(), section), 8))
            counts[table.name] = rows.size
            rowsSoFar += rows.size
        }

        out.write(if (counts.isEmpty()) "},\n" else "\n    },\n")
        val missing = JsonArray(
            unavailable.map {
                buildJsonObject {
                    put("table", it.table)
                    put("label", it.label)
                    put("reason", it.reason)
                }
            },
        )
        out.write("    \"unavailable\": ${indent(json.encodeToString(JsonArray.serializer(), missing), 4)}\n")
        out.write("}\n")
        out.flush()
        onProgress(ExportProgress(tables.size, tables.size, "", rowsSoFar))
        return ExportSummary(counts, unavailable)
    }

    private suspend fun read(table: ExportTable, owners: List<String>): List<JsonObject> {
        val rows = mutableListOf<JsonObject>()
        for (chunk in owners.chunked(OWNER_CHUNK)) {
            var from = 0L
            var total: Long? = null
            while (true) {
                val page = source.page(table, chunk, from, from + pageSize - 1, withCount = from == 0L)
                if (from == 0L) total = page.total
                if (page.rows.isEmpty()) break
                page.rows.mapTo(rows) { row -> if (table.redactedColumns.isEmpty()) row else JsonObject(row - table.redactedColumns) }
                from += page.rows.size
                if (rows.size > MAX_ROWS_PER_TABLE) throw ExportTableUnavailableException("Too many rows to export from the phone.")
                // Without a total, keep going until an empty page: the server may return fewer rows than asked for.
                val known = total
                if (known != null && from >= known) break
            }
        }
        return rows
    }

    private fun writeField(out: Writer, name: String, value: JsonPrimitive) {
        out.write("    ${JsonPrimitive(name)}: $value,\n")
    }

    /** Indents every line after the first; JSON strings never contain raw line breaks, so this is safe. */
    private fun indent(text: String, spaces: Int): String {
        val pad = " ".repeat(spaces)
        return text.lineSequence().mapIndexed { index, line -> if (index == 0) line else pad + line }.joinToString("\n")
    }

    companion object {
        const val FORMAT_VERSION = 1
        const val PAGE_SIZE = 1000
        /** Ids per `in (...)` filter, which keeps request URLs short. */
        const val OWNER_CHUNK = 50
        const val MAX_ROWS_PER_TABLE = 500_000
    }
}
