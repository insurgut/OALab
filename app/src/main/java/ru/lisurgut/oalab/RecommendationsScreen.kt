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
import kotlinx.coroutines.launch

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
    val mode = library.recommendationMode
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
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

    val hideWithUndo: (Anime) -> Unit = { anime ->
        library.hideRecommendation(anime)
        scope.launch {
            val response = snackbarHost.showSnackbar(
                message = "Скрыто из рекомендаций",
                actionLabel = "Отменить",
                withDismissAction = true,
                duration = SnackbarDuration.Short
            )
            if (response == SnackbarResult.ActionPerformed) {
                library.unhideRecommendation(anime)
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Ink)) {
        LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(
            start = 15.dp, end = 15.dp, top = 14.dp, bottom = 116.dp
        ),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        item(key = "reco-header") {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Eyebrow("ТВОЯ ПОДБОРКА")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Что посмотреть", color = Paper, fontSize = 26.sp,
                        letterSpacing = (-1.0).sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                IconButton(
                    onClick = { refresh++ }, enabled = !loading,
                    modifier = Modifier.size(39.dp)
                        .clip(RoundedCornerShape(13.dp)).background(Panel)
                ) {
                    Icon(Icons.Rounded.Refresh, "Обновить подборку",
                        tint = Paper, modifier = Modifier.size(19.dp))
                }
            }
            Text(
                if (rated == 0)
                    "Начни с любимых жанров — подборка подстроится под тебя"
                else "По мотивам " + rated + " аниме из твоей истории",
                color = Muted, fontSize = 12.sp, lineHeight = 16.sp
            )
            Spacer(Modifier.height(15.dp))
        }
        item(key = "reco-tabs") {
            RecommendationTabs(mode, library::updateRecommendationMode)
            Spacer(Modifier.height(14.dp))
        }
        item(key = "reco-taste") {
            RecommendationTasteEditor(
                genres = availableGenres,
                chosen = library.recommendationGenres,
                onToggle = library::toggleRecommendationGenre,
                onReset = library::clearRecommendationGenres
            )
            Spacer(Modifier.height(17.dp))
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
        item(key = "reco-spotlight") {
            suggestions.firstOrNull()?.let { best ->
                RecommendationSpotlightCard(
                    recommendation = best,
                    favorite = library.isFavorite(best.anime),
                    onOpen = { onOpen(best.anime) },
                    onFavorite = { library.toggleFavorite(best.anime) },
                    onHide = { hideWithUndo(best.anime) }
                )
                Spacer(Modifier.height(21.dp))
            }
        }
        item(key = "reco-list-title") {
            if (suggestions.size > 1) {
                Row(
                    Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        when (mode) {
                            RecommendationMode.PERSONAL -> "Ещё для тебя"
                            RecommendationMode.GENRES -> "По твоим жанрам"
                            RecommendationMode.DISCOVER -> "Стоит попробовать"
                        },
                        color = Paper, fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-.45).sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text((suggestions.size - 1).toString() + " вариантов",
                        color = Muted, fontSize = 11.sp)
                }
                Spacer(Modifier.height(7.dp))
            }
        }
        if (suggestions.isNotEmpty()) {
            items(suggestions.drop(1), key = { it.anime.id }) { recommendation ->
                RecommendationCompactItem(
                    recommendation = recommendation,
                    favorite = library.isFavorite(recommendation.anime),
                    onOpen = { onOpen(recommendation.anime) },
                    onFavorite = { library.toggleFavorite(recommendation.anime) },
                    onHide = { hideWithUndo(recommendation.anime) }
                )
            }
            item(key = "reco-info") {
                Text(
                    "История остаётся на устройстве. Просмотренные аниме не повторяются.",
                    color = Muted, fontSize = 10.sp, lineHeight = 14.sp,
                    modifier = Modifier.padding(top = 15.dp)
                )
                if (library.hiddenRecommendations.isNotEmpty()) {
                    TextButton(
                        onClick = library::resetHiddenRecommendations,
                        contentPadding = PaddingValues(horizontal = 0.dp)
                    ) { Text("Вернуть скрытые", fontSize = 11.sp) }
                }
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
        SnackbarHost(
            hostState = snackbarHost,
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, bottom = 86.dp)
        )
    }
}
