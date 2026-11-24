package com.photomatch.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem as ExoItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import com.photomatch.core.NativeCore
import com.photomatch.data.MediaItem
import com.photomatch.ui.theme.Pm
import com.photomatch.ui.theme.grain
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

/** Lets buttons drive the deck (programmatic swipes, undo fly-in) and exposes drag progress. */
@Stable
class DeckController {
    internal var command by mutableStateOf<Int?>(null)
    internal var returning by mutableStateOf<Pair<Long, Int>?>(null)
    /** -1..1 horizontal, -1..0 vertical (up). Read only inside draw/layout lambdas. */
    var dx by mutableFloatStateOf(0f); internal set
    var dy by mutableFloatStateOf(0f); internal set

    fun swipe(decision: Int) { command = decision }
    fun comeBack(item: MediaItem, decision: Int) { returning = item.id to decision }
}

/**
 * Tinder-style card stack. [items] is the visible window, first = top card.
 * Only the top card is interactive; the next two are pre-rendered (and thus
 * pre-loaded) underneath and grow into place as the top card is dragged.
 */
@Composable
fun SwipeDeck(
    items: List<MediaItem>,
    controller: DeckController,
    onDecision: (MediaItem, Int) -> Unit,
    onTap: (MediaItem) -> Unit,
    similarCount: (MediaItem) -> Int,
    modifier: Modifier = Modifier,
) {
    val player = rememberDeckPlayer()
    BoxWithConstraints(modifier) {
        val visible = items.take(3)
        for (i in visible.indices.reversed()) {
            val item = visible[i]
            key(item.id) {
                if (i == 0) {
                    TopCard(item, controller, onDecision, onTap, similarCount(item), player)
                } else {
                    val depth = i
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val p = (max(abs(controller.dx), abs(controller.dy))).coerceIn(0f, 1f)
                                val s = 1f - 0.05f * depth + 0.05f * p
                                scaleX = s; scaleY = s
                                translationY = (14.dp.toPx() * depth) * (1f - p * 0.9f)
                                alpha = if (depth == 2) 0.6f + 0.4f * p else 1f
                            }
                    ) {
                        CardFace(item, similarCount(item), player = null, dim = 0.25f * depth)
                    }
                }
            }
        }
    }
}

