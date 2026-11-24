package ru.lisurgut.oalab

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Only AniLibria streaming data. No API source, dubbing or host selection. */
@Composable
fun SettingsScreen(
    library: LocalLibrary,
    onReload: () -> Unit,
    onUpdates: () -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize().background(Ink).statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
    ) {
        item {
            Text(
                "Настройки", color = Paper,
                fontSize = 27.sp, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(9.dp))
            Text("OAlab · 0.4.1", color = Muted, fontSize = 11.sp)
            SectionHeader("Каталог")
            Row(
                Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.PlayCircle, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text("AniLibria", fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold, color = Paper)
                    Text(
                        "Один каталог и озвучка AniLibria. Никаких других API.",
                        fontSize = 11.sp, lineHeight = 16.sp, color = Muted
                    )
                }
                Icon(Icons.Rounded.CheckCircle, "Активен",
                    tint = MaterialTheme.colorScheme.primary)
            }
            SectionHeader("Плеер")
            Row(
                Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Автозапуск следующей серии", fontSize = 13.sp,
                        fontWeight = FontWeight.Medium, color = Paper)
                    Text("Когда серия доступна на AniLibria",
                        color = Muted, fontSize = 11.sp)
                }
                Switch(checked = library.autoplay, onCheckedChange = library::updateAutoplay)
            }
            Spacer(Modifier.height(9.dp))
            Column(
                Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text("Шаг перемотки", color = Paper, fontSize = 13.sp,
                    fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(9.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    listOf(5, 10, 15, 30).forEach { seconds ->
                        Box(Modifier.weight(1f)) {
                            FlatChip(
                                seconds.toString() + " с",
                                library.seekSeconds == seconds
                            ) { library.setSeek(seconds) }
                        }
                    }
                }
            }
            SectionHeader("Приложение")
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(Panel).clickable(onClick = onUpdates).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.SystemUpdate, null, tint = Paper)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Обновления", color = Paper,
                        fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Text("Новые версии и список изменений", color = Muted,
                        fontSize = 11.sp)
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = Muted)
            }
            SectionHeader("Оформление")
            Row(
                Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Palette, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text("Monet · Material You", color = Paper,
                        fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("Цвета обоев и системная светлая/тёмная тема",
                        color = Muted, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(100.dp))
        }
    }
}
