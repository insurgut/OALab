package ru.lisurgut.oalab

import android.app.DownloadManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SourcesScreen(library: LocalLibrary) {
    val context = LocalContext.current
    var downloadRefresh by remember { mutableIntStateOf(0) }
    val downloads = remember(downloadRefresh) { DownloadSupport.list(context) }
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<SourceFeed?>(null) }
    var showFormat by remember { mutableStateOf(false) }

    fun loadFeed(address: String) {
        if (!address.startsWith("https://")) {
            message = "Укажи полный HTTPS URL JSON-фида"
            return
        }
        busy = true
        message = ""
        scope.launch {
            try {
                val raw = withContext(Dispatchers.IO) { AnimeApi.fetchText(address) }
                val feed = withContext(Dispatchers.IO) { library.putFeed(address, raw) }
                message = "Добавлен «" + feed.name + "»: " + feed.count + " тайтлов"
                url = ""
            } catch (e: Exception) {
                message = "Ошибка импорта: " + (e.localizedMessage ?: "неизвестно")
            } finally {
                busy = false
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().background(Ink).statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp)
    ) {
        item {
            Eyebrow("Система источников")
            Spacer(Modifier.height(5.dp))
            Text("Источники", fontSize = 31.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(9.dp))
            Text(
                "Объединяй эпизоды и озвучки из собственных разрешённых видеокаталогов без аккаунта.",
                color = Muted, fontSize = 13.sp, lineHeight = 20.sp
            )
            Spacer(Modifier.height(20.dp))
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { SourceStat("Активные фиды", library.feeds.size.toString()) }
                Box(Modifier.weight(1f)) { SourceStat("Тайтлы", library.feeds.sumOf { it.count }.toString()) }
            }
            Spacer(Modifier.height(12.dp))
        }
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(Panel).padding(17.dp)
            ) {
                Eyebrow("Подключить видеокаталог")
                Spacer(Modifier.height(9.dp))
                Text("Импорт JSON / M3U", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "OAlab принимает HTTPS JSON и M3U-плейлисты с сериями, языками и озвучками. При сбое потока автоматически пробует другой доступный вариант.",
                    color = Muted, fontSize = 12.sp, lineHeight = 18.sp
                )
                Spacer(Modifier.height(13.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("https://example.org/anime.m3u", fontSize = 12.sp, color = Muted) },
                    singleLine = true,
                    shape = RoundedCornerShape(15.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Edge,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { loadFeed(url.trim()) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                ) {
                    if (busy) CircularProgressIndicator(
                        Modifier.size(17.dp), color = Ink, strokeWidth = 2.dp
                    ) else Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(7.dp))
                    Text(if (busy) "Импортируем…" else "Добавить источник", fontWeight = FontWeight.Bold)
                }
                if (message.isNotBlank()) {
                    Spacer(Modifier.height(9.dp))
                    Text(message, color = Paper, fontSize = 12.sp)
                }
                TextButton(onClick = { showFormat = true }) {
                    Icon(Icons.Rounded.Code, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Формат источника", fontSize = 12.sp)
                }
            }
        }
        item { SectionHeader("Мои видеоисточники", "Каждый фид можно обновить или удалить") }
        if (library.feeds.isEmpty()) {
            item {
                EmptyState(
                    Icons.Rounded.Dns,
                    "Источников пока нет",
                    "Добавь HTTPS JSON или M3U-фид. AniList, Jikan и Kitsu предоставляют каталог, а не сами серии."
                )
            }
        } else {
            items(library.feeds, key = { it.url }) { feed ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 9.dp)
                        .background(Panel, RoundedCornerShape(17.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(43.dp).background(PanelAlt, RoundedCornerShape(13.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Dns, null, tint = Paper, modifier = Modifier.size(21.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(feed.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(feed.count.toString() + " тайтлов · " + feed.url,
                            fontSize = 10.sp, color = Muted, maxLines = 2)
                    }
                    IconButton(onClick = { loadFeed(feed.url) }, enabled = !busy) {
                        Icon(Icons.Rounded.Refresh, "Обновить", tint = Paper, modifier = Modifier.size(19.dp))
                    }
                    IconButton(onClick = { deleteTarget = feed }) {
                        Icon(Icons.Rounded.DeleteOutline, "Удалить", tint = Muted, modifier = Modifier.size(19.dp))
                    }
                }
            }
        }
        item {
            SectionHeader("Мои загрузки", "Прямые MP4 · доступны офлайн после завершения",
                action = { downloadRefresh++ })
            if(downloads.isEmpty()) {
                Text("Загрузок нет. Открой тайтл и нажми «Скачать MP4».",
                    color=Muted,fontSize=12.sp,modifier=Modifier.padding(bottom=12.dp))
            } else {
                downloads.forEach { entry ->
                    val state = DownloadSupport.status(context,entry.id)
                    val text = when(state) {
                        DownloadManager.STATUS_SUCCESSFUL -> "Загружено · можно смотреть офлайн"
                        DownloadManager.STATUS_RUNNING -> "Загружается"
                        DownloadManager.STATUS_PENDING -> "В очереди"
                        DownloadManager.STATUS_PAUSED -> "Пауза"
                        DownloadManager.STATUS_FAILED -> "Не удалось скачать"
                        else -> "Нет данных"
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(bottom=8.dp)
                            .background(Panel,RoundedCornerShape(17.dp)).padding(12.dp),
                        verticalAlignment=Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Download,null,tint=Paper)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entry.title,fontSize=12.sp,fontWeight=FontWeight.SemiBold)
                            Text("Серия "+entry.episode+" · "+text,
                                color=Muted,fontSize=10.sp)
                        }
                        IconButton(onClick={
                            DownloadSupport.remove(context,entry)
                            downloadRefresh++
                        }) {
                            Icon(Icons.Rounded.DeleteOutline,"Удалить",
                                tint=Muted,modifier=Modifier.size(19.dp))
                        }
                    }
                }
            }
        }
        item {
            SectionHeader("Каталоги аниме", "Без регистрации · не содержат видеопотоки")
            SourceProviderRow("Авто", "AniList → Jikan → Kitsu при ошибке", library.catalogProvider == "Авто") {
                library.setProvider("Авто")
            }
            Spacer(Modifier.height(9.dp))
            SourceProviderRow("AniList", "GraphQL · каталог и ссылки на официальные серии", library.catalogProvider == "AniList") {
                library.setProvider("AniList")
            }
            Spacer(Modifier.height(9.dp))
            SourceProviderRow("Jikan", "MyAnimeList · публичный REST API", library.catalogProvider == "Jikan") {
                library.setProvider("Jikan")
            }
            Spacer(Modifier.height(9.dp))
            SourceProviderRow("Kitsu", "Альтернативный каталог · без регистрации", library.catalogProvider == "Kitsu") {
                library.setProvider("Kitsu")
            }
            Spacer(Modifier.height(120.dp))
        }
    }

    if (deleteTarget != null) {
        val feed = deleteTarget!!
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Удалить источник?") },
            text = { Text("Фид «" + feed.name + "» и его сохранённый кэш будут удалены с устройства.") },
            confirmButton = {
                TextButton(onClick = { library.removeFeed(feed); deleteTarget = null }) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Отмена") }
            },
            containerColor = PanelAlt
        )
    }
    if (showFormat) {
        AlertDialog(
            onDismissRequest = { showFormat = false },
            title = { Text("Формат видеокаталога") },
            text = {
                Text(
                    "JSON: name, entries[].anilistId/malId/title, episodes[].number, streams[].url/team/language/voices/votes/popularity/timing/subtitle. Также поддерживается M3U с #EXTINF, атрибутами anime, anilist-id, mal-id, episode, team, language. Примеры в README.",
                    fontSize = 12.sp, lineHeight = 19.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showFormat = false }) { Text("Понятно") }
            },
            containerColor = PanelAlt
        )
    }
}