@Composable
private fun TopCard(
    item: MediaItem,
    controller: DeckController,
    onDecision: (MediaItem, Int) -> Unit,
    onTap: (MediaItem) -> Unit,
    similar: Int,
    player: ExoPlayer,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val ox = remember { Animatable(0f) }
    val oy = remember { Animatable(0f) }
    var grabBottom by remember { mutableStateOf(false) }
    var w by remember { mutableFloatStateOf(1f) }
    var h by remember { mutableFloatStateOf(1f) }
    var armed by remember { mutableStateOf(0) }
    var gone by remember { mutableStateOf(false) }

    fun progress() {
        controller.dx = (ox.value / (w * 0.32f)).coerceIn(-1f, 1f)
        controller.dy = (oy.value / (h * 0.24f)).coerceIn(-1f, 0f)
    }

    fun zoneOf(x: Float, y: Float): Int = when {
        y < -h * 0.2f && abs(x) < w * 0.3f -> NativeCore.SUPER
        x > w * 0.28f -> NativeCore.KEEP
        x < -w * 0.28f -> NativeCore.NOPE
        else -> 0
    }

    suspend fun flyAway(decision: Int, vx: Float = 0f, vy: Float = 0f) {
        if (gone) return
        gone = true
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        val targetX = when (decision) {
            NativeCore.KEEP -> w * 1.5f
            NativeCore.NOPE -> -w * 1.5f
            else -> ox.value * 0.4f
        }
        val targetY = if (decision == NativeCore.SUPER) -h * 1.3f else oy.value + vy * 0.12f
        val spec = tween<Float>(300, easing = FastOutSlowInEasing)
        controller.dx = 0f; controller.dy = 0f
        scope.launch { ox.animateTo(targetX, spec, initialVelocity = vx) }
        oy.animateTo(targetY, spec, initialVelocity = vy)
        onDecision(item, decision)
    }

    // Programmatic swipe from the buttons.
    LaunchedEffect(controller.command) {
        val c = controller.command ?: return@LaunchedEffect
        controller.command = null
        grabBottom = false
        flyAway(c)
    }
    // Undo: fly back in from where the card left.
    LaunchedEffect(Unit) {
        val r = controller.returning
        if (r != null && r.first == item.id) {
            controller.returning = null
            val (x, y) = when (r.second) {
                NativeCore.KEEP -> w * 1.4f to 0f
                NativeCore.NOPE -> -w * 1.4f to 0f
                else -> 0f to -h * 1.2f
            }
            ox.snapTo(x); oy.snapTo(y)
            val sp = spring<Float>(0.72f, Spring.StiffnessLow)
            launch { ox.animateTo(0f, sp) }
            oy.animateTo(0f, sp)
        }
    }
    // Autoplay muted video on the top card.
    DisposableEffect(item.id) {
        if (item.isVideo) {
            player.setMediaItem(ExoItem.fromUri(item.uri))
            player.prepare(); player.play()
        }
        onDispose { if (item.isVideo) player.stop() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(item.id) {
                w = size.width.toFloat(); h = size.height.toFloat()
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (gone) return@awaitEachGesture
                    grabBottom = down.position.y > size.height * 0.5f
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var moved = 0f
                    scope.launch { ox.stop(); oy.stop() }
                    val completed = drag(down.id) { change ->
                        val d = change.positionChange()
                        moved += d.getDistance()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        change.consume()
                        scope.launch {
                            ox.snapTo(ox.value + d.x)
                            oy.snapTo(oy.value + d.y)
                            progress()
                            val z = zoneOf(ox.value, oy.value)
                            if (z != armed) {
                                armed = z
                                if (z != 0) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                            }
                        }
                    }
                    if (moved < with(density) { 8.dp.toPx() } && completed) {
                        onTap(item)
                        return@awaitEachGesture
                    }
                    val v = tracker.calculateVelocity()
                    val fastX = abs(v.x) > 1800f && abs(v.x) > abs(v.y)
                    val fastUp = v.y < -2000f && abs(v.y) > abs(v.x) * 1.4f
                    val decision = when {
                        fastUp -> NativeCore.SUPER
                        fastX -> if (v.x > 0) NativeCore.KEEP else NativeCore.NOPE
                        else -> zoneOf(ox.value, oy.value)
                    }
                    scope.launch {
                        if (decision != 0) flyAway(decision, v.x, v.y)
                        else {
                            armed = 0
                            val sp = spring<Float>(0.55f, Spring.StiffnessMediumLow)
                            launch { ox.animateTo(0f, sp, v.x) { progress() } }
                            oy.animateTo(0f, sp, v.y) { progress() }
                        }
                    }
                }
            }
            .graphicsLayer {
                translationX = ox.value
                translationY = oy.value
                val r = ox.value / w * 16f
                rotationZ = if (grabBottom) -r else r
                transformOrigin = if (grabBottom) TransformOrigin(0.5f, -0.2f) else TransformOrigin(0.5f, 1.2f)
            }
    ) {
        CardFace(item, similar, player = if (item.isVideo) player else null, dim = 0f)
        // direction glow + stamps
        Box(
            Modifier
                .fillMaxSize()
                .clip(Pm.CardShape)
                .drawWithContent {
                    val px = (ox.value / (w * 0.28f)).coerceIn(-1f, 1f)
                    val py = ((-oy.value - h * 0.05f) / (h * 0.15f)).coerceIn(0f, 1f) * (1f - (abs(ox.value) / (w * 0.22f)).coerceIn(0f, 1f))
                    if (px > 0) drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Pm.Keep.copy(alpha = 0.45f * px)), startX = size.width * 0.3f))
                    if (px < 0) drawRect(Brush.horizontalGradient(listOf(Pm.Nope.copy(alpha = 0.45f * -px), Color.Transparent), endX = size.width * 0.7f))
                    if (py > 0) drawRect(Brush.verticalGradient(listOf(Color.Transparent, Pm.Super.copy(alpha = 0.4f * py)), startY = size.height * 0.4f))
                }
        )
        fun upness() = ((-oy.value - abs(ox.value)) / (h * 0.12f)).coerceIn(0f, 1f)
        Stamp("ОСТАВИТЬ", Pm.Keep, -14f, Alignment.TopStart) { ((ox.value - w * 0.04f) / (w * 0.24f)).coerceIn(0f, 1f) * (1f - upness()) }
        Stamp("УДАЛИТЬ", Pm.Nope, 14f, Alignment.TopEnd) { ((-ox.value - w * 0.04f) / (w * 0.24f)).coerceIn(0f, 1f) * (1f - upness()) }
        Stamp("ИЗБРАННОЕ", Pm.Super, -6f, Alignment.BottomCenter) { ((-oy.value - h * 0.05f) / (h * 0.15f)).coerceIn(0f, 1f) * (1f - (abs(ox.value) / (w * 0.22f)).coerceIn(0f, 1f)) }
    }
}

