package ru.lisurgut.oalab

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.CancellationException
import kotlin.math.abs

private val PortraitDeckHeight = 86.dp

@Composable
internal fun HomeCharacterStrip(anime: Anime) {
    val context = LocalContext.current
    var people by remember(anime.id) { mutableStateOf(emptyList<HomeCharacter>()) }
    var loading by remember(anime.id) { mutableStateOf(true) }
    var failed by remember(anime.id) { mutableStateOf(false) }
    var selected by remember(anime.id) { mutableStateOf<HomeCharacter?>(null) }
    var retry by remember(anime.id) { mutableIntStateOf(0) }

    LaunchedEffect(anime.id, anime.malId, retry) {
        loading = true
        failed = false
        try {
            HomeCharactersData.cast(context, anime, refresh = false)?.let { people = it }
            HomeCharactersData.cast(context, anime, refresh = true)?.let { people = it }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        } finally {
            loading = false
        }
    }

    if (people.isEmpty() && !loading && !failed) return

    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Text(
            "Персонажи", Modifier.align(Alignment.CenterHorizontally).padding(bottom = 5.dp),
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
            color = Paper
        )
        when {
            people.isNotEmpty() -> key(anime.id) {
                CharacterDeck(people) { selected = it }
            }
            loading -> Box(
                Modifier.fillMaxWidth().height(PortraitDeckHeight),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
            else -> TextButton(
                onClick = { retry++ },
                modifier = Modifier.align(Alignment.CenterHorizontally).height(PortraitDeckHeight)
            ) {
                Text("Повторить загрузку")
            }
        }
    }

    selected?.let { character ->
        key(character.id) {
            CharacterSheet(character, onDismiss = { selected = null })
        }
    }
}

@Composable
private fun CharacterDeck(people: List<HomeCharacter>, onOpen: (HomeCharacter) -> Unit) {
    val pager = rememberPagerState { people.size }
    PagerScrollHaptics(pager)
    BoxWithConstraints(Modifier.fillMaxWidth().height(PortraitDeckHeight)) {
        val cardSize = minOf(78.dp, maxWidth * .22f)
        val drop = with(LocalDensity.current) { 5.dp.toPx() }
        val edge = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .60f)
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = (maxWidth - cardSize) / 2),
            pageSize = PageSize.Fixed(cardSize),
            pageSpacing = cardSize * -.43f,
            beyondViewportPageCount = 2,
            flingBehavior = PagerDefaults.flingBehavior(
                state = pager,
                snapAnimationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
            key = { people[it].id }
        ) { page ->
            val character = people[page]
            val nearCenter by remember(pager, page) {
                derivedStateOf { abs(page - pager.currentPage - pager.currentPageOffsetFraction) < 2.5f }
            }
            Box(Modifier.width(cardSize).zIndex(-abs(page - pager.currentPage).toFloat())) {
                if (nearCenter) {
                    CharacterFace(
                        character, cardSize,
                        Modifier.graphicsLayer {
                            val depth = abs(page - pager.currentPage - pager.currentPageOffsetFraction)
                            val scale = 1f - .13f * depth.coerceAtMost(2f)
                            scaleX = scale
                            scaleY = scale
                            translationY = drop * depth.coerceAtMost(2f)
                            alpha = ((2.5f - depth) / .65f).coerceIn(0f, 1f)
                        }.border(1.dp, edge, RoundedCornerShape(15.dp))
                            .clickable(role = Role.Button, onClickLabel = "Открыть карточку") { onOpen(character) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CharacterFace(character: HomeCharacter, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val image = remember(context, character.portrait) {
        ImageRequest.Builder(context)
            .data(character.portrait.takeIf { it.isNotBlank() })
            .size(340).allowHardware(false)
            .transformations(AnimeFaceCrop(context)).crossfade(true).build()
    }
    Box(
        Modifier.size(size).then(modifier).clip(RoundedCornerShape(15.dp))
            .background(scheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Rounded.Person,
            contentDescription = character.name.takeIf { character.portrait.isBlank() },
            tint = scheme.onSurfaceVariant, modifier = Modifier.size(25.dp)
        )
        if (character.portrait.isNotBlank()) {
            AsyncImage(
                model = image, contentDescription = character.name,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CharacterSheet(character: HomeCharacter, onDismiss: () -> Unit) {
    var profile by remember { mutableStateOf<CharacterProfile?>(null) }
    var loading by remember { mutableStateOf(true) }
    var retry by remember { mutableIntStateOf(0) }

    LaunchedEffect(character.id, retry) {
        loading = true
        try {
            profile = HomeCharactersData.profile(character.id)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            profile = null
        } finally {
            loading = false
        }
    }

    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * .70f
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = maxHeight).verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                CharacterFace(character, 76.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(character.name, style = MaterialTheme.typography.headlineSmall)
                    profile?.nativeName?.takeIf { it.isNotBlank() }?.let { name ->
                        Text(name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val role = when (character.role) {
                        "Main" -> "Главный персонаж"
                        "Supporting" -> "Второстепенный персонаж"
                        else -> null
                    }
                    role?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
                }
            }
            character.japaneseVoice?.let { voice ->
                Text("Японская озвучка · $voice", style = MaterialTheme.typography.bodyMedium)
            }
            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else if (profile == null) {
                TextButton(onClick = { retry++ }) { Text("Повторить загрузку") }
            } else {
                val details = profile ?: return@Column
                if (details.aliases.isNotEmpty()) {
                    Text("Другие имена · ${details.aliases.joinToString(", ")}", style = MaterialTheme.typography.bodyMedium)
                }
                if (details.about.isNotBlank()) {
                    Text(details.about, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
