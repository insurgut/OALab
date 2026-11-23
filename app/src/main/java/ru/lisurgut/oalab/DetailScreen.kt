package ru.lisurgut.oalab

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DetailScreen(
    anime: Anime,
    library: LocalLibrary,
    onBack: () -> Unit,
    onPlay: (Int, StreamVariant) -> Unit
) {
    val context = LocalContext.current
    val localPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val direct = StreamVariant(
                source="Локальный файл",episode=library.entry(anime)?.lastEpisode ?: 1,
                language=library.language,team="Файл с устройства",
                voices=1,votes=0,popularity=999,timing="Локально",
                url=uri.toString(),subtitle=""
            )
            onPlay(direct.episode,direct)
        }
    }
    val variants = library.variantsFor(anime)
    val availableEps = variants.map { it.episode }.distinct().sorted()
    var episode by remember(anime.id) {
        mutableIntStateOf(
            library.entry(anime)?.lastEpisode?.takeIf { it in availableEps }
                ?: availableEps.firstOrNull() ?: 1
        )
    }
    var chosenUrl by remember(anime.id) { mutableStateOf<String?>(null) }
    var showVoices by remember { mutableStateOf(false) }
    var descriptionExpanded by remember(anime.id) { mutableStateOf(false) }
    var statusMenu by remember { mutableStateOf(false) }
    val ranked = library.rankVariants(variants.filter { it.episode == episode }, library.voiceSort)
    val chosen = ranked.firstOrNull { it.url == chosenUrl } ?: ranked.firstOrNull()
    val status = library.status(anime)

    LazyColumn(
        Modifier.fillMaxSize().background(Ink).statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 45.dp)
    ) {
        item {
            Box(Modifier.fillMaxWidth().height(232.dp)) {
                Cover(anime.banner.ifBlank { anime.cover }, Modifier.fillMaxSize())
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = .6f), Color.Transparent, Ink)
                        )
                    )
                )
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.TopStart).padding(start = 13.dp, top = 10.dp)
                        .clip(CircleShape).background(Color.Black.copy(alpha = .5f))
                ) {
                    Icon(Icons.Rounded.ArrowBack, "Назад", tint = Paper)
                }
                Row(
                    Modifier.align(Alignment.BottomStart).padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Cover(
                        anime.cover, Modifier.width(85.dp).height(118.dp)
                            .clip(RoundedCornerShape(17.dp))
                            .border(1.dp, Edge, RoundedCornerShape(17.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Eyebrow("OAlab / Информация")
                        Spacer(Modifier.height(8.dp))
                        Text(
                            anime.title,
                            fontSize = 20.sp,
                            lineHeight = 23.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(9.dp))
                        Text(
                            listOfNotNull(
                                anime.year.takeIf { it > 0 }?.toString(),
                                anime.format.takeIf { it.isNotBlank() }
                            ).joinToString(" · "),
                            fontSize = 11.sp,
                            color = Muted
                        )
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(13.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (anime.score > 0)
                        MetricChip((anime.score / 10f).toString() + " / 10", Icons.Rounded.Star)
                    if (anime.episodes > 0)
                        MetricChip(anime.episodes.toString() + " сер.", Icons.Rounded.Movie)
                    MetricChip(library.catalogProvider, Icons.Rounded.Public)
                }
                Spacer(Modifier.height(13.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            if (chosen != null) onPlay(episode, chosen)
                        },
                        enabled = chosen != null,
                        shape = RoundedCornerShape(17.dp),
                        modifier = Modifier.weight(1f).height(44.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Paper, contentColor = Ink,
                            disabledContainerColor = PanelAlt, disabledContentColor = Muted
                        )
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null)
                        Spacer(Modifier.width(7.dp))
                        Text(if (chosen == null) "Добавь источник" else "Смотреть", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(9.dp))
                    IconButton(
                        onClick = { library.toggleFavorite(anime) },
                        modifier = Modifier.size(43.dp).background(PanelAlt, RoundedCornerShape(17.dp))
                    ) {
                        Icon(
                            if (library.isFavorite(anime)) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            "Избранное",
                            tint = Paper
                        )
                    }
                    Spacer(Modifier.width(9.dp))
                    Box {
                        IconButton(
                            onClick = { statusMenu = true },
                            modifier = Modifier.size(43.dp).background(PanelAlt, RoundedCornerShape(17.dp))
                        ) {
                            Icon(Icons.Rounded.Checklist, "Статус", tint = Paper)
                        }
                        DropdownMenu(
                            expanded = statusMenu, onDismissRequest = { statusMenu = false },
                            containerColor = PanelAlt
                        ) {
                            listOf("Не смотрел", "В планах", "Смотрю", "Просмотрено", "Брошено").forEach {
                                DropdownMenuItem(
                                    text = { Text(it, fontSize = 13.sp) },
                                    leadingIcon = {
                                        if (it == status) Icon(Icons.Rounded.Check, null)
                                    },
                                    onClick = { library.setStatus(anime, it); statusMenu = false }
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(9.dp))
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text("Статус: " + status,fontSize=12.sp,color=Muted,modifier=Modifier.weight(1f))
                    TextButton(onClick={localPicker.launch(arrayOf("video/*"))}) {
                        Icon(Icons.Rounded.FolderOpen,null,modifier=Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Файл с телефона",fontSize=12.sp)
                    }
                }
            }
        }
        if (anime.description.isNotBlank()) item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                SectionHeader("Описание")
                Text(anime.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (descriptionExpanded) Int.MAX_VALUE else 5,
                    overflow = TextOverflow.Ellipsis, fontSize = 12.sp, lineHeight = 18.sp)
                if (anime.description.length > 260) TextButton(
                    onClick = { descriptionExpanded = !descriptionExpanded },
                    contentPadding = PaddingValues(horizontal = 0.dp)
                ) { Text(if (descriptionExpanded) "Свернуть" else "Читать полностью", fontSize = 12.sp) }
            }
        }
        if (anime.genres.isNotEmpty()) item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                SectionHeader("Жанры")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    anime.genres.forEach { FlatChip(it) {} }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                SectionHeader("Эпизоды", if (variants.isEmpty()) "Подключи видеокаталог в разделе «Источники»" else "Доступно: " + availableEps.size)
            }
        }
        if (availableEps.isNotEmpty()) item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(availableEps) { ep ->
                    FlatChip("Серия " + ep, episode == ep) { episode = ep; chosenUrl = null }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                SectionHeader("Озвучка и субтитры", "Выбор до начала просмотра")
                if (chosen != null) {
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Panel)
                            .clickable { showVoices = true }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(PanelAlt),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.GraphicEq, null, tint = Paper)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(chosen.team, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Spacer(Modifier.height(5.dp))
                            Text(
                                chosen.source + " · " + chosen.language.uppercase() +
                                    " · " + chosen.voices + " гол.",
                                fontSize = 11.sp, color = Muted
                            )
                        }
                        Icon(Icons.Rounded.UnfoldMore, "Выбрать", tint = Paper)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        ranked.size.toString() + " доступных вариантов для серии",
                        color = Muted, fontSize = 11.sp
                    )
                } else {
                    Column(
                        Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(19.dp))
                            .padding(18.dp)
                    ) {
                        Icon(Icons.Rounded.CloudOff, null, tint = Muted)
                        Spacer(Modifier.height(12.dp))
                        Text("Видеопоток пока не найден", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(7.dp))
                        Text(
                            "AniList и Jikan предоставляют описания аниме, но не видео. Добавь свой разрешённый HTTPS-фид в разделе «Источники».",
                            color = Muted, fontSize = 12.sp, lineHeight = 19.sp
                        )
                    }
                }
                if (chosen != null && DownloadSupport.canDownload(chosen)) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            try {
                                DownloadSupport.enqueue(context,anime,chosen)
                                Toast.makeText(context,"Начата загрузка MP4",Toast.LENGTH_SHORT).show()
                            } catch(e:Exception) {
                                Toast.makeText(context,e.localizedMessage ?: "Ошибка загрузки",Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),
                        border=BorderStroke(1.dp,Edge)
                    ) {
                        Icon(Icons.Rounded.Download,null)
                        Spacer(Modifier.width(7.dp))
                        Text("Скачать видео для просмотра офлайн")
                    }
                }
                if (anime.trailer.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = {
                            val link = "https://www.youtube.com/watch?v=" + anime.trailer
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Edge)
                    ) {
                        Icon(Icons.Rounded.SmartDisplay, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Смотреть официальный трейлер")
                    }
                }
                if(anime.official.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    SectionHeader("Где смотреть",
                        "Ссылки на сервисы и серии, доступность зависит от региона")
                    anime.official.distinctBy {it.url}.take(18).forEach {item ->
                        Row(
                            Modifier.fillMaxWidth().padding(bottom=7.dp)
                                .clip(RoundedCornerShape(15.dp)).background(Panel)
                                .clickable {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW,Uri.parse(item.url))
                                    )
                                }.padding(horizontal=13.dp,vertical=12.dp),
                            verticalAlignment=Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.OpenInNew,null,tint=Paper,
                                modifier=Modifier.size(17.dp))
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.site,fontWeight=FontWeight.SemiBold,fontSize=12.sp)
                                if(item.episode.isNotBlank()) {
                                    Text(item.episode,maxLines=1,overflow=TextOverflow.Ellipsis,
                                        color=Muted,fontSize=10.sp)
                                }
                            }
                            Icon(Icons.Rounded.ChevronRight,null,tint=Muted)
                        }
                    }
                }
            }
        }
    }
    if (showVoices && ranked.isNotEmpty()) {
        VoiceSelectorSheet(
            variants = ranked,
            selected = chosen,
            library = library,
            onDismiss = { showVoices = false },
            onSelect = { chosenUrl = it.url; showVoices = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSelectorSheet(
    variants: List<StreamVariant>,
    selected: StreamVariant?,
    library: LocalLibrary,
    onDismiss: () -> Unit,
    onSelect: (StreamVariant) -> Unit
) {
    var order by remember { mutableStateOf(library.voiceSort) }
    var lang by remember { mutableStateOf("Все") }
    val filtered = library.rankVariants(
        variants.filter { lang == "Все" || it.language.equals(lang, ignoreCase = true) },
        order
    )
    val languages = listOf("Все") + variants.map { it.language }.distinct().sorted()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        contentColor = Paper,
        dragHandle = {
            Box(Modifier.padding(top = 13.dp, bottom = 12.dp).size(34.dp, 4.dp)
                .background(Muted, RoundedCornerShape(3.dp)))
        }
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 30.dp)) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Eyebrow("Настройка воспроизведения")
                Spacer(Modifier.height(5.dp))
                Text("Выбери озвучку", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(7.dp))
                Text("Популярность и голоса берутся из подключённого источника.", color = Muted, fontSize = 11.sp)
            }
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                languages.forEach { code ->
                    FlatChip(if (code == "Все") code else code.uppercase(), lang == code) { lang = code }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Популярность", "Голоса", "Оценки", "Тайминги", "По алфавиту").forEach { option ->
                    FlatChip(option, order == option) {
                        order = option
                        library.updateVoiceSort(option)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 410.dp)) {
                items(filtered, key = { it.url + it.episode }) { variant ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { onSelect(variant) }
                            .padding(horizontal = 21.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (selected?.url == variant.url) Icons.Rounded.RadioButtonChecked
                            else Icons.Rounded.RadioButtonUnchecked,
                            null,
                            tint = if (selected?.url == variant.url) Paper else Muted,
                            modifier = Modifier.size(23.dp)
                        )
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            Text(variant.team, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                variant.source + " · " + variant.language.uppercase() +
                                    " · " + variant.voices + " голоса",
                                color = Muted, fontSize = 11.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Популярность " + variant.popularity +
                                    " · оценок " + variant.votes + " · " + variant.timing,
                                color = Muted, fontSize = 10.sp
                            )
                        }
                        Icon(Icons.Rounded.PlayArrow, null, tint = Muted)
                    }
                    HorizontalDivider(color = Edge, thickness = .5.dp)
                }
            }
        }
    }
}
