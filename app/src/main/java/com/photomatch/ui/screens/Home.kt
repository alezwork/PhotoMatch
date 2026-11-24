package com.photomatch.ui.screens

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SwipeRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photomatch.AppViewModel
import com.photomatch.Scope
import com.photomatch.Screen
import com.photomatch.data.MediaItem
import com.photomatch.ui.components.AppIcon
import com.photomatch.ui.components.AuroraBackdrop
import com.photomatch.ui.components.BentoTile
import com.photomatch.ui.components.Caption
import com.photomatch.ui.components.Chip
import com.photomatch.ui.components.DitherWave
import com.photomatch.ui.components.GlassAction
import com.photomatch.ui.components.GlassPanel
import com.photomatch.ui.components.GradientText
import com.photomatch.ui.components.LiquidFill
import com.photomatch.ui.components.Mascot
import com.photomatch.ui.components.MascotReply
import com.photomatch.ui.components.PillButton
import com.photomatch.ui.components.ThumbImage
import com.photomatch.ui.components.UserBubble
import com.photomatch.ui.components.formatBytes
import com.photomatch.ui.components.plural
import com.photomatch.ui.components.pressable
import com.photomatch.ui.theme.Pm
import com.photomatch.ui.theme.grain
import java.util.Locale

@Composable
fun HomeScreen(vm: AppViewModel) {
    val v = vm.version
    val stats = remember(v, vm.items) { vm.stats() }
    val remaining = stats.total - stats.reviewed
    val preview = remember(v, vm.items) { vm.items.asSequence().filter { vm.decisionOf(it) == 0 }.take(3).toList() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 640.dp
        val gap = 10.dp
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 1280.dp), verticalArrangement = Arrangement.spacedBy(gap)) {
                Header(stats.total, vm.months.size)

                val hero = @Composable { m: Modifier -> HeroTile(m, remaining, stats.total, preview) { vm.startDeck(Scope.All) } }
                val years = @Composable { m: Modifier -> YearsTile(m, vm) }
                val progress = @Composable { m: Modifier -> ProgressTile(m, stats.reviewed, stats.total) }
                val trash = @Composable { m: Modifier -> TrashTile(m, stats.nope, stats.nopeBytes) { vm.open(Screen.Trash) } }
                val mascot = @Composable { m: Modifier -> MascotTile(m, stats.keep, stats.fav) }
                val dock = @Composable { m: Modifier -> DockTile(m, vm) }
                val chat = @Composable { m: Modifier -> SimilarChatTile(m, vm) }

                if (wide) {
                    Row(Modifier.fillMaxWidth().height(330.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        hero(Modifier.weight(1.2f).fillMaxHeight()); years(Modifier.weight(1f).fillMaxHeight())
                    }
                    Row(Modifier.fillMaxWidth().height(250.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        progress(Modifier.weight(0.85f).fillMaxHeight()); trash(Modifier.weight(0.85f).fillMaxHeight()); mascot(Modifier.weight(1.3f).fillMaxHeight())
                    }
                    Row(Modifier.fillMaxWidth().height(250.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        dock(Modifier.weight(1.05f).fillMaxHeight()); chat(Modifier.weight(1f).fillMaxHeight())
                    }
                } else {
                    hero(Modifier.fillMaxWidth().height(380.dp))
                    years(Modifier.fillMaxWidth().height(170.dp))
                    Row(Modifier.fillMaxWidth().height(220.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        progress(Modifier.weight(1f).fillMaxHeight()); trash(Modifier.weight(1f).fillMaxHeight())
                    }
                    dock(Modifier.fillMaxWidth().height(200.dp))
                    chat(Modifier.fillMaxWidth().height(270.dp))
                    mascot(Modifier.fillMaxWidth().height(230.dp))
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun Header(total: Int, months: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(greeting(), style = MaterialTheme.typography.bodyMedium, color = Pm.Muted)
            GradientText("PhotoMatch", MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold))
            Text(
                "${plural(total, "файл", "файла", "файлов")} · ${plural(months, "месяц", "месяца", "месяцев")} воспоминаний",
                style = MaterialTheme.typography.bodySmall, color = Pm.Faint,
            )
        }
        AppIcon(size = 54.dp)
    }
}

// ---- hero ----------------------------------------------------------------------------------

@Composable
private fun HeroTile(modifier: Modifier, remaining: Int, total: Int, preview: List<MediaItem>, onStart: () -> Unit) {
    BentoTile(modifier, onClick = onStart) {
        AuroraBackdrop(Modifier.fillMaxSize().grain(0.09f))
        BoxWithConstraints(Modifier.fillMaxSize().padding(start = 20.dp, end = 26.dp, top = 22.dp, bottom = 96.dp)) {
            val cardH = maxHeight
            val cardW: Dp = (cardH * 0.76f).coerceAtMost(maxWidth * 0.62f)
            preview.reversed().forEachIndexed { revIdx, item ->
                val i = preview.size - 1 - revIdx
                val angle = listOf(-2f, 9f, -11f)[i]
                val dx = listOf(0.dp, 30.dp, (-34).dp)[i]
                val shape = RoundedCornerShape(18.dp)
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .offset(x = dx - 16.dp)
                        .graphicsLayer { rotationZ = angle; scaleX = 1f - i * 0.07f; scaleY = 1f - i * 0.07f; shadowElevation = 18f; this.shape = shape; clip = true }
                        .width(cardW).height(cardH)
                        .border(2.dp, Color(0xE6FFFFFF), shape)
                ) { ThumbImage(item, Modifier.fillMaxSize()) }
            }
        }
        Column(Modifier.align(Alignment.BottomStart).padding(22.dp)) {
            Text(if (remaining > 0) "Осталось разобрать" else "Всё разобрано", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
            val shown by animateIntAsState(remaining, tween(900), label = "rem")
            Text(spaced(shown), style = MaterialTheme.typography.displayLarge.copy(fontSize = 54.sp, fontWeight = FontWeight.Bold), color = Color.White)
            Text("из ${spaced(total)}", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.65f))
        }
        PillButton("Начать", onStart, Modifier.align(Alignment.BottomEnd).padding(20.dp), icon = Icons.Rounded.SwipeRight)
    }
}

// ---- years (palette swatches) -----------------------------------------------------------------

/** The palette card of the reference: one tonal column per year, label row in the middle. */
@Composable
private fun YearsTile(modifier: Modifier, vm: AppViewModel) {
    val years = remember(vm.months) {
        vm.months.groupBy { it.year }.map { (y, ms) -> y to ms.sumOf { it.count } }.sortedBy { it.first }.takeLast(8)
    }
    val swatch = listOf(Color.White, Pm.Night, Pm.Dusk, Pm.Blush, Pm.Peach, Pm.Lime, Pm.Mint, Pm.Sky)
    val deep = Color(0xFF5E3460)
    val shadowTone = Color(0xFF1B1C2E)
    BentoTile(modifier, onClick = { vm.open(Screen.Timeline) }) {
        Row(Modifier.fillMaxSize()) {
            years.forEachIndexed { i, (y, n) ->
                val base = swatch[(swatch.size - years.size + i).coerceAtLeast(0)]
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    // 3 haze steps above, the label step, 4 shade steps sinking toward plum/night
                    val tones = buildList {
                        for (k in 3 downTo 1) add(if (base == Pm.Night) Pm.Night else lerp(base, Color(0xFFBDBDC2), 0.12f * k))
                        add(base)
                        for (k in 1..4) add(if (base == Pm.Night) Pm.Night else lerp(lerp(base, deep, 0.22f * k), shadowTone, (0.2f * k).coerceAtMost(0.75f)))
                    }
                    tones.forEachIndexed { k, c ->
                        val label = k == 3
                        Box(Modifier.fillMaxWidth().weight(if (label) 1.35f else 1f).background(c), contentAlignment = Alignment.Center) {
                            if (label) {
                                val tc = if (base == Pm.Night || base == Pm.Dusk) Color.White else Color(0xFF111114)
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("$y", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp), color = tc, maxLines = 1)
                                    Text(spaced(n), style = MaterialTheme.typography.bodySmall.copy(fontSize = 8.5.sp, letterSpacing = 0.4.sp), color = tc.copy(alpha = 0.6f), maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x40FFFFFF), 0.35f to Color.Transparent)))
        Box(Modifier.fillMaxSize().grain(0.05f))
    }
}

// ---- progress (liquid) --------------------------------------------------------------------------

@Composable
private fun ProgressTile(modifier: Modifier, done: Int, total: Int) {
    val f = if (total == 0) 0f else done.toFloat() / total
    val titleStyle = MaterialTheme.typography.headlineMedium.copy(fontSize = 25.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.7).sp)
    BentoTile(modifier) {
        LiquidFill(f, Modifier.fillMaxSize(), minLevel = 0.12f)
        val pct = if (f in 0.0001f..0.1f) String.format(Locale.US, "%.1f%%", f * 100) else "${(f * 100).toInt()}%"
        Column(Modifier.padding(horizontal = 22.dp, vertical = 20.dp)) {
            Text("Разобрано", style = titleStyle, color = Pm.Text)
            Text(pct, style = titleStyle.copy(brush = Brush.linearGradient(Pm.spectrum)))
            Spacer(Modifier.size(8.dp))
            Text("${spaced(done)} из ${spaced(total)}\nфото и видео", style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp), color = Color(0xFFA2A2AA))
        }
    }
}

// ---- trash: wave from the top, caption at the bottom ---------------------------------------------

@Composable
private fun TrashTile(modifier: Modifier, count: Int, bytes: Long, onClick: () -> Unit) {
    BentoTile(modifier, onClick = onClick) {
        DitherWave(Modifier.fillMaxSize(), flip = true, level = 0.66f, amplitude = 0.1f, phaseOffset = 2f)
        Box(Modifier.align(Alignment.BottomStart)) {
            Caption(
                if (count == 0) "Корзина\nпуста" else "В корзине\n${plural(count, "файл", "файла", "файлов")}",
                if (count == 0) "Свайп влево —\nи файл окажется тут" else "Освободит ${formatBytes(bytes)}\nНажми, чтобы удалить",
            )
        }
    }
}

// ---- mascot with contour lines ---------------------------------------------------------------------

@Composable
private fun MascotTile(modifier: Modifier, keep: Int, fav: Int) {
    BentoTile(modifier) {
        Mascot(Modifier.fillMaxSize().padding(top = 26.dp), look = 0.7f, tilt = -10f, contours = 9)
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Оставлено ${spaced(keep)}", tint = Pm.Mint)
            Chip("Избранное ${spaced(fav)}", icon = Icons.Rounded.Star, tint = Pm.Lime)
        }
    }
}