@Composable
private fun Stamp(text: String, color: Color, angle: Float, align: Alignment, alpha: () -> Float) {
    Box(Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 34.dp)) {
        Text(
            text,
            Modifier
                .align(align)
                .graphicsLayer {
                    val a = alpha()
                    this.alpha = a
                    rotationZ = angle
                    val s = 1.25f - 0.25f * a
                    scaleX = s; scaleY = s
                }
                .border(3.dp, color, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .background(Color(0x55000000), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp),
            color = color,
            fontSize = 30.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
        )
    }
}

/** The visual card: photo, scrim, metadata. */
@Composable
fun CardFace(item: MediaItem, similar: Int, player: ExoPlayer?, dim: Float) {
    Box(
        Modifier
            .fillMaxSize()
            .shadow(22.dp, Pm.CardShape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(Pm.CardShape)
            .background(Pm.Surface2)
            .border(1.dp, Brush.verticalGradient(listOf(Color(0x40FFFFFF), Color(0x0AFFFFFF), Color(0x1AFFFFFF))), Pm.CardShape)
    ) {
        ThumbImage(item, Modifier.fillMaxSize())
        // soft top sheen
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.25f).background(Brush.verticalGradient(listOf(Color(0x33000000), Color.Transparent))))
        if (player != null) VideoLayer(item, player)
        // scrim
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.5f to Color.Transparent, 0.78f to Color(0x8C000000), 1f to Color(0xF0050507)))
                .grain(0.05f)
        )
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(20.dp)) {
            Text(formatDate(item.time), style = MaterialTheme.typography.displayMedium.copy(fontSize = 28.sp, lineHeight = 30.sp), color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text(
                listOfNotNull(formatTime(item.time), item.bucket.ifBlank { null }, formatBytes(item.size)).joinToString("  ·  "),
                style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.72f), maxLines = 1,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.isVideo) Chip(formatDuration(item.durationMs), icon = Icons.Rounded.PlayArrow)
                if (item.width > 0) Chip("${item.width}×${item.height}")
                if (similar > 1) Chip("Похожих: $similar", icon = Icons.Rounded.AutoAwesome, tint = Pm.Lime)
                if (item.bucket.isNotBlank() && similar <= 1) Chip(item.bucket, icon = Icons.Rounded.Folder)
            }
        }
        if (dim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoLayer(item: MediaItem, player: ExoPlayer) {
    var ready by remember(item.id) { mutableStateOf(false) }
    DisposableEffect(player, item.id) {
        val l = object : Player.Listener {
            override fun onRenderedFirstFrame() { ready = true }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    BoxWithConstraints(Modifier.fillMaxSize().clip(Pm.CardShape), contentAlignment = Alignment.Center) {
        val vw = item.width.coerceAtLeast(1).toFloat(); val vh = item.height.coerceAtLeast(1).toFloat()
        val s = max(maxWidth.value / vw, maxHeight.value / vh)
        PlayerSurface(
            player = player,
            surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
            modifier = Modifier
                .requiredSize((vw * s).dp, (vh * s).dp)
                .graphicsLayer { alpha = if (ready) 1f else 0f },
        )
    }
}

@Composable
private fun rememberDeckPlayer(): ExoPlayer {
    val ctx = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(ctx).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }
    return player
}
