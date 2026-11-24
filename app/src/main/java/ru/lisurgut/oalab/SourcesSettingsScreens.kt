package ru.lisurgut.oalab

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    library: LocalLibrary,
    onReload: () -> Unit,
    onUpdates: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().background(Ink).statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 3.dp)
    ) {
        Text(
            "Настройки", color = Paper, fontSize = 27.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 17.dp, bottom = 8.dp)
        )
        SectionHeader("Воспроизведение")
        Column(
            Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(20.dp))
                .padding(horizontal = 13.dp, vertical = 5.dp)
        ) {
            SettingToggle(
                "Автоматически пропускать опенинги",
                "По таймингам текущей серии", library.skipOpenings,
                library::updateSkipOpenings
            )
            HorizontalDivider(color = Edge)
            SettingToggle(
                "Автоматически пропускать эндинги",
                "Если время окончания отмечено AniLibria", library.skipEndings,
                library::updateSkipEndings
            )
            HorizontalDivider(color = Edge)
            SettingToggle(
                "Объединить серии",
                "Единая шкала и непрерывная очередь", library.mergeEpisodes,
                library::updateMergeEpisodes
            )
            HorizontalDivider(color = Edge)
            SettingToggle(
                "Следующая серия автоматически",
                "При обычном просмотре, если она доступна", library.autoplay,
                library::updateAutoplay
            )
        }
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp))
                .padding(horizontal = 13.dp, vertical = 11.dp)
        ) {
            Text("Шаг перемотки", color = Paper, fontWeight = FontWeight.Medium,
                fontSize = 13.sp)
            Spacer(Modifier.height(9.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                listOf(5, 10, 15, 30).forEach { seconds ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
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
            Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(17.dp))
                .clickable(onClick = onUpdates)
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.SystemUpdate, null, tint = Paper,
                modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Обновления", color = Paper, fontWeight = FontWeight.Medium,
                    fontSize = 13.sp)
                Text("Список изменений и установка APK", color = Muted, fontSize = 11.sp)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = Muted,
                modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun SettingToggle(
    title: String,
    hint: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 63.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 12.sp, color = Paper,
                fontWeight = FontWeight.Medium)
            Text(hint, fontSize = 10.sp, lineHeight = 15.sp, color = Muted)
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            modifier = Modifier.height(39.dp)
        )
    }
}
