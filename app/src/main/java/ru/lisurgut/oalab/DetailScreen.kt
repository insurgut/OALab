package ru.lisurgut.oalab

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DetailScreen(
    anime: Anime,
    library: LocalLibrary,
    onBack: () -> Unit,
    onWatch: (Int, String?) -> Unit
) {
    var loading by remember(anime.id) { mutableStateOf(true) }
    var problem by remember(anime.id) { mutableStateOf<String?>(null) }
    var expanded by remember(anime.id) { mutableStateOf(false) }
    var statuses by remember { mutableStateOf(false) }
    var modeOptions by remember { mutableStateOf(false) }
    var episode by remember(anime.id) {
        mutableIntStateOf(library.entry(anime)?.lastEpisode ?: 1)
    }
    var refresh by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()

    LaunchedEffect(anime.id, refresh) {
        loading = true
        problem = null
        try {
            library.saveResolvedStreams(anime, AniLibertyApi.resolve(anime))
        } catch (e: Exception) {
            problem = e.localizedMessage ?: "Не удалось загрузить серии"
        } finally {
            loading = false
        }
    }
    val streams = library.variantsFor(anime)
    val variants = streams.groupBy { it.episode }
    val episodes = variants.keys.sorted()
    val details = streams.mapNotNull { it.details }.distinctBy { it.episode }
    LaunchedEffect(episodes) {
        if (episodes.isNotEmpty() && episode !in episodes) episode = episodes.first()
    }

    LazyColumn(
        Modifier.fillMaxSize().background(Ink).statusBarsPadding(),
        state = listState,
        contentPadding = PaddingValues(bottom = 35.dp)
    ) {
        item {
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 7.dp)
                    .height(207.dp).clip(RoundedCornerShape(21.dp))
            ) {
                Cover(anime.banner.ifBlank { anime.cover }, Modifier.fillMaxSize(), anime.title)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = .20f),
                    .5f to Color.Black.copy(alpha = .42f),
                    1f to Color.Black.copy(alpha = .95f)
                )))
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                        .background(Color.Black.copy(alpha = .56f), CircleShape)
                ) { Icon(Icons.Rounded.ArrowBack, "Назад", tint = Color.White) }
                Row(
                    Modifier.align(Alignment.BottomStart).padding(13.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    Cover(
                        anime.cover,
                        Modifier.width(77.dp).height(104.dp)
                            .clip(RoundedCornerShape(13.dp)), anime.title
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(anime.title, color = Color.White,
                            fontSize = 20.sp, lineHeight = 23.sp,
                            fontWeight = FontWeight.Bold, maxLines = 3,
                            overflow = TextOverflow.Ellipsis)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            if (anime.year > 0) {
                                AnimeYearMark(anime.year)
                            }
                            Text(anime.format.ifBlank { "Аниме" },
                                color = Color.White.copy(alpha = .8f), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (anime.score > 0)
                        MetricChip((anime.score / 10f).toString() + " / 10", Icons.Rounded.Star)
                    MetricChip("AniLibria", Icons.Rounded.PlayCircle)
                    if (episodes.isNotEmpty()) MetricChip(
                        episodes.size.toString() + " сер.", Icons.Rounded.Movie
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WatchModeButton(
                        continuous = library.mergeEpisodes,
                        onPlay = { onWatch(episode, null) },
                        onLongPress = { modeOptions = !modeOptions },
                        modifier = Modifier.weight(1f).height(44.dp)
                    )
                    FilledTonalIconButton(
                        onClick = { library.toggleFavorite(anime) },
                        modifier = Modifier.size(44.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(if (library.isFavorite(anime)) Icons.Rounded.Favorite
                            else Icons.Rounded.FavoriteBorder,
                            "Избранное", Modifier.size(21.dp))
                    }
                    Box {
                        FilledTonalIconButton(
                            onClick = { statuses = true },
                            modifier = Modifier.size(44.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) { Icon(Icons.Rounded.Checklist, "Статус", Modifier.size(21.dp)) }
                        DropdownMenu(expanded = statuses, onDismissRequest = { statuses = false }) {
                            listOf(
                                "Не смотрел", "В планах", "Смотрю",
                                "Просмотрено", "Брошено"
                            ).forEach { label ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    leadingIcon = {
                                        if (label == library.status(anime))
                                            Icon(Icons.Rounded.Check, null)
                                    },
                                    onClick = {
                                        library.setStatus(anime, label)
                                        statuses = false
                                    }
                                )
                            }
                        }
                    }
                }
                AnimatedVisibility(
                    visible = modeOptions,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    PlaybackModeStrip(
                        continuous = library.mergeEpisodes,
                        onChoose = { selected ->
                            library.updateMergeEpisodes(selected)
                            modeOptions = false
                        },
                        modifier = Modifier.fillMaxWidth()
                            .padding(top = 8.dp, bottom = 3.dp)
                    )
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                SectionHeader(
                    "Серии",
                    when {
                        loading -> "Загружаем с AniLibria"
                        episodes.isNotEmpty() -> "Доступно: " + episodes.size
                        else -> null
                    }
                )
            }
        }
        if (episodes.isNotEmpty()) {
            items(episodes, key = { "episode-" + it }) { number ->
                val isVisible by remember(number) {
                    derivedStateOf {
                        listState.layoutInfo.visibleItemsInfo.any {
                            it.key == "episode-" + number
                        }
                    }
                }
                val options = variants[number].orEmpty()
                val preview = options.minByOrNull { it.popularity }
                val detailsForEpisode = options.firstOrNull()?.details
                if (preview != null) {
                    EpisodeCard(
                        episode = number,
                        details = detailsForEpisode,
                        stream = preview,
                        selected = number == episode,
                        active = isVisible && !library.dataSaver,
                        progress = library.progressPercent(
                            anime, number, detailsForEpisode?.durationMs ?: 0L
                        ),
                        positionMs = library.episodePosition(anime, number),
                        onPlay = {
                            episode = number
                            onWatch(number, null)
                        }
                    )
                }
            }
        } else {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        .background(Panel, RoundedCornerShape(16.dp)).padding(15.dp)
                ) {
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(
                        if (loading) "Получаем доступные эпизоды…"
                        else "Серии AniLibria пока не найдены",
                        color = Paper, fontSize = 12.sp
                    )
                    if (!loading) {
                        Text(problem ?: "Релиз пока не опубликован", color = Muted,
                            fontSize = 11.sp)
                        TextButton(onClick = { refresh++ }) { Text("Повторить") }
                    }
                }
            }
        }
        if (anime.description.isNotBlank()) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SectionHeader("Описание")
                    Text(
                        anime.description,
                        color = Muted, fontSize = 12.sp, lineHeight = 18.sp,
                        maxLines = if (expanded) Int.MAX_VALUE else 5,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (anime.description.length > 240)
                        TextButton(onClick = { expanded = !expanded }) {
                            Text(if (expanded) "Свернуть" else "Читать полностью",
                                fontSize = 12.sp)
                        }
                }
            }
        }
        if (anime.genres.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SectionHeader("Жанры")
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        anime.genres.forEach { genre ->
                            Box(Modifier.background(Panel, RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 7.dp)) {
                                Text(genre, fontSize = 11.sp, color = Paper)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeCard(
    episode: Int,
    details: EpisodeDetails?,
    stream: StreamVariant,
    selected: Boolean,
    active: Boolean,
    progress: Int,
    positionMs: Long,
    onPlay: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(if (selected) PanelAlt else Panel)
            .clickable(onClick = onPlay)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Column(Modifier.width(37.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(episode.toString(), color = if (selected) accent else Paper,
                fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text("№", color = Muted, fontSize = 10.sp)
        }
        AnimatedEpisodePreview(
            stream,
            Modifier.width(137.dp).height(76.dp),
            active = active
        )
        Column(Modifier.weight(1f)) {
            Text(
                details?.title?.ifBlank { "Серия " + episode } ?: "Серия " + episode,
                color = Paper, fontSize = 12.sp, lineHeight = 16.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium
            )
            if ((details?.durationMs ?: 0L) > 0L) {
                Spacer(Modifier.height(5.dp))
                Text(episodeDuration(details!!.durationMs),
                    color = Muted, fontSize = 10.sp)
            }
            Spacer(Modifier.height(5.dp))
            if (progress > 0) {
                Text(
                    if (progress >= 95) "Просмотрено"
                    else "Продолжить: " + episodeDuration(positionMs),
                    color = if (progress >= 95) accent else Muted,
                    fontSize = 10.sp, maxLines = 1
                )
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth().height(3.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = accent,
                    trackColor = Edge
                )
            } else {
                Icon(Icons.Rounded.PlayCircle, "Смотреть серию",
                    tint = accent, modifier = Modifier.size(21.dp))
            }
        }
    }
}

fun episodeDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "Неизвестно"
    val seconds = durationMs / 1000L
    return if (seconds >= 3600L)
        "%d:%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60, seconds % 60)
    else "%d:%02d".format(seconds / 60, seconds % 60)
}
