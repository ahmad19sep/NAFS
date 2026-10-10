package com.ahmad.nafs.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ahmad.nafs.R

/** Plus Jakarta Sans (SIL OFL 1.1): one family for every UI role, numbers included. */
val JakartaSans = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans_extrabold, FontWeight.ExtraBold),
)

/** Amiri (SIL OFL 1.1): the Arabic wordmark and Qur'anic text. */
val AmiriArabic = FontFamily(Font(R.font.amiri_bold, FontWeight.Bold))

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = JakartaSans,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

val NafsTypography = Typography(
    displayLarge = style(52, 58, FontWeight.ExtraBold, -1.6),
    displayMedium = style(44, 50, FontWeight.ExtraBold, -1.2),
    displaySmall = style(36, 42, FontWeight.ExtraBold, -0.9),
    headlineLarge = style(30, 36, FontWeight.Bold, -0.6),
    headlineMedium = style(26, 32, FontWeight.Bold, -0.45),
    headlineSmall = style(22, 28, FontWeight.Bold, -0.25),
    titleLarge = style(19, 25, FontWeight.Bold, -0.15),
    titleMedium = style(16, 22, FontWeight.SemiBold, -0.05),
    titleSmall = style(14, 20, FontWeight.SemiBold),
    bodyLarge = style(16, 24, FontWeight.Normal),
    bodyMedium = style(14, 21, FontWeight.Normal),
    bodySmall = style(12, 17, FontWeight.Normal, 0.1),
    labelLarge = style(14, 20, FontWeight.SemiBold, 0.1),
    labelMedium = style(12, 16, FontWeight.SemiBold, 0.2),
    labelSmall = style(11, 14, FontWeight.SemiBold, 0.5),
)
