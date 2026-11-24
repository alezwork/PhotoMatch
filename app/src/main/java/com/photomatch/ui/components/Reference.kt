package com.photomatch.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photomatch.ui.theme.Pm
import com.photomatch.ui.theme.grain
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sin

// =============================================================================================
//  ASCII wave — the signature texture of the reference: a soft ridge drawn with digits and
//  "$" signs. Bright, dense glyphs on the crest in rainbow tones, sparse fringe above it with
//  a foggy glow, and a teal/blue field of 0/4/8 below.
// =============================================================================================

private const val RAMP = " .:1714004488$$"

private val glyphPaint by lazy {
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
}

private val fieldPaint by lazy { Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL } }
private val glowPaint by lazy {
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = android.graphics.Color.argb(90, 255, 255, 255)
    }
}

private fun hash(x: Int, y: Int): Float {
    var h = x * 374761393 + y * 668265263
    h = (h xor (h ushr 13)) * 1274126177
    return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
}

/**
 * @param amplitude ridge height as a fraction of the tile height
 * @param flip draws the field hanging from the top (for tiles with text at the bottom)
 * @param level where the ridge sits (0 = top, 1 = bottom), before flipping
 */
@Composable
fun DitherWave(
    modifier: Modifier = Modifier,
    phaseOffset: Float = 0f,
    amplitude: Float = 0.12f,
    flip: Boolean = false,
    level: Float = 0.55f,
) {
    // ~20 fps is plenty for a slow drift and keeps many tiles cheap.
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = System.nanoTime()
        while (true) {
            t = (System.nanoTime() - start) / 1e9f
            delay(50)
        }
    }
    Canvas(modifier) {
        val cw = 5.2.dp.toPx()
        val ch = 7.2.dp.toPx()
        glyphPaint.textSize = 6.6.dp.toPx()
        val cols = (size.width / cw).toInt() + 1
        val rows = (size.height / ch).toInt() + 1
        val phase = t * 0.35f + phaseOffset

        fun ridgeAt(x: Float): Float {
            val nx = x / size.width
            return size.height * (level + amplitude * sin(nx * 4.6f + phase) + amplitude * 0.45f * sin(nx * 9.3f - phase * 1.4f + 1.1f))
        }
        fun fy(y: Float) = if (flip) size.height - y else y

        // colour of the field at (nx, rows-below-crest) — big soft patches like a blurred photo
        fun fieldColor(nx: Float, d: Float): Color {
            if (d < 2.2f) return lerp(Color.White, Color(0xFFFFF4D6), (d / 2.2f).coerceIn(0f, 1f))
            if (d < 7f) {
                val k = ((d - 2.2f) / 4.8f).coerceIn(0f, 1f)
                val warm = lerp(Pm.Peach, Pm.Blush, k)
                return lerp(warm, Pm.Sky, (0.5f + 0.5f * sin(nx * 7f + phase * 0.8f)) * k * 0.6f)
            }
            val m = 0.5f + 0.5f * sin(nx * 4.2f + d * 0.22f - phase * 0.5f)
            val pink = (0.5f + 0.5f * sin(nx * 2.3f - d * 0.15f + 2f + phase * 0.3f)).let { (it - 0.62f).coerceAtLeast(0f) * 2.2f }
            return lerp(lerp(Color(0xFF8FB7E0), Pm.Mint, m), Pm.Blush, pink.coerceAtMost(0.8f))
        }

        // 1) soft glow along the crest + blurred colour field behind the glyphs (one shape each, no seams)
        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            val step = 3.dp.toPx()
            val ridgePath = android.graphics.Path()
            val field = android.graphics.Path()
            var x = -step
            field.moveTo(-step, fy(size.height + ch))
            while (x <= size.width + step) {
                val r = ridgeAt(x)
                if (x < 0f) ridgePath.moveTo(x, fy(r - ch * 1.2f)) else ridgePath.lineTo(x, fy(r - ch * 1.2f))
                field.lineTo(x, fy(r))
                x += step
            }
            field.lineTo(size.width + step, fy(size.height + ch)); field.close()

            val cols = IntArray(6) { k -> fieldColor(k / 5f, 4f).copy(alpha = 0.42f).toArgb() }
            fieldPaint.shader = android.graphics.LinearGradient(0f, 0f, size.width, 0f, cols, null, android.graphics.Shader.TileMode.CLAMP)
            fieldPaint.maskFilter = android.graphics.BlurMaskFilter(ch * 1.6f, android.graphics.BlurMaskFilter.Blur.NORMAL)
            nc.drawPath(field, fieldPaint)

            glowPaint.strokeWidth = ch * 4.5f
            glowPaint.maskFilter = android.graphics.BlurMaskFilter(ch * 3.2f, android.graphics.BlurMaskFilter.Blur.NORMAL)
            nc.drawPath(ridgePath, glowPaint)
        }

        // 2) glyphs
        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            for (cx in 0 until cols) {
                val x = cx * cw + cw / 2
                val ridge = ridgeAt(x)
                val nx = x / size.width
                for (cy in 0 until rows) {
                    val y0 = cy * ch + ch * 0.82f
                    val d = (y0 - ridge) / ch
                    val n = hash(cx, cy)
                    val intensity = when {
                        d < -4f -> continue
                        d < 0f -> { if (n > 0.62f + d * 0.14f) continue; 0.3f + 0.12f * (4f + d) }
                        d < 2.2f -> 1f
                        d < 7f -> 0.92f - (d - 2.2f) * 0.035f
                        else -> (0.72f - (d - 7f) * 0.008f).coerceAtLeast(0.5f)
                    }
                    val base = if (d < 0f) Color(0xFFE9E9EE) else fieldColor(nx, d)
                    val color = lerp(base, Color.White, if (d < 7f) 0.15f else 0.28f)
                    val gi = ((intensity * (RAMP.length - 1)) + (n - 0.5f) * 2.4f).toInt().coerceIn(1, RAMP.length - 1)
                    val glyph = RAMP[gi]
                    if (glyph == ' ') continue
                    glyphPaint.color = color.copy(alpha = (0.4f + intensity * 0.6f).coerceAtMost(1f)).toArgb()
                    nc.drawText(glyph.toString(), x, fy(y0), glyphPaint)
                }
            }
        }
    }
}

