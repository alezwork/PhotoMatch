package com.photomatch.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.photomatch.ui.theme.Pm
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Spectrum colour at position t in [0,1] (wraps). */
fun spectrumAt(t: Float): Color {
    val c = Pm.spectrum
    val x = ((t % 1f) + 1f) % 1f * (c.size - 1)
    val i = x.toInt().coerceAtMost(c.size - 2)
    return lerp(c[i], c[i + 1], x - i)
}

/**
 * Soft lavender backdrop with a warm blurred glow — the "app icon" tile look.
 * Drawn with radial gradients only (no RenderEffect), so it is cheap everywhere.
 */
@Composable
fun AuroraBackdrop(modifier: Modifier = Modifier, base: Color = Pm.Lavender, animate: Boolean = true) {
    val t = if (animate) {
        val inf = rememberInfiniteTransition(label = "aurora")
        inf.animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse), label = "a").value
    } else 0.3f
    Box(modifier.drawBehind {
        drawRect(base)
        val w = size.width; val h = size.height
        fun blob(cx: Float, cy: Float, r: Float, color: Color, a: Float) = drawCircle(
            Brush.radialGradient(listOf(color.copy(alpha = a), color.copy(alpha = 0f)), Offset(cx, cy), r),
            r, Offset(cx, cy),
        )
        blob(w * (0.78f - 0.1f * t), h * (0.92f), maxOf(w, h) * 0.55f, Pm.Blush, 0.85f)
        blob(w * (0.95f), h * (0.55f + 0.1f * t), maxOf(w, h) * 0.35f, Pm.Peach, 0.55f)
        blob(w * (0.15f + 0.1f * t), h * 0.1f, maxOf(w, h) * 0.45f, Pm.Sky, 0.25f)
    })
}

/**
 * Progress drawn as a drink filling a glass: an iridescent liquid with two
 * moving wave layers, a bright meniscus line, rising bubbles and a slosh that
 * kicks in every time [fraction] changes and then calms down.
 */
@Composable
fun LiquidFill(
    fraction: Float,
    modifier: Modifier = Modifier,
    colors: List<Color> = Pm.spectrum,
    minLevel: Float = 0.035f,
    bubbles: Int = 12,
    glass: Boolean = true,
    alpha: Float = 1f,
) {
    val level by androidx.compose.animation.core.animateFloatAsState(
        fraction.coerceIn(0f, 1f),
        androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 60f),
        label = "level",
    )
    val slosh = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0.6f) }
    androidx.compose.runtime.LaunchedEffect(fraction) {
        slosh.snapTo(1f)
        slosh.animateTo(0.28f, tween(2600, easing = androidx.compose.animation.core.FastOutSlowInEasing))
    }
    val inf = rememberInfiniteTransition(label = "liquid")
    val phase by inf.animateFloatAsState2(3200)
    val rise by inf.animateFloatAsState2(5200)
    val seeds = androidx.compose.runtime.remember(bubbles) {
        val r = kotlin.random.Random(11)
        List(bubbles) { floatArrayOf(r.nextFloat(), 0.5f + r.nextFloat(), r.nextFloat(), 0.6f + r.nextFloat() * 1.4f) }
    }
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val lv = (minLevel + (1f - minLevel) * level)
        val surface = h * (1f - lv)
        val amp = (6.dp.toPx() * slosh.value).coerceAtMost(h * 0.08f) * (if (lv > 0.985f) 0.2f else 1f)
        val tau = (2 * PI).toFloat()

        fun wave(ph: Float, k: Float, a: Float): Path = Path().apply {
            moveTo(0f, h)
            val step = 4.dp.toPx()
            var x = 0f
            lineTo(0f, surface + a * sin(ph))
            while (x <= w + step) {
                val y = surface + a * sin(x / w * tau * k + ph) + a * 0.35f * sin(x / w * tau * k * 2.3f - ph * 1.7f)
                lineTo(x, y)
                x += step
            }
            lineTo(w, h); close()
        }

        val p = phase * tau
        // back layer: deeper tone, opposite direction
        drawPath(
            wave(-p * 1.3f + 1.7f, 1.1f, amp * 1.15f),
            Brush.horizontalGradient(colors.map { lerp(it, Pm.Dusk, 0.45f).copy(alpha = 0.75f * alpha) }),
        )
        // front layer
        val front = wave(p, 1.4f, amp)
        drawPath(front, Brush.horizontalGradient(colors.map { it.copy(alpha = 0.92f * alpha) }))
        // depth: darker toward the bottom of the glass
        drawPath(front, Brush.verticalGradient(listOf(Color.Transparent, Color(0x66000000)), startY = surface, endY = h))
        // meniscus line
        val line = Path().apply {
            val step = 4.dp.toPx(); var x = 0f
            while (x <= w + step) {
                val y = surface + amp * sin(x / w * tau * 1.4f + p) + amp * 0.35f * sin(x / w * tau * 1.4f * 2.3f - p * 1.7f)
                if (x == 0f) moveTo(x, y) else lineTo(x, y)
                x += step
            }
        }
        drawPath(line, Color.White.copy(alpha = 0.55f * alpha), style = Stroke(1.6.dp.toPx()))
        // bubbles
        val depth = h - surface
        if (depth > 10.dp.toPx()) seeds.forEach { s ->
            val t = (rise * s[3] + s[2]) % 1f
            val y = h - t * depth
            val x = w * s[0] + 6.dp.toPx() * sin(t * tau * 2f + s[2] * 6f)
            val r = 1.4.dp.toPx() * s[1]
            if (y > surface + amp + r) drawCircle(Color.White.copy(alpha = 0.35f * (1f - t * 0.6f) * alpha), r, Offset(x, y))
        }
        if (glass) {
            // glass reflections
            drawRect(
                Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), startX = 0f, endX = w * 0.18f),
            )
            drawRect(
                Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.06f)), startX = w * 0.86f, endX = w),
            )
        }
    }
}

@Composable
private fun androidx.compose.animation.core.InfiniteTransition.animateFloatAsState2(ms: Int) =
    animateFloat(0f, 1f, infiniteRepeatable(tween(ms, easing = LinearEasing)), label = "t$ms")

/** A small round "glass" with liquid and a percentage — used in top bars and tiles. */
@Composable
fun LiquidBadge(fraction: Float, modifier: Modifier = Modifier, label: String? = null) {
    Box(
        modifier
            .clip(androidx.compose.foundation.shape.CircleShape)
            .drawBehind { drawRect(Pm.Surface2) },
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        LiquidFill(fraction, Modifier.matchParentSize(), bubbles = 5, minLevel = 0.08f)
        androidx.compose.material3.Text(
            label ?: "${(fraction * 100).toInt()}%",
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium.copy(
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                shadow = androidx.compose.ui.graphics.Shadow(Color(0x99000000), blurRadius = 6f),
            ),
            color = Color.White,
        )
    }
}
