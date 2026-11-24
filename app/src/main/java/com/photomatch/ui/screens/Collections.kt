package com.photomatch.ui.screens

import com.photomatch.ui.components.LiquidBadge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SwipeRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.photomatch.AppViewModel
import com.photomatch.Scope
import com.photomatch.Screen
import com.photomatch.core.NativeCore
import com.photomatch.data.MediaItem
import com.photomatch.ui.components.BentoTile
import com.photomatch.ui.components.Chip
import com.photomatch.ui.components.DitherWave
import com.photomatch.ui.components.Mascot
import com.photomatch.ui.components.PillButton
import com.photomatch.ui.components.ThumbImage
import com.photomatch.ui.components.TopBar
import com.photomatch.ui.components.formatBytes
import com.photomatch.ui.components.formatDuration
import com.photomatch.ui.components.formatShort
import com.photomatch.ui.components.plural
import com.photomatch.ui.components.pressable
import com.photomatch.ui.theme.Pm
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.photomatch.ui.components.MascotReply
import com.photomatch.ui.components.GlassPanel
import com.photomatch.ui.components.SwatchCard
import com.photomatch.ui.components.Caption
import com.photomatch.ui.components.HeaderTile
import com.photomatch.ui.theme.iridescent

private val ThumbShape = RoundedCornerShape(16.dp)

// ---- Similar groups ------------------------------------------------------------------------

@Composable
fun SimilarScreen(vm: AppViewModel) {
    val groups = vm.groups
    val h = vm.hashing
    Column(Modifier.fillMaxSize()) {
        TopBar("Похожие снимки", { vm.back() })
        if (groups.isEmpty()) {
            EmptyState(if (h.running) "Ищу\nпохожие…" else "Похожих\nне найдено", "Серии, дубли и почти одинаковые\nкадры появятся здесь")
            return
        }
        val shots = remember(groups) { groups.sumOf { it.items.size } }
        LazyVerticalGrid(
            GridCells.Adaptive(300.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.navigationBarsPadding(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                HeaderTile(
                    "${plural(groups.size, "группа", "группы", "групп")}\nпохожих кадров",
                    if (h.running) "Анализирую ${h.done} из ${h.total}…" else "${plural(shots, "снимок", "снимка", "снимков")} можно проредить —\nоставь лучший кадр в каждой серии",
                    Modifier.fillMaxWidth().height(200.dp),
                    trailing = { if (h.running) LiquidBadge(h.fraction, Modifier.size(56.dp).border(1.dp, Pm.Stroke, CircleShape)) },
                ) { DitherWave(Modifier.fillMaxSize(), flip = true, level = 0.83f, amplitude = 0.05f, phaseOffset = 1f) }
            }
            items(groups, key = { it.id }) { g ->
                BentoTile(Modifier.fillMaxWidth().height(230.dp), onClick = { vm.open(Screen.Group(g.id)) }) {
                    Row(Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val shown = g.items.take(3)
                        shown.forEachIndexed { i, m ->
                            ThumbImage(m, Modifier.weight(if (i == 0) 1.4f else 1f).fillMaxHeight().clip(RoundedCornerShape(18.dp)))
                        }
                    }
                    Box(
                        Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color(0xD0000000)))
                    )
                    Box(Modifier.align(Alignment.BottomStart)) {
                        Caption(plural(g.items.size, "похожий кадр", "похожих кадра", "похожих кадров"), formatShort(g.items.first().time) + " · " + formatBytes(g.items.sumOf { it.size }), big = false)
                    }
                    Chip("${g.items.size}", Modifier.align(Alignment.TopEnd).padding(16.dp), icon = Icons.Rounded.AutoAwesome, tint = Pm.Lime)
                }
            }
        }
    }
}

