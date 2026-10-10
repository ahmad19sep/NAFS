package com.ahmad.nafs

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.print.PageRange
import androidx.test.platform.app.InstrumentationRegistry
import com.ahmad.nafs.ui.progress.NativeProgressPdf
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class ReportExportTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun report(name: String, ranges: Array<PageRange>? = null): File {
        val file = File(context.filesDir, name)
        file.outputStream().use { NativeProgressPdf.write(listOf("NAFS TEST REPORT - synthetic data", "No account records used.") +
            (1..100).map { "Test day $it: Pending - no recorded data" }, it, ranges) }
        return file
    }
    @Test fun reportExportsThreeReadableA4Pages() {
        val file = report("report-validation.pdf")
        PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
            assertEquals(3, renderer.pageCount)
            renderer.openPage(0).use { page ->
                assertEquals(595, page.width); assertEquals(842, page.height)
                val image = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                image.eraseColor(Color.WHITE)
                page.render(image, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                File(context.filesDir, "report-validation.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
                image.recycle()
            }
        }
    }
    @Test fun printingHonorsTheSelectedPageRange() {
        val file = report("report-range-validation.pdf", arrayOf(PageRange(1, 1)))
        PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer -> assertEquals(1, renderer.pageCount) }
    }
}
