package ru.lisurgut.oalab

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.io.File
import java.time.LocalDate
import kotlin.math.abs
import kotlin.random.Random

private const val RecommendationsPerRound = 6

@Composable
fun WheelHomeScreen(
    modifier: Modifier,
    catalog: List<Anime>,
    library: LocalLibrary,
    onOpen: (Anime) -> Unit,
    onPlay: (Anime) -> Unit,
    onCatalogue: () -> Unit
) {
    val today by produceState(LocalDate.now()) {
        while (true) {
            value = LocalDate.now()
            delay(30_000)
        }
    }
    val day = today.toEpochDay()
    var shuffleDay by rememberSaveable { mutableLongStateOf(day) }
    var savedRound by rememberSaveable { mutableIntStateOf(0) }
    val round = if (shuffleDay == day) savedRound else 0
    var retry by remember { mutableIntStateOf(0) }
    var candidates by remember(library) {
        mutableStateOf(library.recommendationCandidates())
    }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val history = library.entries.toList()
    val known = remember(history, library.hiddenRecommendations) {
        history.map { it.anime.id }.toSet() + library.hiddenRecommendations
    }
    val available = remember(catalog, candidates, known) {
        (catalog + candidates).distinctBy { it.id }.count {
            it.id.startsWith("aniliberty:") && it.id !in known
        }
    }
    LaunchedEffect(available < RecommendationsPerRound, retry) {
        if (available >= RecommendationsPerRound && retry == 0) return@LaunchedEffect
        loading = true
        failed = false
        try {
            val page = if (available == 0) 1 else 2
            val fetched = AnimeApi.load(library.catalogProvider, page = page)
            if (fetched.isNotEmpty()) {
                candidates = (candidates + fetched).distinctBy { it.id }
            } else {
                failed = true
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        } finally {
            loading = false
        }
    }

    val genres = library.recommendationGenres
    val hidden = library.hiddenRecommendations
    val mode = library.recommendationMode
    val ranked = remember(catalog, candidates, history, genres, hidden, mode, today) {
        RecommendationEngine.rank(
            candidates = (catalog + candidates).distinctBy { it.id },
            history = history, explicitGenres = genres, hidden = hidden, mode = mode
        ).map { it.anime }.shuffled(Random(today.toEpochDay()))
    }
    val picks = remember(ranked, round) {
        if (ranked.size <= RecommendationsPerRound) {
            val shift = round % ranked.size.coerceAtLeast(1)
            ranked.drop(shift) + ranked.take(shift)
        } else {
            val groupCount = (ranked.size + RecommendationsPerRound - 1) / RecommendationsPerRound
            val group = round % groupCount
            ranked.filterIndexed { index, _ -> index % groupCount == group }
        }
    }
    val shuffle = {
        shuffleDay = day
        savedRound = if (round == Int.MAX_VALUE) 0 else round + 1
    }

    val last = library.lastInProgress()
    val anime = last?.anime
    val context = LocalContext.current
    var artwork by remember(anime?.id) { mutableStateOf<File?>(null) }
    LaunchedEffect(anime) {
        if (anime != null) artwork = HomeCharacterArtwork.load(context, anime)
    }

    val positionMs = last?.let {
        library.episodePosition(it.anime, it.lastEpisode)
            .takeIf { position -> position > 0L } ?: it.lastPosition
    } ?: 0L

    BoxWithConstraints(modifier.fillMaxSize().background(Ink).statusBarsPadding()) {
        // Schedule starts after the first screen. The wheel stays above the dock.
        val fontScale = LocalDensity.current.fontScale
        val stageHeight = maxHeight.coerceAtLeast(560.dp + 160.dp * (fontScale - 1f).coerceAtLeast(0f))
        val wheelHeight = (stageHeight * .30f).coerceIn(164.dp, 228.dp)
        val cardTop = stageHeight * if (last == null) .30f else .35f
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(stageHeight)) {
                HomeGlow(Modifier.matchParentSize())
                Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)) {
                    Text(
                        "OA Lab", Modifier.align(Alignment.Center), color = Paper,
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        letterSpacing = (-.6).sp
                    )
                    IconButton(onClick = onCatalogue, modifier = Modifier.align(Alignment.CenterEnd)) {
                        Icon(Icons.Rounded.Search, "Поиск аниме", tint = Paper)
                    }
                }
                if (artwork != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(artwork).crossfade(true).build(),
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.TopCenter).offset(y = 54.dp)
                            .widthIn(max = 340.dp).fillMaxWidth(.84f).height(cardTop - 56.dp),
                        alignment = Alignment.BottomCenter,
                        contentScale = ContentScale.Fit
                    )
                }
                ContinueWatchingCard(
                    entry = last,
                    positionMs = positionMs,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)
                        .offset(y = cardTop),
                    onPlay = onPlay,
                    onCatalogue = onCatalogue
                )
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 18.dp, end = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Рекомендации на сегодня", Modifier.weight(1f), color = Paper,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        FilledTonalIconButton(onClick = shuffle, enabled = ranked.size > 1) {
                            Icon(Icons.Rounded.Shuffle, "Встряхнуть рекомендации")
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(wheelHeight), contentAlignment = Alignment.Center) {
                        if (picks.isEmpty()) {
                            when {
                                loading -> CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp)
                                failed -> TextButton(onClick = { retry++ }) {
                                    Text("Не удалось загрузить · Повторить")
                                }
                                else -> TextButton(onClick = onCatalogue) {
                                    Text("Открыть каталог")
                                }
                            }
                        } else {
                            Crossfade(
                                targetState = picks, animationSpec = tween(260),
                                label = "Рекомендации на сегодня"
                            ) { titles ->
                                RecommendationWheel(titles, day, round, onOpen)
                            }
                        }
                    }
                }
            }

            Box(Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 20.dp)) {
                UpcomingScheduleSection(library = library, onOpen = onOpen)
            }
        }
    }
}

