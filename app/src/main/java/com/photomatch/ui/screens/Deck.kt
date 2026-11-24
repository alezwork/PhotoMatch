package com.photomatch.ui.screens

import com.photomatch.ui.components.LiquidBadge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.border
import androidx.compose.animation.Crossfade
import com.photomatch.ui.components.ThumbImage
import com.photomatch.ui.theme.grain
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import com.photomatch.AppViewModel
import com.photomatch.Screen
import com.photomatch.core.NativeCore
import com.photomatch.ui.components.DeckController
import com.photomatch.ui.components.GlassButton
import com.photomatch.ui.components.Mascot
import com.photomatch.ui.components.PillButton
import com.photomatch.ui.components.SwipeDeck
import com.photomatch.ui.components.TopBar
import com.photomatch.ui.theme.Pm
import com.photomatch.ui.theme.iridescent
import com.photomatch.data.Thumb

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckScreen(vm: AppViewModel) {
    val controller = remember { DeckController() }
    val ctx = LocalContext.current
    val deck = vm.deck
    val pos = vm.deckPos
    val window = remember(deck, pos) { if (pos < deck.size) deck.subList(pos, minOf(deck.size, pos + 3)) else emptyList() }
    val top = window.firstOrNull()
    var sheetFor by remember { mutableStateOf<com.photomatch.data.MediaItem?>(null) }
    val focus = remember { FocusRequester() }

    // Prefetch the cards after the visible ones so swiping never waits on decoding.
    LaunchedEffect(deck, pos) {
        val loader = SingletonImageLoader.get(ctx)
        // Same box as the card (screen-sized) so the prefetched bitmap is reused as-is: sharp, no re-decode.
        val dm = ctx.resources.displayMetrics
        val side = maxOf(dm.widthPixels, dm.heightPixels)
        for (i in pos + 3 until minOf(deck.size, pos + 8)) {
            loader.enqueue(ImageRequest.Builder(ctx).data(Thumb(deck[i].uri, deck[i].isVideo)).size(side, side).build())
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun undo() {
        vm.undo()?.let { (item, d) -> controller.comeBack(item, d) }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionRight -> { controller.swipe(NativeCore.KEEP); true }
                    Key.DirectionLeft -> { controller.swipe(NativeCore.NOPE); true }
                    Key.DirectionUp -> { controller.swipe(NativeCore.SUPER); true }
                    Key.Z, Key.Backspace -> { undo(); true }
                    else -> false
                }
            }
    ) {
        val wide = maxWidth >= 840.dp || (maxWidth >= 640.dp && maxWidth > maxHeight)
        val xl = maxWidth >= 1100.dp
        val remaining = deck.size - pos
        val progress by animateFloatAsState(if (deck.isEmpty()) 1f else pos.toFloat() / deck.size, tween(400), label = "deckp")

        AmbientBackdrop(top, Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize()) {
            TopBar(vm.scope.title, { vm.back() }, subtitle = if (remaining > 0) "Осталось ${"%,d".format(remaining).replace(',', ' ')}" else "Готово") {
                LiquidBadge(
                    if (deck.isEmpty()) 1f else pos.toFloat() / deck.size,
                    Modifier.size(52.dp).border(1.dp, Pm.Stroke, androidx.compose.foundation.shape.CircleShape),
                )
                if (!wide && top != null) {
                    Spacer(Modifier.size(10.dp))
                    GlassButton(Icons.Rounded.Info, { sheetFor = top }, size = 44.dp, contentDescription = "Свойства")
                }
            }
            Row(Modifier.fillMaxSize().navigationBarsPadding()) {
                Column(
                    Modifier.weight(1f).fillMaxHeight().padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.weight(1f).fillMaxWidth().widthIn(max = 640.dp), contentAlignment = Alignment.Center) {
                        AnimatedContent(top == null, transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.9f)) togetherWith fadeOut() }, label = "empty") { empty ->
                            if (empty) DoneState(vm) else BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                // Keep the card portrait-ish (like a photo print) on wide/landscape screens.
                                val cardMod = if (maxWidth / maxHeight > 0.78f) Modifier.fillMaxHeight().width(maxHeight * 0.78f) else Modifier.fillMaxSize()
                                SwipeDeck(
                                    items = window,
                                    controller = controller,
                                    onDecision = { item, d -> vm.decide(item, d) },
                                    onTap = { if (!wide) sheetFor = it },
                                    similarCount = { vm.groupFor(it)?.items?.size ?: 0 },
                                    modifier = cardMod,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    ActionBar(
                        controller = controller,
                        enabled = top != null,
                        canUndo = vm.canUndo,
                        onUndo = ::undo,
                        onNope = { controller.swipe(NativeCore.NOPE) },
                        onSuper = { controller.swipe(NativeCore.SUPER) },
                        onKeep = { controller.swipe(NativeCore.KEEP) },
                    )
                }
                if (wide) {
                    Box(
                        Modifier
                            .weight(if (xl) 0.8f else 0.9f)
                            .fillMaxHeight()
                            .padding(top = 14.dp, end = 16.dp, bottom = 14.dp)
                            .clip(Pm.TileShape)
                            .background(Pm.Surface.copy(alpha = 0.82f))
                            .border(1.dp, Pm.Stroke, Pm.TileShape)
                    ) {
                        if (top != null) DetailsPanel(vm, top, Modifier.fillMaxSize())
                    }
                }
            }
        }
    }

    sheetFor?.let { item ->
        val state = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        ModalBottomSheet(
            onDismissRequest = { sheetFor = null },
            sheetState = state,
            containerColor = Pm.Surface,
            scrimColor = Color(0x99000000),
        ) {
            DetailsPanel(vm, item, Modifier.fillMaxWidth().height(620.dp))
        }
    }
}

