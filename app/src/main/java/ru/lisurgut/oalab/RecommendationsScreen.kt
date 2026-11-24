package ru.lisurgut.oalab

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private enum class RecommendationYears(val label: String) {
    ALL("Любые"), RECENT("С 2020"), CLASSICS("До 2015")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecommendationsScreen(library: LocalLibrary, onOpen: (Anime) -> Unit) {
    val cached = remember(library) {
        (library.recommendationCandidates() + library.cachedCatalog())
            .filter { it.id.startsWith("aniliberty:") }
            .distinctBy { it.id }
    }
    var candidates by remember(library) { mutableStateOf(cached) }
    var refresh by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var filtersOpen by remember { mutableStateOf(false) }
    var reasonFilter by remember { mutableStateOf<RecommendationKind?>(null) }
    var ratingFloor by remember { mutableIntStateOf(0) }
    var years by remember { mutableStateOf(RecommendationYears.ALL) }
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(refresh) {
        if (refresh == 0 && library.recommendationCacheFresh()) return@LaunchedEffect
        loading = true
        error = null
        try {
            // Fetch from distinct real AniLibria pages; no fake recommendations.
            val pages = listOf(
                "Высокий рейтинг" to 1,
                "Высокий рейтинг" to 2,
                "Популярные" to 1,
                "Популярные" to 2,
                "Новые" to 1
            )
            val results = coroutineScope {
                pages.map { (sort, page) ->
                    async(Dispatchers.IO) {
                        runCatching {
                            AnimeApi.load(provider = "AniLibria", sort = sort, page = page)
                        }
                    }
                }.awaitAll()
            }
            val fetched = results.mapNotNull { it.getOrNull() }.flatten()
            if (fetched.isNotEmpty()) {
                candidates = (fetched + cached).distinctBy { it.id }
                library.saveRecommendationCandidates(candidates)
            } else {
                error = "AniLibria сейчас недоступна"
            }
        } catch (_: Exception) {
            error = "Не удалось обновить каталог"
        } finally {
            loading = false
        }
    }

    val history = library.entries.toList()
    val watchedCount = history.count {
        it.status == "Смотрю" || it.status == "Просмотрено" ||
            it.favorite || it.lastPosition > 0L
    }
    val selectedGenres = library.recommendationGenres
    val hidden = library.hiddenRecommendations
    val mode = library.recommendationMode
    val choices = remember(candidates, history, mode, selectedGenres, hidden,
        reasonFilter, ratingFloor, years) {
        RecommendationEngine.rank(
            candidates = candidates, history = history,
            explicitGenres = selectedGenres, hidden = hidden, mode = mode,
            kindFilter = reasonFilter, minRating = ratingFloor,
            yearFrom = if (years == RecommendationYears.RECENT) 2020 else null,
            yearTo = if (years == RecommendationYears.CLASSICS) 2015 else null
        )
    }
    val genres = remember(candidates, history, selectedGenres) {
        val provider = candidates.asSequence()
            .flatMap { it.genres.asSequence() }
            .filter { it.isNotBlank() }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.take(18).map { it.key }
        (selectedGenres.toList().sorted() + history.flatMap { it.anime.genres }.take(8) +
            provider).distinct().take(24)
    }
    val filterCount = (if (reasonFilter != null) 1 else 0) +
        (if (ratingFloor > 0) 1 else 0) +
        (if (years != RecommendationYears.ALL) 1 else 0) +
        (if (selectedGenres.isNotEmpty()) 1 else 0)

    val reset: () -> Unit = {
        reasonFilter = null
        ratingFloor = 0
        years = RecommendationYears.ALL
        library.clearRecommendationGenres()
    }
    val hide: (Anime) -> Unit = { anime ->
        library.hideRecommendation(anime)
        scope.launch {
            val choice = snackbarHost.showSnackbar(
                message = "Скрыто из рекомендаций",
                actionLabel = "Отменить",
                withDismissAction = true,
                duration = SnackbarDuration.Short
            )
            if (choice == SnackbarResult.ActionPerformed) {
                library.unhideRecommendation(anime)
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Ink)) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 14.dp, bottom = 115.dp
            )
        ) {
            item(key = "header") {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Рекомендации",
                            color = Paper, fontSize = 26.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-.9).sp
                        )
                        Text(
                            if (watchedCount > 0)
                                "На основе " + watchedCount + " аниме из твоей истории"
                            else "Выбери жанры или начни смотреть аниме",
                            color = Muted, fontSize = 11.sp
                        )
                    }
                    IconButton(onClick = { refresh++ }, enabled = !loading) {
                        Icon(
                            Icons.Rounded.Refresh, "Обновить",
                            tint = Muted, modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                RecommendationTabs(mode, library::updateRecommendationMode)
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .background(Panel)
                            .clickable { filtersOpen = true }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Tune, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            "Фильтры" + if (filterCount > 0) " · " + filterCount else "",
                            color = Paper, fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (filterCount > 0) {
                        TextButton(onClick = reset) {
                            Text("Сбросить", fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        choices.size.toString() + " аниме",
                        color = Muted, fontSize = 11.sp
                    )
                }
                if (selectedGenres.isNotEmpty() || reasonFilter != null) {
                    Text(
                        listOfNotNull(
                            reasonFilter?.label,
                            selectedGenres.take(3).joinToString(", ")
                                .takeIf { it.isNotBlank() }
                        ).joinToString(" · "),
                        color = Muted, fontSize = 10.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (loading) {
                    LinearProgressIndicator(
                        Modifier.fillMaxWidth().height(2.dp)
                            .clip(CircleShape)
                    )
                    Spacer(Modifier.height(9.dp))
                } else if (error != null) {
                    Text(
                        "Нет сети — показаны сохранённые рекомендации",
                        color = Muted, fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 9.dp)
                    )
                }
            }

            if (choices.isNotEmpty()) {
                items(choices, key = { it.anime.id }) { suggestion ->
                    RecommendationCompactItem(
                        recommendation = suggestion,
                        favorite = library.isFavorite(suggestion.anime),
                        onOpen = { onOpen(suggestion.anime) },
                        onFavorite = {
                            library.toggleFavorite(suggestion.anime)
                        },
                        onHide = { hide(suggestion.anime) },
                        onSource = onOpen
                    )
                }
                item(key = "privacy") {
                    Spacer(Modifier.height(13.dp))
                    Text(
                        "Подборка рассчитывается на устройстве по реальной истории " +
                            "просмотра. Уже добавленное в библиотеку не повторяется.",
                        color = Muted, fontSize = 10.sp, lineHeight = 15.sp
                    )
                    if (hidden.isNotEmpty()) {
                        TextButton(
                            onClick = library::resetHiddenRecommendations,
                            contentPadding = PaddingValues(0.dp)
                        ) { Text("Вернуть скрытые", fontSize = 11.sp) }
                    }
                }
            } else {
                item(key = "empty") {
                    Column(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(15.dp))
                            .background(Panel)
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Rounded.AutoAwesome, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            if (loading) "Подбираю аниме…"
                            else if (filterCount > 0) "По фильтрам ничего не найдено"
                            else "Рекомендации пока пусты",
                            color = Paper, fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(7.dp))
                        Text(
                            if (filterCount > 0)
                                "Попробуй ослабить фильтры"
                            else "Загрузи каталог или добавь аниме в библиотеку",
                            color = Muted, fontSize = 11.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = {
                            if (filterCount > 0) reset() else refresh += 1
                        }) {
                            Text(if (filterCount > 0) "Сбросить фильтры" else "Повторить")
                        }
                        if (hidden.isNotEmpty()) {
                            TextButton(onClick = library::resetHiddenRecommendations) {
                                Text("Вернуть скрытые")
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHost,
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(start = 12.dp, end = 12.dp, bottom = 82.dp)
        )
    }

    if (filtersOpen) {
        ModalBottomSheet(
            onDismissRequest = { filtersOpen = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 18.dp, end = 18.dp, bottom = 25.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Фильтры", color = Paper,
                        fontSize = 21.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = reset) {
                        Text("Очистить", fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "На основе чего предлагать",
                    color = Paper, fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(7.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    listOf(null) .plus(RecommendationKind.entries).forEach { type ->
                        FilterChip(
                            selected = reasonFilter == type,
                            onClick = { reasonFilter = type },
                            label = {
                                Text(type?.label ?: "Все причины", fontSize = 11.sp)
                            },
                            shape = RoundedCornerShape(11.dp)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Любимые жанры", color = Paper,
                    fontSize = 13.sp, fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(6.dp))
                if (genres.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        genres.take(18).forEach { genre ->
                            FilterChip(
                                selected = genre in selectedGenres,
                                onClick = { library.toggleRecommendationGenre(genre) },
                                label = { Text(genre, fontSize = 11.sp) },
                                shape = RoundedCornerShape(11.dp)
                            )
                        }
                    }
                } else {
                    Text("Жанры появятся после загрузки каталога", color = Muted,
                        fontSize = 11.sp)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Оценка", color = Paper,
                    fontSize = 13.sp, fontWeight = FontWeight.Medium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf(0 to "Любая", 70 to "7+", 80 to "8+").forEach { (rating, title) ->
                        FilterChip(
                            selected = ratingFloor == rating,
                            onClick = { ratingFloor = rating },
                            label = { Text(title, fontSize = 11.sp) },
                            shape = RoundedCornerShape(11.dp)
                        )
                    }
                }
                Spacer(Modifier.height(11.dp))
                Text(
                    "Год выхода", color = Paper,
                    fontSize = 13.sp, fontWeight = FontWeight.Medium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    RecommendationYears.entries.forEach { era ->
                        FilterChip(
                            selected = era == years,
                            onClick = { years = era },
                            label = { Text(era.label, fontSize = 11.sp) },
                            shape = RoundedCornerShape(11.dp)
                        )
                    }
                }
                Spacer(Modifier.height(15.dp))
                Button(
                    onClick = { filtersOpen = false },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Показать " + choices.size + " аниме")
                }
            }
        }
    }
}
