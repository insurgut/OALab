package ru.lisurgut.oalab

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
fun HomeScreen(
    catalog: List<Anime>,
    library: LocalLibrary,
    loading: Boolean,
    error: String?,
    onOpen: (Anime) -> Unit,
    onCategory: (String) -> Unit,
    onViewAll: () -> Unit,
    onRetry: () -> Unit
) {
    val featured = catalog.firstOrNull { it.banner.isNotBlank() } ?: catalog.firstOrNull()
    val genres = library.preferredGenres().take(3)
    val recommended = catalog
        .filter { !library.watched().any { watched -> watched.id == it.id } }
        .sortedWith(compareByDescending<Anime> { anime -> anime.genres.count { it in genres } }
            .thenByDescending { it.score })
        .take(12)

    LazyColumn(
        Modifier.fillMaxSize().background(Ink).statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 21.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Eyebrow("Твоё аниме-пространство")
                    Spacer(Modifier.height(4.dp))
                    Text("OAlab", fontSize = 36.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.5).sp)
                }
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(16.dp)).background(PanelAlt),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = "Рекомендации", tint = Paper)
                }
            }
        }
        if (featured != null) {
            item {
                Box(
                    Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(355.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Panel)
                        .clickable { onOpen(featured) }
                ) {
                    Cover(featured.banner.ifBlank { featured.cover }, Modifier.fillMaxSize())
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = .02f),
                                .35f to Color.Black.copy(alpha = .15f),
                                1f to Color.Black.copy(alpha = .97f)
                            )
                        )
                    )
                    Column(
                        Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(24.dp)
                    ) {
                        Eyebrow("Выбор редакции каталога")
                        Spacer(Modifier.height(10.dp))
                        Text(
                            featured.title,
                            color = Paper,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            lineHeight = 32.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(9.dp))
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            AnimeYearMark(featured.year)
                            Text(
                                listOfNotNull(
                                    featured.format.takeIf { it.isNotBlank() },
                                    featured.score.takeIf { it > 0 }?.let { (it / 10f).toString() + " / 10" }
                                ).joinToString("  ·  "),
                                color = Color.White.copy(alpha = .76f), fontSize = 12.sp
                            )
                        }
                        Spacer(Modifier.height(19.dp))
                        Button(
                            onClick = { onOpen(featured) },
                            shape = RoundedCornerShape(17.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Paper, contentColor = Ink)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Подробнее", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        if (loading && catalog.isEmpty()) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) }
        }
        if (error != null) {
            item {
                ErrorPanel(error, onRetry)
            }
        }
        if (catalog.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader("В центре внимания", "Самое интересное прямо сейчас", onViewAll)
                }
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    items(catalog.take(14), key = { it.id }) { anime ->
                        PosterCard(
                            anime = anime,
                            favorite = library.isFavorite(anime),
                            modifier = Modifier.width(146.dp),
                            onOpen = { onOpen(anime) },
                            onFavorite = { library.toggleFavorite(anime) }
                        )
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader(
                        "Подобрано для тебя",
                        if (genres.isEmpty()) "Начни добавлять любимое — рекомендации станут точнее"
                        else "По твоей библиотеке: " + genres.joinToString(", ")
                    )
                }
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    items(recommended, key = { it.id }) { anime ->
                        PosterCard(
                            anime, library.isFavorite(anime), Modifier.width(146.dp),
                            onOpen = { onOpen(anime) },
                            onFavorite = { library.toggleFavorite(anime) }
                        )
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader("Выбери настроение", "Жанры и направления")
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AnimeApi.categories.drop(1).forEach { item ->
                            FlatChip(item) { onCategory(item) }
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader("Продолжить просмотр", "Твоя история остаётся на устройстве")
                    val progress = library.inProgress()
                    if (progress.isEmpty()) {
                        Text("Пока пусто. Выбери тайтл и начни смотреть.", color = Muted, fontSize = 13.sp)
                    } else {
                        progress.take(5).forEach { entry ->
                            LibraryRow(entry.anime, entry, { onOpen(entry.anime) })
                        }
                    }
                }
            }
        } else if (!loading && error == null) {
            item {
                EmptyState(Icons.Rounded.MovieFilter, "Каталог пуст", "Попробуй обновить страницу")
            }
        }
    }
}

@Composable
fun ErrorPanel(error: String, onRetry: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 19.dp, vertical = 12.dp)
            .fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(PanelAlt).padding(15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.WifiOff, null, Modifier.size(21.dp), tint = Muted)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text("Не удалось загрузить каталог", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(error.take(160), fontSize = 11.sp, color = Muted, maxLines = 3)
        }
        TextButton(onClick = onRetry) { Text("Повтор") }
    }
}

