package com.ahmad.nafs.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import java.net.URI
import java.net.HttpURLConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Public images use a separate connection without session headers or disk persistence. */
@Composable
fun PublicPhoto(url: String?, description: String, modifier: Modifier = Modifier) {
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(url) { mutableStateOf(false) }
    LaunchedEffect(url) {
        if (url.isNullOrBlank()) return@LaunchedEffect
        try {
            bitmap = withContext(Dispatchers.IO) {
                val uri = URI(url); require(uri.scheme == "https" && uri.userInfo == null && uri.host != null)
                val connection = uri.toURL().openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = false; connection.connectTimeout = 10_000; connection.readTimeout = 15_000
                try {
                    require(connection.responseCode == 200)
                    val bytes = connection.inputStream.use { input ->
                        val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                        while (true) { val count = input.read(buffer); if (count < 0) break; require(output.size() + count <= 6 * 1024 * 1024); output.write(buffer, 0, count) }
                        output.toByteArray()
                    }
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    require(bounds.outWidth in 1..30000 && bounds.outHeight in 1..30000)
                    val options = BitmapFactory.Options().apply { inSampleSize = generateSequence(1) { it * 2 }.first { bounds.outWidth / it <= 1024 && bounds.outHeight / it <= 1024 } }
                    checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)).asImageBitmap()
                } finally { connection.disconnect() }
            }
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { failed = true }
    }
    bitmap?.let { Image(it, description, modifier, contentScale = ContentScale.Fit) }
    if (failed) Text("Photo unavailable")
}