// =============================================================================================
//  Mascot — our own iridescent photo-card creature, drawn in the reference's rendering style:
//  pastel mesh gradient, thin white outline, bean eyes, topographic contour lines around it.
// =============================================================================================

@Composable
fun Mascot(modifier: Modifier = Modifier, look: Float = 0f, tilt: Float = -8f, contours: Int = 0) {
    val inf = rememberInfiniteTransition(label = "mascot")
    val breathe by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2800), RepeatMode.Reverse), label = "b")
    val blink by inf.animateFloat(
        1f, 1f,
        infiniteRepeatable(keyframes { durationMillis = 4600; 1f at 0; 1f at 4300; 0.08f at 4390; 1f at 4500 }),
        label = "blink",
    )
    val sheen by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Reverse), label = "s")
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val bw = minOf(w * 0.64f, h * 0.78f); val bh = bw * 1.32f * (1f + 0.012f * breathe)
        val left = (w - bw) / 2 + w * 0.06f; val top = h - bh * 0.9f
        val pivot = Offset(left + bw / 2, h)
        rotate(tilt, pivot) {
            val corner = bw * 0.44f
            // contour lines
            for (i in contours downTo 1) {
                val g = bw * 0.13f * i
                drawRoundRect(
                    Color.White.copy(alpha = 0.22f - 0.018f * i),
                    Offset(left - g, top - g), Size(bw + 2 * g, bh * 1.2f + 2 * g),
                    CornerRadius(corner + g), style = Stroke(1.dp.toPx()),
                )
            }
            val body = Path().apply {
                addRoundRect(RoundRect(left, top, left + bw, top + bh * 1.2f, CornerRadius(corner)))
            }
            // pastel mesh: warm bottom-left, mint/sky right, lavender top
            drawPath(body, Brush.linearGradient(listOf(Pm.Blush, Pm.Peach, Pm.Lime.copy(alpha = 0.9f), Pm.Mint, Pm.Sky), start = Offset(left - bw * 0.2f * sheen, top + bh), end = Offset(left + bw * 1.1f, top + bh * (0.15f - 0.1f * sheen))))
            drawPath(body, Brush.radialGradient(listOf(Color(0xCCB9C6FF), Color.Transparent), Offset(left + bw * 0.35f, top), bw * 0.75f))
            drawPath(body, Brush.radialGradient(listOf(Color(0x99FFFFFF), Color.Transparent), Offset(left + bw * 0.55f, top + bh * 0.28f), bw * 0.45f))
            drawPath(body, Color.White.copy(alpha = 0.95f), style = Stroke(2.2.dp.toPx()))
            // bean eyes, looking to the side
            val ey = top + bh * 0.42f
            val ex = left + bw * (0.56f + 0.07f * look)
            val eyeW = bw * 0.11f; val eyeH = bw * 0.2f * blink
            for ((k, dx) in listOf(0f, bw * 0.21f).withIndex()) {
                rotate(if (k == 0) -8f else 8f, Offset(ex + dx, ey)) {
                    drawOval(Pm.Night, Offset(ex + dx - eyeW / 2, ey - eyeH / 2), Size(eyeW, eyeH))
                }
            }
        }
    }
}

/** Kept for call sites that layer rings behind a mascot; contours are now drawn by [Mascot]. */
@Composable
fun Rings(modifier: Modifier = Modifier) {
    Box(modifier)
}

