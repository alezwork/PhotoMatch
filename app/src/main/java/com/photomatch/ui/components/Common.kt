package com.photomatch.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.photomatch.data.MediaItem
import com.photomatch.data.Thumb
import com.photomatch.ui.theme.Pm
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Press-to-shrink with a soft spring, used by every tappable surface. */
@Composable
fun Modifier.pressable(onClick: (() -> Unit)?, shape: Shape = Pm.TileShape): Modifier {
    if (onClick == null) return this
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.965f else 1f, spring(0.55f, Spring.StiffnessMediumLow), label = "press")
    return this
        .graphicsLayer { scaleX = s; scaleY = s }
        .clip(shape)
        .clickable(src, indication = null, onClick = onClick)
}

/** A bento tile: near-black card, hairline border, big radius. */
@Composable
fun BentoTile(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    background: Color = Pm.Surface,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .pressable(onClick)
            .clip(Pm.TileShape)
            .background(background)
            .border(1.dp, Pm.Stroke, Pm.TileShape),
        content = content,
    )
}

@Composable
fun TileText(title: String, subtitle: String? = null, modifier: Modifier = Modifier, big: Boolean = false) {
    androidx.compose.foundation.layout.Column(modifier.padding(18.dp)) {
        Text(title, style = if (big) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge, color = Pm.Text)
        if (subtitle != null) {
            Spacer(Modifier.size(6.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Pm.Muted)
        }
    }
}

@Composable
fun Chip(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, tint: Color = Pm.Text, bg: Color = Color(0x33000000)) {
    Row(
        modifier
            .clip(Pm.ChipShape)
            .background(bg)
            .border(1.dp, Color(0x1FFFFFFF), Pm.ChipShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

/** Round "glass" icon button. */
@Composable
fun GlassButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    tint: Color = Pm.Text,
    ring: Brush? = null,
    enabled: Boolean = true,
    contentDescription: String? = null,
    /** 0..1 — how "active" the button is (e.g. while the card is dragged its way). */
    activation: () -> Float = { 0f },
) {
    val alpha = if (enabled) 1f else 0.35f
    Box(
        modifier
            .size(size)
            .graphicsLayer {
                val a = activation()
                this.alpha = alpha
                val s = 1f + 0.16f * a
                scaleX = s; scaleY = s
            }
            .pressable(if (enabled) onClick else null, CircleShape)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(tint.copy(alpha = if (ring != null) 0.16f else 0.05f), Color(0xFF16161B)),
                ),
            )
            .drawWithContent {
                val a = activation()
                if (a > 0f) drawCircle(tint.copy(alpha = 0.9f * a))
                drawContent()
            }
            .then(if (ring != null) Modifier.border(1.5.dp, ring, CircleShape) else Modifier.border(1.dp, Pm.Stroke, CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(size * 0.42f).graphicsLayer { this.alpha = 1f - activation() })
        Icon(icon, null, tint = Pm.Night, modifier = Modifier.size(size * 0.42f).graphicsLayer { this.alpha = activation() })
    }
}

/** Pill-shaped primary action. */
@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, brush: Brush? = null, textColor: Color = Pm.Night, icon: ImageVector? = null) {
    Row(
        modifier
            .pressable(onClick, Pm.ChipShape)
            .clip(Pm.ChipShape)
            .background(brush ?: Brush.linearGradient(listOf(Pm.Text, Pm.Text)))
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = textColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = textColor, fontWeight = FontWeight.SemiBold)
    }
}

/** Thumbnail from MediaStore's thumbnail cache via Coil. */
@Composable
fun ThumbImage(
    item: MediaItem,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    requestSize: Int? = null,
    placeholder: Color = Pm.Surface2,
) {
    val ctx = LocalContext.current
    val req = remember(item.id, requestSize) {
        ImageRequest.Builder(ctx).data(Thumb(item.uri, item.isVideo)).apply { if (requestSize != null) size(requestSize) }.build()
    }
    Box(modifier.background(placeholder)) {
        AsyncImage(req, null, Modifier.fillMaxSize(), contentScale = contentScale)
    }
}

@Composable
fun TopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null, actions: @Composable () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassButton(Icons.AutoMirrored.Rounded.ArrowBack, onBack, size = 44.dp, contentDescription = "Назад")
        Spacer(Modifier.width(14.dp))
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
                color = Pm.Text, maxLines = 1,
            )
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Pm.Muted, maxLines = 1)
        }
        actions()
    }
}

// ---- formatting ------------------------------------------------------------------------------

private val ru = Locale("ru")
private val dateFmt = DateTimeFormatter.ofPattern("d MMMM yyyy", ru)
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", ru)
private val shortFmt = DateTimeFormatter.ofPattern("d MMM, HH:mm", ru)

fun formatDate(ms: Long): String = dateFmt.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
fun formatTime(ms: Long): String = timeFmt.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
fun formatShort(ms: Long): String = shortFmt.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

fun formatBytes(b: Long): String = when {
    b >= 1L shl 30 -> String.format(ru, "%.1f ГБ", b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> String.format(ru, "%.1f МБ", b / (1L shl 20).toDouble())
    b >= 1L shl 10 -> String.format(ru, "%d КБ", b shr 10)
    else -> "$b Б"
}

fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) String.format(ru, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) else String.format(ru, "%d:%02d", s / 60, s % 60)
}

fun plural(n: Int, one: String, few: String, many: String): String {
    val m10 = n % 10; val m100 = n % 100
    val w = when {
        m10 == 1 && m100 != 11 -> one
        m10 in 2..4 && m100 !in 12..14 -> few
        else -> many
    }
    return "$n $w"
}

@Composable
fun GradientText(text: String, style: androidx.compose.ui.text.TextStyle, modifier: Modifier = Modifier) {
    Text(text, modifier, style = style.copy(brush = Brush.linearGradient(Pm.spectrum)))
}

@Composable
fun Dot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}