// ---- dock -------------------------------------------------------------------------------------------

@Composable
private fun DockTile(modifier: Modifier, vm: AppViewModel) {
    BentoTile(modifier) {
        AuroraBackdrop(Modifier.fillMaxSize().grain(0.09f))
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val icon = (maxWidth / 6.2f).coerceIn(48.dp, 76.dp)
            Row(
                Modifier
                    .clip(RoundedCornerShape(icon * 0.42f))
                    .background(Color(0x40FFFFFF))
                    .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(icon * 0.42f))
                    .padding(horizontal = icon * 0.16f, vertical = icon * 0.14f),
                horizontalArrangement = Arrangement.spacedBy(icon * 0.2f),
            ) {
                DockIcon(icon, "Месяцы", { vm.open(Screen.Timeline) }) { CalendarGlyph() }
                DockIcon(icon, "Похожие", { vm.open(Screen.Similar) }) { BubbleGlyph() }
                DockIcon(icon, "Избранное", { vm.open(Screen.Favorites) }) { NotesGlyph() }
                DockIcon(icon, "Видео", { vm.startDeck(Scope.Videos) }) { VideoGlyph() }
            }
        }
    }
}

@Composable
private fun DockIcon(size: Dp, label: String, onClick: () -> Unit, glyph: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val shape = RoundedCornerShape(size * 0.25f)
        Box(
            Modifier
                .size(size)
                .pressable(onClick, shape)
                .graphicsLayer { shadowElevation = 10f; this.shape = shape; clip = true },
        ) { glyph() }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp, shadow = Shadow(Color(0x66000000), blurRadius = 4f)), color = Color.White)
    }
}