@Composable
fun CatalogScreen(
    catalog: List<Anime>,
    library: LocalLibrary,
    search: String,
    onSearch: (String) -> Unit,
    category: String,
    onCategory: (String) -> Unit,
    sorting: String,
    onSorting: (String) -> Unit,
    loading: Boolean,
    error: String?,
    onOpen: (Anime) -> Unit,
    onMore: () -> Unit,
    onRetry: () -> Unit
) {
    var sortExpanded by remember { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize().background(Ink).statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 115.dp)
    ) {
        item {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp)) {
                Eyebrow(library.catalogProvider + " / Каталог")
                Spacer(Modifier.height(6.dp))
                Text("Исследуй аниме", fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
                Spacer(Modifier.height(17.dp))
                OutlinedTextField(
                    value = search,
                    onValueChange = onSearch,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Muted) },
                    trailingIcon = {
                        if (search.isNotEmpty())
                            IconButton(onClick = { onSearch("") }) { Icon(Icons.Rounded.Close, "Очистить", tint = Muted) }
                    },
                    placeholder = { Text("Название аниме…", color = Muted) },
                    shape = RoundedCornerShape(21.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Panel,
                        unfocusedContainerColor = Panel,
                        focusedBorderColor = Edge,
                        unfocusedBorderColor = Edge
                    )
                )
                Spacer(Modifier.height(14.dp))
            }
        }
        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnimeApi.categories.forEach { item ->
                    FlatChip(item, item == category) { onCategory(item) }
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Результаты", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Box {
                    OutlinedButton(
                        onClick = { sortExpanded = true },
                        border = androidx.compose.foundation.BorderStroke(1.dp, Edge),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Rounded.Sort, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(sorting, fontSize = 11.sp)
                    }
                    DropdownMenu(
                        expanded = sortExpanded,
                        onDismissRequest = { sortExpanded = false },
                        containerColor = PanelAlt
                    ) {
                        AnimeApi.sorting.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option, fontSize = 13.sp) },
                                onClick = { onSorting(option); sortExpanded = false }
                            )
                        }
                    }
                }
            }
        }
        if (error != null) item { ErrorPanel(error, onRetry) }
        items(catalog.chunked(2)) { row ->
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                row.forEach { anime ->
                    PosterCard(
                        anime, library.isFavorite(anime), Modifier.weight(1f),
                        { onOpen(anime) }, { library.toggleFavorite(anime) }
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (loading) {
            item {
                Box(Modifier.fillMaxWidth().padding(22.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Paper, modifier = Modifier.size(25.dp), strokeWidth = 2.dp)
                }
            }
        } else if (catalog.isNotEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(22.dp), contentAlignment = Alignment.Center) {
                    OutlinedButton(
                        onClick = onMore,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Edge),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text("Загрузить ещё")
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Rounded.ExpandMore, null)
                    }
                }
            }
        } else if (error == null) {
            item { EmptyState(Icons.Rounded.SearchOff, "Ничего не найдено", "Попробуй другое название или жанр") }
        }
    }
}

@Composable
fun LibraryRow(anime: Anime, entry: LibraryEntry, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp)
            .clip(RoundedCornerShape(17.dp)).background(Panel).clickable(onClick = onOpen)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(anime.cover, Modifier.size(width = 65.dp, height = 84.dp).clip(RoundedCornerShape(11.dp)))
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(anime.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(7.dp))
            Text(entry.status, fontSize = 11.sp, color = Muted)
            if (entry.lastPosition > 0L) {
                Spacer(Modifier.height(5.dp))
                Text("Серия " + entry.lastEpisode + " · " + (entry.lastPosition / 60000) + " мин", fontSize = 11.sp, color = Muted)
            }
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = Muted, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun MyLibraryScreen(library: LocalLibrary, onOpen: (Anime) -> Unit) {
    var filter by remember { mutableStateOf("Избранное") }
    val records = when (filter) {
        "Избранное" -> library.entries.filter { it.favorite }
        "Смотрю" -> library.entries.filter { it.status == "Смотрю" }
        "Просмотрено" -> library.entries.filter { it.status == "Просмотрено" }
        "В планах" -> library.entries.filter { it.status == "В планах" }
        else -> library.entries.toList()
    }
    LazyColumn(
        Modifier.fillMaxSize().background(Ink).statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
                Eyebrow("Приватная коллекция")
                Spacer(Modifier.height(5.dp))
                Text("Моя библиотека", fontSize = 29.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Все сохранено на этом устройстве. Никакого аккаунта.", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(18.dp))
            }
        }
        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Избранное", "Смотрю", "Просмотрено", "В планах", "Все").forEach {
                    FlatChip(it, it == filter) { filter = it }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        if (records.isEmpty()) {
            item {
                EmptyState(
                    Icons.Rounded.BookmarkBorder,
                    "Здесь пока пусто",
                    "Добавляй аниме в избранное или отмечай свой прогресс"
                )
            }
        } else {
            item {
                Text(
                    records.size.toString() + " тайтлов",
                    color = Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 7.dp)
                )
            }
            items(records, key = { it.anime.id }) { entry ->
                Box(Modifier.padding(horizontal = 20.dp)) {
                    LibraryRow(entry.anime, entry) { onOpen(entry.anime) }
                }
            }
        }
    }
}