@Composable
fun GroupScreen(vm: AppViewModel, id: Int) {
    val group = vm.groups.firstOrNull { it.id == id }
    if (group == null) { vm.back(); return }
    val initial = remember(group.id) { setOf(group.items.maxBy { it.width.toLong() * it.height * 4 + it.size / 1024 }.id) }
    var best by remember(group.id) { mutableStateOf(initial) }
    Column(Modifier.fillMaxSize()) {
        TopBar("Выбери лучшее", { vm.back() }, subtitle = "Отмеченные останутся, остальные — в корзину")
        LazyVerticalGrid(
            GridCells.Adaptive(170.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(group.items, key = { it.id }) { m ->
                val sel = m.id in best
                val ring by animateColorAsState(if (sel) Pm.Mint else Color.Transparent, label = "ring")
                val s by animateFloatAsState(if (sel) 1f else 0.94f, label = "s")
                Box(
                    Modifier
                        .aspectRatio(0.78f)
                        .graphicsLayer { scaleX = s; scaleY = s }
                        .pressable({ best = if (sel) best - m.id else best + m.id }, ThumbShape)
                        .border(3.dp, ring, ThumbShape)
                        .padding(3.dp)
                        .clip(RoundedCornerShape(13.dp))
                ) {
                    ThumbImage(m, Modifier.fillMaxSize())
                    if (!sel) Box(Modifier.fillMaxSize().background(Color(0x66000000)))
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(8.dp).size(26.dp).clip(CircleShape)
                            .background(if (sel) Pm.Mint else Color(0x66000000)).border(1.5.dp, Color.White.copy(alpha = 0.8f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { if (sel) Icon(Icons.Rounded.Check, null, tint = Pm.Night, modifier = Modifier.size(16.dp)) }
                    Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                        Chip("${m.width}×${m.height} · ${formatBytes(m.size)}")
                    }
                }
            }
        }
        GlassPanel(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
            MascotReply("Оставлю ${plural(best.size, "кадр", "кадра", "кадров")}, остальные ${group.items.size - best.size} — в корзину")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Свайпать", { vm.startDeck(Scope.Similar(group.id)) }, icon = Icons.Rounded.SwipeRight)
                PillButton("Готово", { vm.keepBest(group, best); vm.back() }, Modifier.weight(1f), brush = iridescent())
            }
        }
    }
}

// ---- Timeline -------------------------------------------------------------------------------

@Composable
fun TimelineScreen(vm: AppViewModel) {
    val months = vm.months
    val v = vm.version
    Column(Modifier.fillMaxSize()) {
        TopBar("По месяцам", { vm.back() })
        LazyVerticalGrid(
            GridCells.Adaptive(165.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.navigationBarsPadding(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                val years = remember(months) { months.groupBy { it.year }.map { (y, ms) -> y to ms.sumOf { it.count } }.sortedBy { it.first }.takeLast(8) }
                val sw = listOf(Color.White, Pm.Night, Pm.Dusk, Pm.Blush, Pm.Peach, Pm.Lime, Pm.Mint, Pm.Sky).takeLast(years.size.coerceAtLeast(1))
                BoxWithConstraints {
                    val wideHeader = maxWidth >= 600.dp
                    if (wideHeader) {
                        Row(Modifier.fillMaxWidth().height(190.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            HeaderTile("Разбирай\nпо периодам", "${plural(months.size, "месяц", "месяца", "месяцев")} —\nвыбери любой и свайпай", Modifier.weight(1f).fillMaxHeight()) {
                                DitherWave(Modifier.fillMaxSize(), flip = true, level = 0.83f, amplitude = 0.05f, phaseOffset = 3f)
                            }
                            SwatchCard(sw, years.map { "${it.first}" }, years.map { "${it.second}" }, Modifier.weight(1.3f).fillMaxHeight())
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            HeaderTile("Разбирай\nпо периодам", "${plural(months.size, "месяц", "месяца", "месяцев")} — выбери любой", Modifier.fillMaxWidth().height(180.dp)) {
                                DitherWave(Modifier.fillMaxSize(), flip = true, level = 0.83f, amplitude = 0.05f, phaseOffset = 3f)
                            }
                            SwatchCard(sw, years.map { "${it.first}" }, years.map { "${it.second}" }, Modifier.fillMaxWidth().height(150.dp))
                        }
                    }
                }
            }
            var lastYear = -1
            months.forEach { b ->
                if (b.year != lastYear) {
                    lastYear = b.year
                    item(key = "y${b.year}", span = { GridItemSpan(maxLineSpan) }) {
                        Text("${b.year}", style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold, brush = Brush.linearGradient(Pm.spectrum)), modifier = Modifier.padding(top = 14.dp, bottom = 2.dp, start = 4.dp))
                    }
                }
                item(key = b.key) {
                    val reviewed = remember(b.key, v) { vm.reviewedIn(b) }
                    val cover = vm.items[b.start]
                    BentoTile(Modifier.aspectRatio(0.82f), onClick = { vm.startDeck(Scope.Month(b.key, "${b.label} ${b.year}")) }) {
                        ThumbImage(cover, Modifier.fillMaxSize())
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color(0xE6050506))))
                        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp)) {
                            Text(b.label, style = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp), color = Pm.Text)
                            Text("$reviewed из ${b.count}", style = MaterialTheme.typography.bodySmall, color = Color(0xFFA2A2AA))
                        }
                        LiquidBadge(
                            reviewed.toFloat() / b.count,
                            Modifier.align(Alignment.TopEnd).padding(10.dp).size(40.dp).border(1.dp, Color(0x40FFFFFF), CircleShape),
                            label = if (reviewed == b.count) "✓" else null,
                        )
                    }
                }
            }
        }
    }
}

