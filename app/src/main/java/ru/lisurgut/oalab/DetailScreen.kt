package ru.lisurgut.oalab

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Single source detail: real AniLibria episodes, no dubbing/provider selection. */
@Composable
fun DetailScreen(
    anime: Anime,
    library: LocalLibrary,
    onBack: () -> Unit,
    onWatch: (Int, String?) -> Unit
) {
    var loading by remember(anime.id) { mutableStateOf(true) }
    var failure by remember(anime.id) { mutableStateOf<String?>(null) }
    var expanded by remember(anime.id) { mutableStateOf(false) }
    var showStatuses by remember { mutableStateOf(false) }
    var episode by remember(anime.id) {
        mutableIntStateOf(library.entry(anime)?.lastEpisode ?: 1)
    }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(anime.id, refresh) {
        loading = true
        failure = null
        try {
            library.saveResolvedStreams(anime, AniLibertyApi.resolve(anime))
        } catch (e: Exception) {
            failure = e.localizedMessage ?: "Видеосервис недоступен"
        } finally {
            loading = false
        }
    }
    val episodes = library.variantsFor(anime).map { it.episode }.distinct().sorted()
    LaunchedEffect(episodes) {
        if (episodes.isNotEmpty() && episode !in episodes) {
            episode = episodes.first()
        }
    }
    val status = library.status(anime)

    LazyColumn(
        Modifier.fillMaxSize().background(Ink).statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 36.dp)
    ) {
        item {
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 7.dp)
                    .height(213.dp).clip(RoundedCornerShape(22.dp))
            ) {
                Cover(anime.banner.ifBlank { anime.cover }, Modifier.fillMaxSize(), anime.title)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = .24f),
                    .50f to Color.Black.copy(alpha = .46f),
                    1f to Color.Black.copy(alpha = .95f)
                )))
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                        .background(Color.Black.copy(alpha = .55f), CircleShape)
                ) {
                    Icon(Icons.Rounded.ArrowBack, "Назад", tint = Color.White)
                }
                Row(
                    Modifier.align(Alignment.BottomStart).padding(14.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    Cover(
                        anime.cover,
                        Modifier.width(77.dp).height(105.dp).clip(RoundedCornerShape(12.dp)),
                        anime.title
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            anime.title, color = Color.White,
                            fontSize = 19.sp, lineHeight = 22.sp,
                            maxLines = 3, overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (anime.year > 0) {
                                Box(Modifier.size(7.dp)
                                    .background(yearTint(anime.year), CircleShape))
                                Text(anime.year.toString(), color = Color.White, fontSize = 11.sp)
                            }
                            Text(
                                anime.format.ifBlank { "Аниме" },
                                color = Color.White.copy(alpha = .83f), fontSize = 11.sp
                            )
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
                    if (episodes.isNotEmpty())
                        MetricChip(episodes.size.toString() + " сер.", Icons.Rounded.Movie)
                }
                Spacer(Modifier.height(13.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { onWatch(episode, null) },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Смотреть", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    FilledTonalIconButton(
                        onClick = { library.toggleFavorite(anime) },
                        modifier = Modifier.size(44.dp), shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            if (library.isFavorite(anime)) Icons.Rounded.Favorite
                            else Icons.Rounded.FavoriteBorder,
                            "Избранное", modifier = Modifier.size(20.dp)
                        )
                    }
                    Box {
                        FilledTonalIconButton(
                            onClick = { showStatuses = true },
                            modifier = Modifier.size(44.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Checklist, "Статус", modifier = Modifier.size(20.dp))
                        }
                        DropdownMenu(
                            expanded = showStatuses,
                            onDismissRequest = { showStatuses = false }
                        ) {
                            listOf(
                                "Не смотрел", "В планах", "Смотрю",
                                "Просмотрено", "Брошено"
                            ).forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    leadingIcon = {
                                        if (option == status) Icon(Icons.Rounded.Check, null)
                                    },
                                    onClick = {
                                        library.setStatus(anime, option)
                                        showStatuses = false
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text("Статус: " + status, fontSize = 11.sp, color = Muted)
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                SectionHeader("Серии", if (loading) "Загружаем с AniLibria"
                    else if (episodes.isNotEmpty()) "Доступно: " + episodes.size else null)
            }
        }
        if (episodes.isNotEmpty()) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    items(episodes) { number ->
                        FlatChip("Серия " + number, selected = number == episode) {
                            episode = number
                        }
                    }
                }
            }
        } else {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        .background(Panel, RoundedCornerShape(16.dp))
                        .padding(15.dp)
                ) {
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(
                        if (loading) "Получаем доступные эпизоды…"
                        else "Серии AniLibria пока не найдены",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!loading) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            failure ?: "Возможно, релиз ещё не опубликован.",
                            color = Muted, fontSize = 11.sp
                        )
                        TextButton(onClick = { refresh++ }) { Text("Обновить") }
                    }
                }
            }
        }
        if (anime.description.isNotBlank()) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SectionHeader("Описание")
                    Text(
                        anime.description, color = Muted,
                        fontSize = 12.sp, lineHeight = 18.sp,
                        maxLines = if (expanded) Int.MAX_VALUE else 5,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (anime.description.length > 240) {
                        TextButton(onClick = { expanded = !expanded }) {
                            Text(if (expanded) "Свернуть" else "Читать полностью",
                                fontSize = 12.sp)
                        }
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
                            Box(
                                Modifier.background(Panel, RoundedCornerShape(11.dp))
                                    .padding(horizontal = 11.dp, vertical = 8.dp)
                            ) {
                                Text(genre, color = Paper, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