@Composable
private fun CalendarGlyph() = Canvas(Modifier.fillMaxSize()) {
    drawRect(Color.White)
    drawRect(Pm.Blush, size = Size(size.width, size.height * 0.24f))
    val cols = 6; val rows = 4
    val dx = size.width / (cols + 1); val dy = size.height * 0.62f / (rows + 1)
    for (r in 1..rows) for (c in 1..cols) {
        val hot = r == 3 && c == 4
        drawCircle(if (hot) Pm.Blush else Color(0xFF2A2A2E), size.width * (if (hot) 0.05f else 0.03f), Offset(c * dx, size.height * 0.3f + r * dy))
    }
}

@Composable
private fun BubbleGlyph() = Canvas(Modifier.fillMaxSize()) {
    drawRect(Brush.verticalGradient(listOf(Pm.Mint, Color(0xFF3FB6C9))))
    drawOval(Brush.verticalGradient(listOf(Color.White, Color(0xFFE6FAFB))), Offset(size.width * 0.16f, size.height * 0.22f), Size(size.width * 0.68f, size.height * 0.52f))
    drawCircle(Color.White, size.width * 0.09f, Offset(size.width * 0.3f, size.height * 0.72f))
}

@Composable
private fun NotesGlyph() = Canvas(Modifier.fillMaxSize()) {
    drawRect(Color.White)
    drawRect(Pm.Lime, size = Size(size.width, size.height * 0.26f))
    for (k in 0..2) drawRect(Color(0xFFDADADF), Offset(size.width * 0.12f, size.height * (0.48f + k * 0.16f)), Size(size.width * 0.76f, size.height * 0.025f))
}

