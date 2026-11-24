package ru.lisurgut.oalab

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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

/** Personal, offline-scored recommendations; only AniLibria catalog pages are fetched. */
@Composable
fun RecommendationsScreen(
    library: LocalLibrary,
    onOpen: (Anime) -> Unit
) {
    val initial = remember(library) {
        (library.recommendationCandidates() + library.cachedCatalog())
            .filter { it.id.startsWith("aniliberty:") }.distinctBy { it.id }
    }
    var candidates by remember(library) { mutableStateOf(initial) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var mode by remember { mutableStateOf(RecommendationMode.PERSONAL) }
    val listState = rememberLazyListState()

    LaunchedEffect(refresh) {
        if (refresh == 0 && library.recommendationCacheFresh()) return@LaunchedEffect
        loading = true
        message = null
        val searches = listOf(
            "Высокий рейтинг" to 1,
            "Высокий рейтинг" to 2,
            "Популярные" to 1,
            "Популярные" to 2,
            "Новые" to 1
        )
        try {
            val results = coroutineScope {
                searches.map { (sort, page) ->
                    async(Dispatchers.IO) {
                        runCatching {
                            AnimeApi.load(
                                provider = "AniLibria",
                                sort = sort,
                                page = page
                            )
                        }
                    }
                }.awaitAll()
            }
            val successful = results.mapNotNull { it.getOrNull() }.flatten()
            if (successful.isNotEmpty()) {
                candidates = (successful + initial)
                    .filter { it.id.startsWith("aniliberty:") }
                    .distinctBy { it.id }
                library.saveRecommendationCandidates(candidates)
            } else {
                message = results.firstNotNullOfOrNull {
                    it.exceptionOrNull()?.localizedMessage
                } ?: "Каталог рекомендаций временно недоступен"
            }
        } catch (error: Exception) {
            message = error.localizedMessage ?: "Не удалось обновить рекомендации"
        } finally {
            loading = false
        }
    }

    // Copying observable history here causes reranking when the user watches,
    // favorites, hides or marks an anime as finished.
    val history = library.entries.toList()
    val rated = history.count {
        it.favorite || it.status == "Просмотрено" ||
            it.status == "Смотрю" || it.lastPosition > 0L
    }
    val suggestions = remember(
        candidates, history, mode, library.recommendationGenres,
        library.hiddenRecommendations
    ) {
        RecommendationEngine.rank(
            candidates = candidates,
            history = history,
            explicitGenres = library.recommendationGenres,
            hidden = library.hiddenRecommendations,
            mode = mode
        )
    }
    val availableGenres = remember(candidates, history, library.recommendationGenres) {
        val fromProvider = candidates.flatMap { it.genres }
            .filter { it.isNotBlank() }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .take(12).map { it.key }
        (library.recommendationGenres.toList() + history.flatMap { it.anime.genres }
            .take(10) + fromProvider).distinct().take(18)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(Ink).statusBarsPadding(),
        contentPadding = PaddingValues(
            start = 15.dp, end = 15.dp, top = 14.dp, bottom = 116.dp
        ),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Для тебя", color = Paper, fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (rated == 0)
                            "Начни смотреть аниме — рекомендации станут точнее"
                        else "Учитываю " + rated + " аниме из твоей истории",
                        color = Muted, fontSize = 11.sp
                    )
                }
                FilledTonalIconButton(
                    onClick = { refresh++ }, enabled = !loading,
                    modifier = Modifier.size(38.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Rounded.Refresh,
                        "Обновить подборку",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    RecommendationMode.PERSONAL to "Для тебя",
                    RecommendationMode.GENRES to "По жанрам",
                    RecommendationMode.DISCOVER to "Открытия"
                ).forEach { (option, text) ->
                    Box(Modifier.weight(1f)) {
                        FlatChip(text, selected = mode == option) { mode = option }
                    }
                }
            }
        }
        item {
            if (availableGenres.isNotEmpty()) {
                Text(
                    "Интересующие жанры", color = Paper,
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 3.dp)
                )
                Spacer(Modifier.height(7.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    availableGenres.forEach { genre ->
                        FilterChip(
                            selected = genre in library.recommendationGenres,
                            onClick = { library.toggleRecommendationGenre(genre) },
                            label = {
                                Text(
                                    genre, fontSize = 11.sp,
                                    maxLines = 1
                                )
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    "Выбирай жанры: они влияют на порядок и состав подборки",
                    color = Muted, fontSize = 10.sp
                )
            }
        }
        item {
            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text(
                    "Ищу подходящее в AniLibria…", color = Muted, fontSize = 11.sp
                )
            } else if (message != null) {
                Text(
                    "Не удалось обновить каталог: " + message,
                    color = Muted, fontSize = 11.sp
                )
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    when (mode) {
                        RecommendationMode.PERSONAL -> "Подобрано под твои интересы"
                        RecommendationMode.GENRES -> "По любимым жанрам"
                        RecommendationMode.DISCOVER -> "Попробуй что-то новое"
                    },
                    modifier = Modifier.weight(1f),
                    color = Paper, fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (library.hiddenRecommendations.isNotEmpty()) {
                    TextButton(
                        onClick = library::resetHiddenRecommendations,
                        contentPadding = PaddingValues(horizontal = 5.dp)
                    ) {
                        Text("Вернуть скрытые", fontSize = 10.sp)
                    }
                }
            }
        }

        if (suggestions.isNotEmpty()) {
            items(suggestions, key = { it.anime.id }) { recommendation ->
                RecommendationItem(
                    recommendation = recommendation,
                    favorite = library.isFavorite(recommendation.anime),
                    onOpen = { onOpen(recommendation.anime) },
                    onFavorite = { library.toggleFavorite(recommendation.anime) },
                    onHide = { library.hideRecommendation(recommendation.anime) }
                )
            }
        } else {
            item {
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Panel).padding(21.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Rounded.AutoAwesome, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(27.dp)
                    )
                    Text(
                        if (loading) "Собираю подборку…"
                        else "Пока нечего рекомендовать",
                        color = Paper,
                        fontWeight = FontWeight.SemiBold, fontSize = 14.sp
                    )
                    Text(
                        if (loading) "Загружаю реальные аниме из каталога"
                        else "Попробуй другие жанры или обнови подборку",
                        color = Muted, fontSize = 11.sp
                    )
                    if (!loading) TextButton(onClick = { refresh++ }) {
                        Text("Повторить")
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendationItem(
    recommendation: AnimeRecommendation,
    favorite: Boolean,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    onHide: () -> Unit
) {
    val anime = recommendation.anime
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(17.dp))
            .background(Panel)
            .clickable(onClick = onOpen)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(
            anime.cover,
            Modifier.size(width = 82.dp, height = 115.dp)
                .clip(RoundedCornerShape(11.dp)),
            anime.title
        )
        Column(
            modifier = Modifier.weight(1f).heightIn(min = 113.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                anime.title, maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = Paper, fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp, lineHeight = 17.sp
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (anime.year > 0) {
                    Text(anime.year.toString(), color = Muted, fontSize = 10.sp)
                }
                if (anime.score > 0) {
                    Icon(Icons.Rounded.Star, null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Text(
                        (anime.score / 10.0).toString(),
                        color = Muted, fontSize = 10.sp
                    )
                }
            }
            Text(
                recommendation.reason, maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 11.sp, lineHeight = 15.sp
            )
            if (recommendation.matchedGenres.isNotEmpty()) {
                Text(
                    recommendation.matchedGenres.take(2).joinToString(" · "),
                    color = Muted, fontSize = 10.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.weight(1f))
            Row(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onFavorite,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        if (favorite) Icons.Rounded.Favorite
                        else Icons.Rounded.FavoriteBorder,
                        if (favorite) "Убрать из избранного" else "В избранное",
                        tint = if (favorite) MaterialTheme.colorScheme.primary else Muted,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onHide,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        Icons.Rounded.VisibilityOff,
                        "Не рекомендовать это аниме",
                        tint = Muted, modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "Открыть", color = Paper, fontSize = 10.sp
                )
                Icon(
                    Icons.Rounded.ChevronRight, null,
                    tint = Muted, modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