@Composable
private fun ContinueWatchingCard(
    entry: LibraryEntry?,
    positionMs: Long,
    modifier: Modifier,
    onPlay: (Anime) -> Unit,
    onCatalogue: () -> Unit
) {
    val shape = RoundedCornerShape(28.dp)
    val scheme = MaterialTheme.colorScheme
    val image = entry?.anime?.let { it.banner.ifBlank { it.cover } }.orEmpty()
    val hasArtwork = image.startsWith("http")
    val foreground = if (hasArtwork) Color.White else scheme.onPrimaryContainer
    val accent = if (hasArtwork) Color.White else scheme.primary
    val shade = Color(0xFF0D1020)
    val action = { if (entry == null) onCatalogue() else onPlay(entry.anime) }
    Box(
        modifier.clip(shape)
            .background(scheme.primaryContainer)
            .border(1.dp, if (hasArtwork) Color.White.copy(alpha = .16f) else scheme.outlineVariant, shape)
            .clickable(role = Role.Button, onClick = action)
    ) {
        if (hasArtwork) {
            Cover(image, Modifier.matchParentSize())
            Box(
                Modifier.matchParentSize().background(
                    Brush.horizontalGradient(
                        0f to shade.copy(alpha = .96f),
                        .56f to shade.copy(alpha = .88f),
                        1f to shade.copy(alpha = .58f)
                    )
                )
            )
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 142.dp)
                .padding(start = 19.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    "Продолжить просмотр", color = accent,
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    entry?.anime?.title ?: "Выбрать аниме",
                    color = foreground, style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold, maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry != null) {
                    val seconds = positionMs.coerceAtLeast(0L) / 1_000
                    val time = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
                    Text(
                        "Серия ${entry.lastEpisode} · $time",
                        color = foreground.copy(alpha = .86f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Box(
                Modifier.size(46.dp).background(
                    if (hasArtwork) Color.White.copy(alpha = .94f) else scheme.primary, CircleShape
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (entry == null) Icons.Rounded.ArrowForward else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = if (hasArtwork) shade else scheme.onPrimary
                )
            }
        }
    }
}

@Composable
private fun HomeGlow(modifier: Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val pale = Ink.luminance() > .5f
    Box(
        modifier.background(
            Brush.verticalGradient(
                0f to color.copy(alpha = if (pale) .12f else .22f),
                .22f to color.copy(alpha = if (pale) .16f else .28f),
                .48f to color.copy(alpha = if (pale) .09f else .14f),
                .65f to color.copy(alpha = if (pale) .025f else .04f),
                .80f to Color.Transparent,
                1f to Color.Transparent
            )
        )
    )
}

@Composable
private fun RecommendationWheel(titles: List<Anime>, day: Long, round: Int, onOpen: (Anime) -> Unit) {
    val silhouettes = remember(day) { recommendationSilhouettes.shuffled(Random(day)) }
    val shapeCount = silhouettes.size
    val repeats = if (titles.size >= 3) 1_000 else 1
    val pager = rememberPagerState(initialPage = titles.size * (repeats / 2)) {
        titles.size * repeats
    }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val wheelWidth = maxWidth.coerceAtMost(552.dp)
        val gap = 4.dp
        val pageWidth = (wheelWidth - gap * 2) / 3
        val sidePadding = (wheelWidth - pageWidth) / 2
        val drop = with(LocalDensity.current) { (maxHeight * .09f).toPx() }
        HorizontalPager(
            state = pager,
            modifier = Modifier.width(wheelWidth).fillMaxHeight(),
            pageSize = PageSize.Fixed(pageWidth),
            contentPadding = PaddingValues(horizontal = sidePadding),
            pageSpacing = gap,
            key = { "${it / titles.size}:${titles[it % titles.size].id}" }
        ) { index ->
            val anime = titles[index % titles.size]
            val seed = day xor anime.id.hashCode().toLong()
            val color = remember(seed) { Random(seed).nextInt(8) }
            val shapeIndex = (index % titles.size + round % shapeCount) % shapeCount
            val startShape = silhouettes[shapeIndex]
            val endShape = silhouettes[(shapeIndex + 3) % shapeCount]
            val morph = remember(startShape, endShape) { Morph(startShape, endShape) }
            val scheme = MaterialTheme.colorScheme
            val palette = listOf(
                Color(0xFF9C99FF), Color(0xFFF5A1CE), Color(0xFF75DBD2), Color(0xFFFFBF7E),
                Color(0xFFACD389), scheme.primary, scheme.secondary, scheme.tertiary
            )
            WheelTile(
                anime = anime, hue = palette[color], morph = morph,
                morphProgress = { (2f * abs(pager.currentPageOffsetFraction)).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().fillMaxHeight(.88f)
                    .graphicsLayer {
                        val distance = (index - pager.currentPage - pager.currentPageOffsetFraction)
                            .coerceIn(-2f, 2f)
                        val edge = abs(distance).coerceAtMost(1.4f)
                        translationY = drop * edge
                        scaleX = 1f - edge * .12f
                        scaleY = scaleX
                    },
                onOpen = { onOpen(anime) }
            )
        }
    }
}

@Composable
private fun WheelTile(
    anime: Anime,
    hue: Color,
    morph: Morph,
    morphProgress: () -> Float,
    modifier: Modifier,
    onOpen: () -> Unit
) {
    BoxWithConstraints(
        modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null, role = Role.Button, onClick = onOpen
        ),
        contentAlignment = Alignment.TopCenter
    ) {
        val figureSize = minOf(maxWidth * .82f, maxHeight * .61f)
        val posterHeight = minOf(maxHeight * .51f, maxWidth * .87f)
        val outline = remember { Path() }
        Canvas(
            Modifier.align(Alignment.TopStart).offset(x = maxWidth * .04f, y = 8.dp).size(figureSize)
        ) {
            outline.reset()
            var start = true
            morph.forEachCubic(morphProgress()) { cubic ->
                if (start) {
                    outline.moveTo(cubic.anchor0X * size.width, cubic.anchor0Y * size.height)
                    start = false
                }
                outline.cubicTo(
                    cubic.control0X * size.width, cubic.control0Y * size.height,
                    cubic.control1X * size.width, cubic.control1Y * size.height,
                    cubic.anchor1X * size.width, cubic.anchor1Y * size.height
                )
            }
            outline.close()
            drawPath(outline, color = hue)
        }
        Cover(
            anime.cover,
            Modifier.align(Alignment.TopEnd)
                .padding(top = maxHeight * .22f, end = maxWidth * .05f)
                .height(posterHeight).width(posterHeight * .72f)
                .clip(RoundedCornerShape(10.dp))
        )
        Text(
            anime.title, color = Paper, fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp, lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
