package com.photomatch.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.photomatch.ui.components.AuroraBackdrop
import com.photomatch.ui.components.BentoTile
import com.photomatch.ui.components.DitherWave
import com.photomatch.ui.components.Mascot
import com.photomatch.ui.components.PillButton
import com.photomatch.ui.components.Caption
import com.photomatch.ui.theme.Pm
import com.photomatch.ui.theme.grain
import com.photomatch.ui.theme.iridescent

@Composable
fun OnboardingScreen(onGrant: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp
        val gap = 10.dp
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 980.dp), verticalArrangement = Arrangement.spacedBy(gap)) {
                Row(Modifier.fillMaxWidth().height(if (wide) 240.dp else 170.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    BentoTile(Modifier.weight(1f).fillMaxHeight()) {
                        AuroraBackdrop(Modifier.fillMaxSize().grain(0.09f))
                        com.photomatch.ui.components.AppIcon(Modifier.align(Alignment.TopEnd).padding(20.dp), size = if (wide) 110.dp else 72.dp)
                        Column(Modifier.padding(20.dp).align(Alignment.BottomStart)) {
                            Text("PhotoMatch", style = MaterialTheme.typography.displayLarge, color = Pm.Text)
                            Text("Наведи порядок в галерее свайпами", style = MaterialTheme.typography.bodyMedium, color = Pm.Text.copy(alpha = 0.8f))
                        }
                    }
                    if (wide) {
                        BentoTile(Modifier.weight(0.7f).fillMaxHeight()) {
                            Mascot(Modifier.fillMaxSize().padding(top = 24.dp), look = 0.6f, contours = 8)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().height(if (wide) 220.dp else 190.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    BentoTile(Modifier.weight(1f).fillMaxHeight()) {
                        DitherWave(Modifier.fillMaxSize(), level = 0.72f, amplitude = 0.08f)
                        Caption("Вправо —\nоставить", "Влево — в корзину,\nвверх — в избранное")
                    }
                    if (!wide) {
                        BentoTile(Modifier.weight(1f).fillMaxHeight()) {
                            Mascot(Modifier.fillMaxSize().padding(top = 20.dp), look = 0.6f, contours = 8)
                        }
                    } else {
                        BentoTile(Modifier.weight(1f).fillMaxHeight()) {
                            DitherWave(Modifier.fillMaxSize(), phaseOffset = 2f, flip = true)
                            Box(Modifier.align(Alignment.BottomStart)) {
                                Caption("Похожие —\nрядом", "Серии и дубли находятся\nавтоматически")
                            }
                        }
                        BentoTile(Modifier.weight(1f).fillMaxHeight()) {
                            Caption("Живёт только\nна телефоне", "Ни одно фото не покидает\nтвоё устройство")
                        }
                    }
                }
                if (!wide) {
                    Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        BentoTile(Modifier.weight(1f).fillMaxHeight()) {
                            DitherWave(Modifier.fillMaxSize(), phaseOffset = 2f, flip = true)
                            Box(Modifier.align(Alignment.BottomStart)) { Caption("Похожие —\nрядом", "Серии и дубли") }
                        }
                        BentoTile(Modifier.weight(1f).fillMaxHeight()) {
                            Caption("Живёт только\nна телефоне", "Фото не покидают\nустройство")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                BentoTile(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Нужен доступ к фото и видео", style = MaterialTheme.typography.titleLarge, color = Pm.Text)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Удаление всегда подтверждается системой, а файлы сначала попадают в корзину на 30 дней.",
                            style = MaterialTheme.typography.bodyMedium, color = Pm.Muted,
                        )
                        Spacer(Modifier.height(16.dp))
                        PillButton("Открыть галерею", onGrant, Modifier.fillMaxWidth(), brush = iridescent(), icon = Icons.Rounded.Lock)
                    }
                }
                Spacer(Modifier.size(8.dp))
            }
        }
    }
}