@Composable
private fun ActionBar(controller: DeckController, enabled: Boolean, canUndo: Boolean, onUndo: () -> Unit, onNope: () -> Unit, onSuper: () -> Unit, onKeep: () -> Unit) {
    Row(
        Modifier
            .clip(Pm.ChipShape)
            .background(Color(0x99101013))
            .border(1.dp, Pm.Stroke, Pm.ChipShape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassButton(Icons.Rounded.Undo, onUndo, size = 46.dp, tint = Pm.Peach, enabled = canUndo, contentDescription = "Отменить")
        GlassButton(
            Icons.Rounded.Close, onNope, size = 64.dp, tint = Pm.Nope, ring = Brush.linearGradient(listOf(Pm.Nope, Pm.Plum)),
            enabled = enabled, contentDescription = "Удалить", activation = { (-controller.dx).coerceIn(0f, 1f) },
        )
        GlassButton(
            Icons.Rounded.Star, onSuper, size = 52.dp, tint = Pm.Super, ring = Brush.linearGradient(listOf(Pm.Lime, Pm.Peach)),
            enabled = enabled, contentDescription = "В избранное", activation = { (-controller.dy).coerceIn(0f, 1f) * (1f - kotlin.math.abs(controller.dx)) },
        )
        GlassButton(
            Icons.Rounded.Favorite, onKeep, size = 64.dp, tint = Pm.Keep, ring = Brush.linearGradient(listOf(Pm.Mint, Pm.Sky)),
            enabled = enabled, contentDescription = "Оставить", activation = { controller.dx.coerceIn(0f, 1f) },
        )
    }
}

/** Blurred, slowly cross-fading colour wash taken from the current photo (Android 12+ blur). */
@Composable
private fun AmbientBackdrop(item: com.photomatch.data.MediaItem?, modifier: Modifier) {
    Box(modifier) {
        Crossfade(item, animationSpec = tween(700), label = "ambient") { m ->
            if (m != null) ThumbImage(
                m,
                Modifier.fillMaxSize().graphicsLayer { alpha = 0.55f; scaleX = 1.3f; scaleY = 1.3f }.blur(90.dp),
                requestSize = 96,
                placeholder = Color.Transparent,
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xB30B0B0D), Color(0x800B0B0D), Color(0xF20B0B0D)))))
        Box(Modifier.fillMaxSize().grain(0.05f))
    }
}

@Composable
private fun DoneState(vm: AppViewModel) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        com.photomatch.ui.components.BentoTile(Modifier.fillMaxWidth().weight(1f)) {
            Mascot(Modifier.fillMaxSize().padding(top = 36.dp), look = 0f, contours = 9)
        }
        com.photomatch.ui.components.BentoTile(Modifier.fillMaxWidth()) {
            com.photomatch.ui.components.DitherWave(Modifier.matchParentSize(), level = 0.85f, amplitude = 0.06f)
            Column {
                com.photomatch.ui.components.Caption("Всё\nразобрано", "Загляни в корзину,\nчтобы освободить место")
                Row(Modifier.padding(start = 22.dp, end = 22.dp, bottom = 22.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton("Корзина", { vm.open(Screen.Trash) }, brush = Brush.linearGradient(listOf(Pm.Blush, Pm.Peach)))
                    PillButton("Домой", { vm.back() })
                }
            }
        }
    }
}
