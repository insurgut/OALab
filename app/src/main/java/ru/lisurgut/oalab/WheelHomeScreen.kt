package ru.lisurgut.oalab

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
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

    BoxWithConstraints(modifier.fillMaxSize().background(Ink).statusBarsPadding()) {
        // Schedule starts after the first screen. The wheel stays above the dock.
        val fontScale = LocalDensity.current.fontScale
        val stageHeight = maxHeight.coerceAtLeast(510.dp + 160.dp * (fontScale - 1f).coerceAtLeast(0f))
        val wheelHeight = (stageHeight * .30f).coerceIn(164.dp, 228.dp)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(stageHeight)) {
                HomeGlow(
                    modifier = Modifier.fillMaxWidth().height(stageHeight * .62f),
                    hue = MaterialTheme.colorScheme.primary
                )
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
                val last = library.lastInProgress()
                ContinueWatchingCard(
                    entry = last,
                    positionMs = last?.let {
                        library.episodePosition(it.anime, it.lastEpisode)
                            .takeIf { position -> position > 0L } ?: it.lastPosition
                    } ?: 0L,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)
                        .offset(y = stageHeight * .30f),
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
                                RecommendationWheel(titles, day, onOpen)
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
private fun HomeGlow(modifier: Modifier, hue: Color) {
    val scheme = MaterialTheme.colorScheme
    val light = scheme.background.luminance() > .5f
    Canvas(modifier) {
        drawRect(
            Brush.verticalGradient(
                0f to hue.copy(alpha = if (light) .16f else .24f),
                .42f to hue.copy(alpha = .07f),
                1f to Color.Transparent
            )
        )
        val center = Offset(size.width * .54f, size.height * .49f)
        val radius = maxOf(size.width, size.height) * .65f
        drawRect(
            Brush.radialGradient(
                listOf(hue.copy(alpha = if (light) .24f else .35f),
                    hue.copy(alpha = .11f), Color.Transparent),
                center = center, radius = radius
            )
        )
        drawRect(
            Brush.radialGradient(
                listOf(scheme.tertiary.copy(alpha = .17f), Color.Transparent),
                center = Offset(size.width * .18f, size.height * .44f), radius = radius * .77f
            )
        )
    }
}

@Composable
private fun RecommendationWheel(titles: List<Anime>, day: Long, onOpen: (Anime) -> Unit) {
    val repeats = if (titles.size >= 3) 1_000 else 1
    val pager = rememberPagerState(initialPage = titles.size * (repeats / 2)) {
        titles.size * repeats
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pageWidth = (maxWidth * .39f).coerceIn(112.dp, 180.dp)
        val sidePadding = (maxWidth - pageWidth) / 2
        val drop = with(LocalDensity.current) { (maxHeight * .19f).toPx() }
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            pageSize = PageSize.Fixed(pageWidth),
            contentPadding = PaddingValues(horizontal = sidePadding),
            pageSpacing = 4.dp,
            key = { "${it / titles.size}:${titles[it % titles.size].id}" }
        ) { index ->
            val anime = titles[index % titles.size]
            val seed = day xor anime.id.hashCode().toLong()
            val style = remember(seed) {
                val random = Random(seed)
                random.nextInt(6) to random.nextInt(8)
            }
            val scheme = MaterialTheme.colorScheme
            val palette = listOf(
                Color(0xFF9C99FF), Color(0xFFF5A1CE), Color(0xFF75DBD2), Color(0xFFFFBF7E),
                Color(0xFFACD389), scheme.primary, scheme.secondary, scheme.tertiary
            )
            WheelTile(
                anime = anime, hue = palette[style.second], shape = style.first,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(.88f)
                    .graphicsLayer {
                        val distance = (index - pager.currentPage - pager.currentPageOffsetFraction)
                            .coerceIn(-2f, 2f)
                        val edge = abs(distance).coerceAtMost(1.4f)
                        translationY = drop * edge
                        rotationZ = distance * 8f
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
    shape: Int,
    modifier: Modifier,
    onOpen: () -> Unit
) {
    Box(
        modifier.clip(RoundedCornerShape(22.dp)).clickable(role = Role.Button, onClick = onOpen)
    ) {
        val backdrop = Ink
        val deep = lerp(hue, Color(0xFF24223E), .68f)
        val (lobes, depth) = remember(shape) {
            listOf(5 to .17f, 4 to .21f, 7 to .10f, 3 to .21f, 8 to .14f, 2 to .22f)[shape]
        }
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width * .41f, size.height * .37f)
            val radius = minOf(size.width * .38f, size.height * .33f)
            val form = Path().apply {
                for (step in 0 until 144) {
                    val angle = (step * PI / 72 - PI / 2).toFloat()
                    val r = radius * (1f + depth * cos(angle * lobes + PI.toFloat() / 2))
                    val x = center.x + cos(angle) * r
                    val y = center.y + sin(angle) * r
                    if (step == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            val glow = Offset(size.width * .46f, size.height * .53f)
            drawRect(
                Brush.radialGradient(
                    listOf(hue.copy(alpha = .42f), hue.copy(alpha = .13f), Color.Transparent),
                    center = glow, radius = size.width * .62f
                )
            )
            val ovalTop = size.height * .31f
            val ovalOrigin = Offset(size.width * .09f, ovalTop)
            val ovalSize = Size(size.width * .82f, size.height * .68f)
            drawOval(
                brush = Brush.verticalGradient(
                    0f to hue.copy(alpha = .55f), .55f to deep.copy(alpha = .38f),
                    1f to Color.Transparent, startY = ovalTop, endY = size.height
                ),
                topLeft = ovalOrigin, size = ovalSize
            )
            drawOval(
                brush = Brush.verticalGradient(
                    0f to hue.copy(alpha = .70f), .66f to Color.Transparent,
                    endY = size.height
                ),
                topLeft = ovalOrigin, size = ovalSize, style = Stroke(width = 1.dp.toPx())
            )
            drawPath(
                form, Brush.linearGradient(
                    listOf(lerp(hue, Color.White, .30f), hue, deep),
                    start = Offset.Zero, end = Offset(size.width * .84f, size.height * .76f)
                )
            )
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val posterHeight = maxHeight * .51f
            Cover(
                anime.cover,
                Modifier.align(Alignment.TopEnd)
                    .padding(top = maxHeight * .21f, end = maxWidth * .10f)
                    .height(posterHeight).width(posterHeight * .72f)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, hue.copy(alpha = .62f), RoundedCornerShape(10.dp))
            )
        }
        Text(
            anime.title, color = Paper, fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomEnd).fillMaxWidth(.82f)
                .padding(end = 5.dp)
                .background(backdrop.copy(alpha = .92f), RoundedCornerShape(9.dp))
                .padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}
