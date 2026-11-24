package com.photomatch.ui.theme

import android.graphics.Bitmap
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

/** Palette sampled from the reference: Mist · Night · Dusk · Blush · Peach · Lime · Mint · Sky. */
object Pm {
    val Night = Color(0xFF050506)
    val Surface = Color(0xFF0E0E10)
    val Surface2 = Color(0xFF19191C)
    val Stroke = Color(0x1AFFFFFF)
    val Text = Color(0xFFF5F5F7)
    val Muted = Color(0xFF9C9CA4)
    val Faint = Color(0xFF55555E)

    val Mist = Color(0xFFC9C9C9)
    val Dusk = Color(0xFF4D4A75)
    val Lavender = Color(0xFF5F5C8C)
    val Blush = Color(0xFFEC8B85)
    val Peach = Color(0xFFF0BF8E)
    val Lime = Color(0xFFEFF47F)
    val Mint = Color(0xFF7FEBE9)
    val Sky = Color(0xFF8FAAD3)
    val Plum = Color(0xFFB55E7C)

    val spectrum = listOf(Blush, Peach, Lime, Mint, Sky)

    val Keep = Mint
    val Nope = Blush
    val Super = Lime

    val CardShape = RoundedCornerShape(28.dp)
    val TileShape = RoundedCornerShape(22.dp)
    val ChipShape = RoundedCornerShape(50)
}

fun iridescent(start: Offset = Offset.Zero, end: Offset = Offset.Infinite) =
    Brush.linearGradient(Pm.spectrum, start, end)

private val Display = FontFamily.SansSerif

val PmTypography = Typography(
    displayLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 46.sp, letterSpacing = (-1.6).sp),
    displayMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 36.sp, letterSpacing = (-1.1).sp),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 27.sp, letterSpacing = (-0.6).sp),
    titleLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 22.sp, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 19.sp, letterSpacing = (-0.2).sp),
    bodyMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = (-0.1).sp),
    labelMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.2.sp),
)

@Composable
fun PhotoMatchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Pm.Night, surface = Pm.Surface, surfaceVariant = Pm.Surface2,
            primary = Pm.Mint, onPrimary = Pm.Night, secondary = Pm.Peach, tertiary = Pm.Lime,
            onBackground = Pm.Text, onSurface = Pm.Text, onSurfaceVariant = Pm.Muted, outline = Pm.Stroke,
            surfaceContainer = Pm.Surface, surfaceContainerHigh = Pm.Surface2, surfaceContainerLow = Pm.Surface,
        ),
        typography = PmTypography,
        content = content,
    )
}

// ---- film grain ---------------------------------------------------------------------------

private val grainBitmap by lazy {
    val s = 128
    val rnd = Random(7)
    val px = IntArray(s * s) {
        val v = rnd.nextInt(256)
        val a = rnd.nextInt(40, 110)
        (a shl 24) or (v shl 16) or (v shl 8) or v
    }
    Bitmap.createBitmap(px, s, s, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** Overlays a subtle tiled film grain like in the reference renders. */
fun Modifier.grain(alpha: Float = 0.07f) = drawWithCache {
    val brush = ShaderBrush(ImageShader(grainBitmap, TileMode.Repeated, TileMode.Repeated))
    onDrawWithContent {
        drawContent()
        drawRect(brush, alpha = alpha)
    }
}
