package com.ahmad.nafs.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.ahmad.nafs.core.preferences.ThemeMode

private val DarkColors = darkColorScheme(
    primary = Emerald300,
    onPrimary = Emerald900,
    primaryContainer = EmeraldDeep,
    onPrimaryContainer = Color(0xFFB4F4E5),
    inversePrimary = Emerald600,
    secondary = Gold300,
    onSecondary = Color(0xFF3B2A00),
    secondaryContainer = Gold800,
    onSecondaryContainer = Gold200,
    tertiary = Sky300,
    onTertiary = Color(0xFF0F2551),
    tertiaryContainer = SkyDeep,
    onTertiaryContainer = SkyMist,
    background = Night900,
    onBackground = NightText,
    surface = Night900,
    onSurface = NightText,
    surfaceVariant = Night700,
    onSurfaceVariant = NightMuted,
    surfaceTint = Emerald300,
    inverseSurface = NightText,
    inverseOnSurface = Night900,
    error = Rose300,
    onError = Color(0xFF55101B),
    errorContainer = Color(0xFF4A1820),
    onErrorContainer = Color(0xFFFFDADC),
    outline = Night500,
    outlineVariant = Night650,
    scrim = Color.Black,
    surfaceBright = Night650,
    surfaceDim = Night950,
    surfaceContainerLowest = Night950,
    surfaceContainerLow = Night850,
    surfaceContainer = Night800,
    surfaceContainerHigh = Night750,
    surfaceContainerHighest = Night700,
)

private val LightColors = lightColorScheme(
    primary = Emerald600,
    onPrimary = Color.White,
    primaryContainer = EmeraldMist,
    onPrimaryContainer = Emerald900,
    inversePrimary = Emerald300,
    secondary = Gold600,
    onSecondary = Color.White,
    secondaryContainer = GoldMist,
    onSecondaryContainer = Color(0xFF2C1F00),
    tertiary = Sky600,
    onTertiary = Color.White,
    tertiaryContainer = SkyMist,
    onTertiaryContainer = Color(0xFF0D1C3E),
    background = Paper100,
    onBackground = Ink,
    surface = Paper100,
    onSurface = Ink,
    surfaceVariant = Paper300,
    onSurfaceVariant = InkMuted,
    surfaceTint = Emerald600,
    inverseSurface = Color(0xFF233532),
    inverseOnSurface = Paper100,
    error = Rose600,
    onError = Color.White,
    errorContainer = Color(0xFFFFDADC),
    onErrorContainer = Color(0xFF410011),
    outline = Color(0xFFB9BDB5),
    outlineVariant = Paper400,
    scrim = Color.Black,
    surfaceBright = Paper50,
    surfaceDim = Paper300,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Paper50,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Paper200,
    surfaceContainerHighest = Paper300,
)

/** Colours Material 3 has no role for: status, gold, the hero surface and per-feature accents. */
@Immutable
data class NafsColors(
    val isDark: Boolean,
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val gold: Color,
    val goldSoft: Color,
    val heroTop: Color,
    val heroBottom: Color,
    val heroGlow: Color,
    val onHero: Color,
    val onHeroMuted: Color,
    /** Accents that stay legible on the hero gradient in both themes. */
    val heroGold: Color,
    val heroMint: Color,
    val heroSky: Color,
    val cardBorder: Color,
    val tasks: Color,
    val habits: Color,
    val goals: Color,
    val challenges: Color,
    val health: Color,
    val deen: Color,
    val coach: Color,
    val reflect: Color,
)

private val DarkNafsColors = NafsColors(
    isDark = true,
    success = Success300,
    successContainer = Color(0xFF113A27),
    onSuccessContainer = Color(0xFFB9F5CF),
    warning = Warning300,
    warningContainer = Color(0xFF45300D),
    onWarningContainer = Color(0xFFFFE0B5),
    gold = Gold300,
    goldSoft = Gold300.copy(alpha = 0.14f),
    heroTop = Color(0xFF123B3A),
    heroBottom = Color(0xFF0A1E26),
    heroGlow = Gold400.copy(alpha = 0.22f),
    onHero = Color(0xFFF4F8F6),
    onHeroMuted = Color(0xFFB9CCC8),
    heroGold = Gold300,
    heroMint = Emerald300,
    heroSky = AccentTasksDark,
    cardBorder = Color.White.copy(alpha = 0.07f),
    tasks = AccentTasksDark,
    habits = Emerald300,
    goals = Gold300,
    challenges = AccentChallengesDark,
    health = AccentHealthDark,
    deen = AccentDeenDark,
    coach = AccentCoachDark,
    reflect = Gold300,
)

private val LightNafsColors = NafsColors(
    isDark = false,
    success = Success600,
    successContainer = Color(0xFFD6F5E1),
    onSuccessContainer = Color(0xFF00391D),
    warning = Warning600,
    warningContainer = Color(0xFFFFE6C4),
    onWarningContainer = Color(0xFF3A2000),
    gold = Gold600,
    goldSoft = Gold400.copy(alpha = 0.18f),
    heroTop = Color(0xFF0F6458),
    heroBottom = Color(0xFF0A3F3D),
    heroGlow = Gold300.copy(alpha = 0.30f),
    onHero = Color(0xFFFFFFFF),
    onHeroMuted = Color(0xFFCDE6DF),
    heroGold = Gold300,
    heroMint = Emerald300,
    heroSky = AccentTasksDark,
    cardBorder = Color(0xFF10201E).copy(alpha = 0.07f),
    tasks = AccentTasksLight,
    habits = Emerald600,
    goals = Gold600,
    challenges = AccentChallengesLight,
    health = AccentHealthLight,
    deen = AccentDeenLight,
    coach = AccentCoachLight,
    reflect = Gold600,
)

val LocalNafsColors = staticCompositionLocalOf { DarkNafsColors }

/** Access point for NAFS-specific tokens: `Nafs.colors.gold`. */
object Nafs {
    val colors: NafsColors
        @Composable @ReadOnlyComposable get() = LocalNafsColors.current
}

val NafsShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun NafsTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalNafsColors provides if (darkTheme) DarkNafsColors else LightNafsColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = NafsTypography,
            shapes = NafsShapes,
            content = content,
        )
    }
}