@Composable
private fun VideoGlyph() = Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF222226), Color(0xFF09090A)))), contentAlignment = Alignment.Center) {
    Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.fillMaxSize(0.62f), tint = Pm.Peach)
}

// ---- similar: frosted chat panel over the ASCII wave -------------------------------------------------

@Composable
private fun SimilarChatTile(modifier: Modifier, vm: AppViewModel) {
    val h = vm.hashing
    val groups = vm.groups
    val shots = remember(groups) { groups.sumOf { it.items.size } }
    BentoTile(modifier) {
        DitherWave(Modifier.fillMaxSize(), level = 0.6f, amplitude = 0.09f, phaseOffset = 4f)
        Box(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp), contentAlignment = Alignment.Center) {
            GlassPanel(Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
                UserBubble("Есть у меня дубли?")
                MascotReply(
                    when {
                        h.running && groups.isEmpty() -> "Сравниваю снимки… ${(h.fraction * 100).toInt()}%"
                        h.running -> "Уже нашёл ${plural(groups.size, "группу", "группы", "групп")}, ищу дальше… ${(h.fraction * 100).toInt()}%"
                        groups.isEmpty() -> "Похожих кадров не нашлось"
                        else -> "${plural(groups.size, "группа", "группы", "групп")} — ${plural(shots, "похожий кадр", "похожих кадра", "похожих кадров")}"
                    },
                )
                GlassAction("Показать похожие снимки", { vm.open(Screen.Similar) })
            }
        }
    }
}

private fun spaced(n: Int): String = "%,d".format(n).replace(',', ' ').replace(' ', ' ')

private fun greeting(): String = when (java.time.LocalTime.now().hour) {
    in 5..11 -> "Доброе утро"
    in 12..17 -> "Добрый день"
    in 18..22 -> "Добрый вечер"
    else -> "Доброй ночи"
}