@Composable
private fun SourceStat(label: String, count: String) {
    Column(
        Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(19.dp))
            .padding(17.dp)
    ) {
        Text(count, fontSize = 31.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(label, color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun SourceProviderRow(name: String, description: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(17.dp))
            .clickable(onClick = onSelect).padding(15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Public, null, tint = Paper)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.SemiBold)
            Text(description, fontSize = 11.sp, color = Muted)
        }
        Icon(
            if (selected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
            "Выбрать", tint = if (selected) Paper else Muted
        )
    }
}

@Composable
fun SettingsScreen(library: LocalLibrary, onReload: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().background(Ink).statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp)
    ) {
        item {
            Eyebrow("Только твоё")
            Spacer(Modifier.height(5.dp))
            Text("Настройки", fontSize = 31.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Управляй просмотром, озвучками и оформлением.", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(15.dp))
        }
        item {
            SectionHeader("Каталог и рекомендации")
            SettingSelect(
                icon = Icons.Rounded.Dns,
                title = "Каталог метаданных",
                description = "Источники названий, постеров и описаний",
                value = library.catalogProvider,
                options = listOf("Авто", "AniList", "Jikan", "Kitsu"),
                onSelect = { library.setProvider(it); onReload() }
            )
            SettingsSwitch(
                icon = Icons.Rounded.Sync,
                title = "Автопереключение при ошибке",
                description = "Перебрать другие найденные потоки серии, сохранив время",
                checked = library.autoFallback,
                onChecked = library::updateAutoFallback
            )
            SettingSelect(
                icon = Icons.Rounded.Translate,
                title = "Язык по умолчанию",
                description = "Предпочтительный язык звуковой дорожки",
                value = library.language,
                options = listOf("ru", "ja", "en", "uk", "de", "es"),
                onSelect = library::updateLanguage
            )
            SettingSelect(
                icon = Icons.Rounded.Sort,
                title = "Приоритет озвучек",
                description = "Порядок доступных вариантов",
                value = library.voiceSort,
                options = listOf("Популярность", "Голоса", "Оценки", "Тайминги", "По алфавиту"),
                onSelect = library::updateVoiceSort
            )
        }
        item {
            SectionHeader("Плеер")
            SettingSelect(
                icon = Icons.Rounded.FastForward,
                title = "Шаг перемотки",
                description = "Сколько секунд пропускать по кнопке",
                value = library.seekSeconds.toString() + " секунд",
                options = listOf("5 секунд", "10 секунд", "15 секунд", "30 секунд"),
                onSelect = { library.setSeek(it.substringBefore(" ").toInt()) }
            )
            SettingsSwitch(
                icon = Icons.Rounded.SkipNext,
                title = "Следующая серия автоматически",
                description = "Только если следующий поток реально найден",
                checked = library.autoplay,
                onChecked = library::updateAutoplay
            )
        }
        item {
            SectionHeader("Внешний вид")
            Row(
                Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(17.dp))
                    .padding(15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Palette, null, tint = Paper)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Monet · цвета обоев", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("Динамическая тема Android 12+; светлая или тёмная по системе.", fontSize = 11.sp, color = Muted)
                }
                Icon(Icons.Rounded.CheckCircle, null, tint = Paper)
            }
        }
        item {
            SectionHeader("Конфиденциальность")
            Row(
                Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(17.dp))
                    .padding(15.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(Icons.Rounded.Lock, null, tint = Paper)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Без аккаунтов", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        "Избранное, статусы, импортированные источники и прогресс хранятся на телефоне. Онлайн запросы нужны только для каталога и выбранного видео.",
                        fontSize = 11.sp, color = Muted, lineHeight = 17.sp
                    )
                }
            }
            Spacer(Modifier.height(23.dp))
            Eyebrow("OAlab · 0.2.0")
            Spacer(Modifier.height(6.dp))
            Text(
                "Jetpack Compose · Material 3 · Media3 ExoPlayer",
                color = Muted, fontSize = 11.sp
            )
            Spacer(Modifier.height(115.dp))
        }
    }
}

@Composable
private fun SettingsSwitch(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 9.dp)
            .background(Panel, RoundedCornerShape(17.dp)).padding(15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Paper)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(description, fontSize = 11.sp, color = Muted, lineHeight = 16.sp)
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = Muted,
                uncheckedTrackColor = Edge
            )
        )
    }
}

@Composable
private fun SettingSelect(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.padding(bottom = 9.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp))
                .background(Panel).clickable { expanded = true }.padding(15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = Paper)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(description, fontSize = 11.sp, color = Muted, lineHeight = 16.sp)
            }
            Spacer(Modifier.width(6.dp))
            Text(value, color = Muted, fontSize = 11.sp)
            Icon(Icons.Rounded.ExpandMore, null, tint = Muted, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = PanelAlt
        ) {
            options.forEach {
                DropdownMenuItem(
                    text = { Text(it) },
                    onClick = { onSelect(it); expanded = false },
                    leadingIcon = {
                        if (it == value) Icon(Icons.Rounded.Check, null)
                    }
                )
            }
        }
    }
}