// ---- Trash & favourites -----------------------------------------------------------------------

@Composable
fun TrashScreen(vm: AppViewModel, onDelete: () -> Unit) {
    val v = vm.version
    val list = remember(v, vm.items) { vm.itemsWith(NativeCore.NOPE) }
    val bytes = list.sumOf { it.size }
    Column(Modifier.fillMaxSize()) {
        TopBar("Корзина", { vm.back() })
        if (list.isEmpty()) {
            EmptyState("Корзина\nпуста", "Свайпни влево то,\nчто не нужно")
            return
        }
        MediaGrid(list, Modifier.weight(1f), Icons.Rounded.Restore, "Вернуть", header = {
            HeaderTile("В корзине\n${plural(list.size, "файл", "файла", "файлов")}", "Освободит ${formatBytes(bytes)}.\nНажми ↺, чтобы вернуть файл", Modifier.fillMaxWidth().height(190.dp)) {
                DitherWave(Modifier.fillMaxSize(), flip = true, level = 0.83f, amplitude = 0.05f, phaseOffset = 2f)
            }
        }) { vm.setDecision(listOf(it), NativeCore.NONE) }
        GlassPanel(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
            MascotReply("Файлы уйдут в системную корзину — их можно вернуть 30 дней")
            PillButton(
                "Удалить ${list.size} · освободить ${formatBytes(bytes)}", onDelete, Modifier.fillMaxWidth(),
                brush = Brush.linearGradient(listOf(Pm.Blush, Pm.Peach)), icon = Icons.Rounded.DeleteForever,
            )
        }
    }
}

@Composable
fun FavoritesScreen(vm: AppViewModel, onSync: () -> Unit) {
    val v = vm.version
    val list = remember(v, vm.items) { vm.itemsWith(NativeCore.SUPER) }
    Column(Modifier.fillMaxSize()) {
        TopBar("Избранное", { vm.back() })
        if (list.isEmpty()) {
            EmptyState("Пока\nпусто", "Свайпни вверх, чтобы\nдобавить в избранное")
            return
        }
        MediaGrid(list, Modifier.weight(1f), Icons.Rounded.Close, "Убрать", header = {
            HeaderTile("Самое\nлюбимое", plural(list.size, "снимок", "снимка", "снимков"), Modifier.fillMaxWidth().height(190.dp)) {
                Mascot(Modifier.fillMaxSize().padding(top = 18.dp, start = 140.dp), look = -0.5f, tilt = 8f, contours = 8)
            }
        }) { vm.setDecision(listOf(it), NativeCore.KEEP) }
        PillButton(
            "Отметить в галерее", onSync, Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
            brush = Brush.linearGradient(listOf(Pm.Lime, Pm.Peach)), icon = Icons.Rounded.Star,
        )
    }
}

@Composable
private fun MediaGrid(
    list: List<MediaItem>,
    modifier: Modifier,
    actionIcon: ImageVector,
    actionLabel: String,
    header: (@Composable () -> Unit)? = null,
    onAction: (MediaItem) -> Unit,
) {
    LazyVerticalGrid(
        GridCells.Adaptive(112.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        if (header != null) item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.padding(bottom = 4.dp)) { header() } }
        items(list, key = { it.id }) { m ->
            Box(Modifier.animateItem().aspectRatio(1f).clip(ThumbShape).border(1.dp, Pm.Stroke, ThumbShape)) {
                ThumbImage(m, Modifier.fillMaxSize())
                if (m.isVideo) Chip(formatDuration(m.durationMs), Modifier.align(Alignment.BottomStart).padding(6.dp))
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(30.dp)
                        .pressable({ onAction(m) }, CircleShape)
                        .clip(CircleShape)
                        .background(Color(0x99000000))
                        .border(1.dp, Color(0x33FFFFFF), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(actionIcon, actionLabel, tint = Color.White, modifier = Modifier.size(16.dp)) }
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, sub: String) {
    Column(Modifier.fillMaxSize().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BentoTile(Modifier.fillMaxWidth().weight(1f)) {
            Mascot(Modifier.fillMaxSize().padding(top = 40.dp), look = -0.3f, contours = 9)
        }
        BentoTile(Modifier.fillMaxWidth().height(210.dp)) {
            DitherWave(Modifier.fillMaxSize(), level = 0.78f, amplitude = 0.07f)
            Caption(title, sub)
        }
    }
}
