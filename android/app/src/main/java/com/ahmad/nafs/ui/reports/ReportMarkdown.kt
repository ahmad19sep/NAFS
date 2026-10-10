package com.ahmad.nafs.ui.reports

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
fun ReportMarkdown(text: String) {
    val lines = remember(text) { ReportText.lines(text) }
    Column { lines.forEach { ReportLine(it) } }
}
