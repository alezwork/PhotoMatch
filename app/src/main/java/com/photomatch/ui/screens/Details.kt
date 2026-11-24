package com.photomatch.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photomatch.AppViewModel
import com.photomatch.core.NativeCore
import com.photomatch.data.MediaDetails
import com.photomatch.data.MediaItem
import com.photomatch.ui.components.Dot
import com.photomatch.ui.components.SwatchCard
import com.photomatch.ui.components.ThumbImage
import com.photomatch.ui.components.formatBytes
import com.photomatch.ui.components.formatDate
import com.photomatch.ui.components.formatDuration
import com.photomatch.ui.components.formatTime
import com.photomatch.ui.components.hexOf
import com.photomatch.ui.theme.Pm
import java.util.Locale

private val colorNames = listOf("Свет", "Тон", "Тень", "Акцент", "Глубина", "Ночь")

/** Properties, the photo's own palette, and "same moment" / "similar" strips for one item. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailsPanel(vm: AppViewModel, item: MediaItem, modifier: Modifier = Modifier) {
    var details by remember(item.id) { mutableStateOf<MediaDetails?>(null) }
    var palette by remember(item.id) { mutableStateOf<List<Color>>(emptyList()) }
    LaunchedEffect(item.id) {
        details = vm.details(item)
        palette = vm.palette(item).map { Color(it) }
    }
    val v = vm.version
    val moment = remember(item.id, vm.items) { vm.sameMoment(item) }
    val similar = remember(item.id, vm.groups) { vm.groupFor(item)?.items.orEmpty() }

    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 20.dp)) {
        Text("Свойства", style = MaterialTheme.typography.labelMedium, color = Pm.Muted)
        Spacer(Modifier.height(6.dp))
        val date = formatDate(item.time)
        val title = date.substringBeforeLast(' ') + "\n" + date.substringAfterLast(' ')
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
            color = Pm.Text,
        )
        Spacer(Modifier.height(6.dp))
        Text("${formatTime(item.time)} · ${item.name}", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFA2A2AA), maxLines = 1)
        Spacer(Modifier.height(18.dp))

        if (palette.size >= 3) {
            SwatchCard(
                palette,
                labels = palette.indices.map { colorNames.getOrElse(it) { "" } },
                sublabels = palette.map { hexOf(it) },
                modifier = Modifier.fillMaxWidth().height(150.dp),
            )
            Spacer(Modifier.height(12.dp))
        }

        val d = details
        val mp = if (item.width > 0) String.format(Locale.US, "%.1f МП", item.width * item.height / 1_000_000f) else null
        val cells = buildList {
            add("Тип" to if (item.isVideo) "Видео · ${formatDuration(item.durationMs)}" else item.mime.substringAfter('/').uppercase())
            add("Размер" to formatBytes(item.size))
            if (item.width > 0) add("Разрешение" to "${item.width}×${item.height}")
            if (mp != null && !item.isVideo) add("Мегапиксели" to mp)
            if (item.bucket.isNotBlank()) add("Папка" to item.bucket)
            d?.camera?.let { add("Камера" to it) }
            d?.lens?.let { add("Объектив" to it) }
            d?.aperture?.let { add("Диафрагма" to it) }
            d?.exposure?.let { add("Выдержка" to it) }
            d?.iso?.let { add("ISO" to it) }
            d?.focal?.let { add("Фокусное" to it) }
            if (d?.lat != null && d.lon != null) add("Место" to String.format(Locale.US, "%.4f, %.4f", d.lat, d.lon))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 2) {
            cells.forEach { (k, value) -> PropCell(k, value, Modifier.weight(1f)) }
            if (cells.size % 2 == 1) Spacer(Modifier.weight(1f))
        }

        if (moment.size > 1) Strip("В тот же\nмомент", "${moment.size} кадров за ~${spanText(moment)}", moment, item, vm, v)
        if (similar.size > 1) Strip("Похожие\nснимки", "Можно оставить лучший", similar, item, vm, v)
        Spacer(Modifier.height(12.dp))
    }
}

private fun spanText(list: List<MediaItem>): String {
    val span = (list.maxOf { it.time } - list.minOf { it.time }) / 1000
    return when {
        span < 60 -> "$span с"
        span < 3600 -> "${span / 60} мин"
        else -> "${span / 3600} ч"
    }
}

@Composable
private fun PropCell(k: String, v: String, modifier: Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(Color(0xFF141417))
            .border(1.dp, Pm.Stroke, shape)
            .padding(horizontal = 14.dp, vertical = 11.dp)
    ) {
        Text(k, style = MaterialTheme.typography.labelMedium, color = Pm.Muted)
        Spacer(Modifier.height(3.dp))
        Text(v, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Pm.Text, maxLines = 1)
    }
}

@Composable
private fun Strip(title: String, sub: String, list: List<MediaItem>, current: MediaItem, vm: AppViewModel, version: Int) {
    Spacer(Modifier.height(24.dp))
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
            color = Pm.Text, modifier = Modifier.weight(1f),
        )
        Text(sub, style = MaterialTheme.typography.bodySmall, color = Pm.Muted)
    }
    Spacer(Modifier.height(10.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 8.dp)) {
        items(list, key = { it.id }) { m ->
            val d = remember(m.id, version) { vm.decisionOf(m) }
            val isCur = m.id == current.id
            val shape = RoundedCornerShape(14.dp)
            Box(
                Modifier
                    .size(width = 78.dp, height = 104.dp)
                    .clip(shape)
                    .border(if (isCur) 2.dp else 1.dp, if (isCur) Color.White else Pm.Stroke, shape)
            ) {
                ThumbImage(m, Modifier.fillMaxSize())
                val c = when (d) {
                    NativeCore.KEEP -> Pm.Keep
                    NativeCore.NOPE -> Pm.Nope
                    NativeCore.SUPER -> Pm.Super
                    else -> null
                }
                if (c != null) Dot(c, Modifier.align(Alignment.TopEnd).padding(6.dp), 10.dp)
                if (d == NativeCore.NOPE) Box(Modifier.fillMaxSize().background(Color(0x88000000)))
            }
        }
    }
}
