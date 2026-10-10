package com.ahmad.nafs.ui.components

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ahmad.nafs.ui.theme.Nafs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * A short burst of confetti and sparkles (about 1.5 s) drawn over its bounds each time [trigger] changes after
 * the first composition. It never plays on first render, so opening or rotating a screen can't replay it, and
 * it draws nothing at all when the system animator duration scale is 0 (animations turned off).
 *
 * It has no semantics and takes no touches; pair it with a visible state change and haptics.
 */
@Composable
fun NafsCelebration(trigger: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val c = Nafs.colors
    val palette = listOf(c.gold, c.tasks, c.habits, c.challenges, c.health, c.deen)
    val baseline = remember { trigger }
    var burst by remember { mutableIntStateOf(baseline) }
    var playing by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(trigger) {
        if (trigger == baseline) return@LaunchedEffect
        val scale = coroutineContext[MotionDurationScale]?.scaleFactor
        if (scale == 0f || animationsOff(context)) return@LaunchedEffect
        burst = trigger
        playing = true
        try {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(DURATION_MS, easing = LinearEasing))
        } finally {
            playing = false
        }
    }

    if (!playing) return
    val particles = remember(burst) { particlesFor(burst) }
    // Progress is read while drawing only, so the burst redraws each frame without recomposing.
    Canvas(modifier.fillMaxSize()) { drawBurst(particles, progress.value, palette) }
}

private fun animationsOff(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

private const val DURATION_MS = 1_500
private const val PARTICLES = 40
private const val CONFETTI = 0
private const val DOT = 1
private const val SPARKLE = 2

private class Particle(
    /** Launch direction in radians; mostly upwards. */
    val angle: Float,
    /** Share of the burst's reach this piece travels. */
    val speed: Float,
    val sizeDp: Float,
    /** Turns over the whole burst, for confetti. */
    val spin: Float,
    val colorIndex: Int,
    val shape: Int,
    /** Share of the timeline before this piece appears, so the burst has depth. */
    val delay: Float,
)

/** The same burst for the same trigger, so recomposition never reshuffles the pieces. */
private fun particlesFor(seed: Int): List<Particle> {
    val random = Random(seed * 7_919 + 17)
    return List(PARTICLES) { index ->
        val shape = when {
            index % 5 == 0 -> SPARKLE
            index % 4 == 0 -> DOT
            else -> CONFETTI
        }
        Particle(
            angle = (-PI / 2 + (random.nextFloat() - 0.5f) * PI * 0.85).toFloat(),
            speed = 0.45f + random.nextFloat() * 0.6f,
            sizeDp = if (shape == SPARKLE) 9f + random.nextFloat() * 5f else 5f + random.nextFloat() * 4f,
            spin = (random.nextFloat() - 0.5f) * 3f,
            colorIndex = random.nextInt(0, 64),
            shape = shape,
            delay = random.nextFloat() * 0.12f,
        )
    }
}

private fun DrawScope.drawBurst(particles: List<Particle>, time: Float, palette: List<Color>) {
    val origin = Offset(size.width / 2f, size.height * 0.3f)
    val reach = min(size.width, size.height) * 0.62f
    particles.forEach { piece ->
        val local = ((time - piece.delay) / (1f - piece.delay)).coerceIn(0f, 1f)
        if (local <= 0f) return@forEach
        // Thrown out fast, then gravity takes over and the piece drifts down as it fades.
        val out = 1f - (1f - local) * (1f - local) * (1f - local)
        val travel = reach * piece.speed * out
        val position = origin + Offset(
            x = cos(piece.angle) * travel,
            y = sin(piece.angle) * travel + reach * 0.85f * local * local,
        )
        val alpha = if (local < 0.6f) 1f else 1f - (local - 0.6f) / 0.4f
        val color = palette[piece.colorIndex % palette.size].copy(alpha = alpha.coerceIn(0f, 1f))
        val pieceSize = piece.sizeDp.dp.toPx()
        when (piece.shape) {
            CONFETTI -> rotate(degrees = piece.spin * 360f * local, pivot = position) {
                drawRoundRect(
                    color = color,
                    topLeft = position - Offset(pieceSize / 2f, pieceSize / 4f),
                    size = Size(pieceSize, pieceSize / 2f),
                    cornerRadius = CornerRadius(pieceSize / 6f),
                )
            }
            DOT -> drawCircle(color = color, radius = pieceSize / 2.6f, center = position)
            else -> {
                // A four-point twinkle that shrinks as it fades.
                val arm = pieceSize * (1f - local * 0.5f) / 2f
                val stroke = (pieceSize / 5f).coerceAtLeast(1f)
                drawLine(color, position - Offset(arm, 0f), position + Offset(arm, 0f), stroke, StrokeCap.Round)
                drawLine(color, position - Offset(0f, arm), position + Offset(0f, arm), stroke, StrokeCap.Round)
            }
        }
    }
}