/** App icon: black squircle with the mascot, like the icon tile in the reference. */
@Composable
fun AppIcon(modifier: Modifier = Modifier, size: Dp = 120.dp) {
    val shape = RoundedCornerShape(size * 0.27f)
    Box(
        modifier
            .size(size)
            .shadow(size * 0.18f, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF1A1A1C), Color(0xFF050506))))
            .border(1.dp, Brush.verticalGradient(listOf(Color(0x55FFFFFF), Color(0x08FFFFFF))), shape),
        contentAlignment = Alignment.Center,
    ) {
        Mascot(Modifier.fillMaxSize().padding(size * 0.12f), look = 0.6f, tilt = 0f)
    }
}

/** Lavender, grainy tile with a warm blurred glow and the app icon in the middle. */
@Composable
fun IconTileContent(iconSize: Dp = 120.dp) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AuroraBackdrop(Modifier.fillMaxSize().grain())
        AppIcon(size = iconSize)
    }
}

// =============================================================================================
//  Frosted chat panel (the "Ask anything" card) — used for the similar-photos assistant tile.
// =============================================================================================

@Composable
fun GlassPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .shadow(30.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xE6202024), Color(0xD9141417))))
            .border(1.dp, Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x0FFFFFFF))), shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text(
            text,
            Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF2C2C31)).padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium, color = Pm.Text,
        )
    }
}

@Composable
fun MascotReply(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Mascot(Modifier.size(30.dp), look = 0.5f, tilt = 0f)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Pm.Text.copy(alpha = 0.92f))
    }
}

@Composable
fun GlassAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .pressable(onClick, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x14FFFFFF))
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Pm.Muted, maxLines = 1)
        Box(Modifier.size(30.dp).clip(CircleShape).background(Color(0x26FFFFFF)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.ArrowUpward, null, tint = Pm.Text, modifier = Modifier.size(16.dp))
        }
    }
}

/** Reference-style tile caption: big bold two-line title, light grey body. */
@Composable
fun Caption(title: String, body: String? = null, modifier: Modifier = Modifier, big: Boolean = true) {
    Column(modifier.padding(horizontal = 22.dp, vertical = 20.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = if (big) 25.sp else 20.sp, lineHeight = if (big) 27.sp else 22.sp,
                fontWeight = FontWeight.Bold, letterSpacing = (-0.7).sp,
            ),
            color = Pm.Text,
        )
        if (body != null) {
            Spacer(Modifier.size(8.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp, letterSpacing = 0.1.sp), color = Color(0xFFA2A2AA))
        }
    }
}

// =============================================================================================
//  Screen header tile — bento card with a decorative layer and a reference-style caption.
// =============================================================================================

@Composable
fun HeaderTile(
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
    decor: @Composable () -> Unit,
) {
    BentoTile(modifier) {
        decor()
        Box(Modifier.align(Alignment.BottomStart)) { Caption(title, body) }
        Box(Modifier.align(Alignment.TopEnd).padding(16.dp)) { trailing() }
    }
}

/**
 * Palette card from the reference: tonal columns (lighter haze above, the colour itself with its
 * name/hex in the middle, deeper shades below).
 */
@Composable
fun SwatchCard(colors: List<Color>, labels: List<String>, sublabels: List<String>, modifier: Modifier = Modifier) {
    val deep = Color(0xFF5E3460)
    val shadowTone = Color(0xFF1B1C2E)
    BentoTile(modifier) {
        Row(Modifier.fillMaxSize()) {
            colors.forEachIndexed { i, base ->
                val lum = 0.299f * base.red + 0.587f * base.green + 0.114f * base.blue
                Column(Modifier.weight(1f).fillMaxSize()) {
                    val tones = buildList {
                        for (k in 2 downTo 1) add(lerp(base, Color(0xFFBDBDC2), 0.14f * k))
                        add(base)
                        for (k in 1..3) add(lerp(lerp(base, deep, 0.2f * k), shadowTone, 0.22f * k))
                    }
                    tones.forEachIndexed { k, c ->
                        val label = k == 2
                        Box(Modifier.fillMaxWidth().weight(if (label) 1.5f else 1f).background(c), contentAlignment = Alignment.Center) {
                            if (label) {
                                val tc = if (lum < 0.55f) Color.White else Color(0xFF111114)
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(labels.getOrElse(i) { "" }, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp), color = tc, maxLines = 1)
                                    Text(sublabels.getOrElse(i) { "" }, style = MaterialTheme.typography.bodySmall.copy(fontSize = 8.sp, letterSpacing = 0.3.sp), color = tc.copy(alpha = 0.6f), maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x33FFFFFF), 0.3f to Color.Transparent)))
    }
}

fun hexOf(c: Color): String = String.format("#%06X", c.toArgb() and 0xFFFFFF)
