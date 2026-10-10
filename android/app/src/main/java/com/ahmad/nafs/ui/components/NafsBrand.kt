package com.ahmad.nafs.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahmad.nafs.ui.theme.AmiriArabic
import com.ahmad.nafs.ui.theme.Gold200
import com.ahmad.nafs.ui.theme.Gold500

/** The NAFS mark: نفس in gold calligraphy on the evening gradient. */
@Composable
fun NafsBrandMark(modifier: Modifier = Modifier, size: Dp = 84.dp) {
    val shape = RoundedCornerShape(size * 0.3f)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF14423F), Color(0xFF07161B))))
            .border(1.dp, Gold200.copy(alpha = 0.22f), shape)
            .semantics { contentDescription = "NAFS" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "نفس",
            style = TextStyle(
                fontFamily = AmiriArabic,
                fontSize = (size.value * 0.42f).sp,
                brush = Brush.verticalGradient(listOf(Gold200, Gold500)),
            ),
        )
    }
}

/** Mark with the wordmark and tagline underneath, for sign-in and loading. */
@Composable
fun NafsBrandLockup(modifier: Modifier = Modifier, tagline: String? = "Build a life you can trust.", markSize: Dp = 84.dp) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NafsBrandMark(size = markSize)
        Text("NAFS", style = MaterialTheme.typography.headlineMedium.copy(letterSpacing = 6.sp))
        tagline?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
