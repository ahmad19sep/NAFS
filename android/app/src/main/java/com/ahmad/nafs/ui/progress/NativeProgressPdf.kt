package com.ahmad.nafs.ui.progress

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object NativeProgressPdf {
    private fun pages(lines: List<String>): List<List<String>> {
        val paint = Paint().apply { textSize = 11f }
        val wrapped = lines.flatMap { line ->
            buildList {
                var rest = line
                if (rest.isEmpty()) add("")
                while (rest.isNotEmpty()) {
                    val count = paint.breakText(rest, true, 505f, null).coerceAtLeast(1)
                    val boundary = if (count < rest.length) rest.lastIndexOf(' ', count).takeIf { it > count / 2 } ?: count else count
                    add(rest.take(boundary)); rest = rest.drop(boundary).trimStart()
                }
            }
        }
        return wrapped.chunked(45).ifEmpty { listOf(listOf("No recorded data.")) }
    }
    fun write(lines: List<String>, output: OutputStream, ranges: Array<PageRange>? = null) {
        val document = PdfDocument()
        try {
            val pages = pages(lines)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = Color.BLACK }
            pages.forEachIndexed { index, linesOnPage ->
                if (ranges != null && ranges.none { index in it.start..it.end }) return@forEachIndexed
                val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, index + 1).create())
                linesOnPage.forEachIndexed { line, text -> page.canvas.drawText(text, 45f, 55f + line * 16f, paint) }
                page.canvas.drawText("NAFS · Page ${index + 1} of ${pages.size}", 45f, 805f, paint)
                document.finishPage(page)
            }
            document.writeTo(output)
        } finally {
            document.close()
        }
    }
    suspend fun share(context: Context, lines: List<String>) {
        val file = withContext(Dispatchers.IO) {
            val directory = File(context.cacheDir, "reports").apply { mkdirs() }
            directory.listFiles()?.filter { it.isFile && it.lastModified() < System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L }?.forEach { it.delete() }
            File(directory, "NAFS-progress-${java.util.UUID.randomUUID()}.pdf").also { FileOutputStream(it).use { output -> write(lines, output) } }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newRawUri("NAFS report", uri)
        }, "Share NAFS report"))
    }
    fun print(context: Context, lines: List<String>) {
        val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        manager.print("NAFS progress", object : PrintDocumentAdapter() {
            override fun onLayout(old: PrintAttributes?, new: PrintAttributes?, cancellation: CancellationSignal?, callback: LayoutResultCallback, extras: Bundle?) {
                if (cancellation?.isCanceled == true) { callback.onLayoutCancelled(); return }
                callback.onLayoutFinished(PrintDocumentInfo.Builder("NAFS-progress.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(pages(lines).size).build(), old != new)
            }
            override fun onWrite(ranges: Array<PageRange>, destination: ParcelFileDescriptor, cancellation: CancellationSignal?, callback: WriteResultCallback) {
                if (cancellation?.isCanceled == true) { callback.onWriteCancelled(); return }
                try { FileOutputStream(destination.fileDescriptor).use { write(lines, it, ranges) }; callback.onWriteFinished(ranges) }
                catch (_: Exception) { callback.onWriteFailed("Unable to print this report.") }
            }
        }, PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
    }
}
