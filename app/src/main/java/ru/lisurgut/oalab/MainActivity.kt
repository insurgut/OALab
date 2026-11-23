package ru.lisurgut.oalab

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

data class PlaybackRequest(val anime: Anime, val episode: Int, val stream: StreamVariant)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(9, 9, 11)
        window.navigationBarColor = android.graphics.Color.rgb(9, 9, 11)
        window.decorView.systemUiVisibility = 0
        setContent {
            val library = remember { LocalLibrary(applicationContext) }
            OALabTheme {
                OALabApp(
                    library = library,
                    fullscreen = { enabled ->
                        requestedOrientation = if (enabled)
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                )
            }
        }
    }
}

@Composable
private fun OALabApp(library: LocalLibrary, fullscreen: (Boolean) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var category by remember { mutableStateOf("Все") }
    var sorting by remember { mutableStateOf("Популярные") }
    var fromYear by remember { mutableStateOf<Int?>(null) }
    var toYear by remember { mutableStateOf<Int?>(null) }
    var search by remember { mutableStateOf("") }
    var page by remember { mutableIntStateOf(1) }
    var catalog by remember { mutableStateOf(library.cachedCatalog()) }
    var loading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var selectedAnime by remember { mutableStateOf<Anime?>(null) }
    var playback by remember { mutableStateOf<PlaybackRequest?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(library.catalogProvider, category, sorting, search, page, fromYear, toYear, refreshKey) {
        if (search.isNotBlank()) delay(380)
        loading = true
        loadError = null
        try {
            val result = AnimeApi.load(library.catalogProvider, search, category, sorting, page, fromYear, toYear)
            catalog = if (page == 1) {
                if (search.isBlank() && category == "Все" && fromYear == null)
                    (listOf(OpenMedia.sintel) + result).distinctBy { it.id }
                else result
            } else (catalog + result).distinctBy { it.id }
            if (page == 1 && search.isBlank() && category == "Все")
                library.saveCache(result)
        } catch (error: Exception) {
            loadError = error.localizedMessage ?: "Каталог временно недоступен"
            if (catalog.isEmpty()) catalog = library.cachedCatalog()
        } finally {
            loading = false
        }
    }

    BackHandler(enabled = playback != null || selectedAnime != null) {
        if (playback != null) {
            fullscreen(false)
            playback = null
        } else {
            selectedAnime = null
        }
    }

    val openAnime: (Anime) -> Unit = { selectedAnime = it }
    val playOrOpen: (Anime) -> Unit = { anime ->
        val variants = library.rankVariants(library.variantsFor(anime), library.voiceSort)
        val lastEpisode = library.entry(anime)?.lastEpisode ?: 1
        val variant = variants.firstOrNull { it.episode == lastEpisode } ?: variants.firstOrNull()
        if (variant != null) playback = PlaybackRequest(anime, variant.episode, variant)
        else selectedAnime = anime
    }
    val showCatalogue: (String) -> Unit = {
        category = it
        search = ""
        sorting = "Популярные"
        page = 1
        tab = 1
    }

    when {
        playback != null -> {
            val active = playback!!
            PlayerScreen(
                request = active,
                library = library,
                onBack = {
                    fullscreen(false)
                    playback = null
                },
                onFullscreen = fullscreen,
                onSelect = { stream -> playback = active.copy(episode = stream.episode, stream = stream) }
            )
        }
        selectedAnime != null -> {
            val anime = selectedAnime!!
            DetailScreen(
                anime = anime,
                library = library,
                onBack = { selectedAnime = null },
                onPlay = { episode, stream ->
                    playback = PlaybackRequest(anime, episode, stream)
                }
            )
        }
        else -> {
            Scaffold(
                containerColor = Ink,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = { BottomPill(tab) { tab = it } }
            ) { innerPadding ->
                when (tab) {
                    0 -> PlumHomeScreen(
                        catalog = catalog, library = library, loading = loading, error = loadError,
                        onOpen = openAnime, onPlay = playOrOpen, onCatalogue = { tab = 1 },
                        onRetry = { refreshKey++ }
                    )
                    1 -> PlumCatalogScreen(
                        catalog = catalog, library = library, search = search,
                        onSearch = { search = it; page = 1 },
                        category = category, onCategory = { category = it; page = 1 },
                        sort = sorting, onSort = { sorting = it; page = 1 },
                        fromYear = fromYear, toYear = toYear,
                        onYears = { a, b -> fromYear = a; toYear = b; page = 1 },
                        loading = loading, error = loadError, onOpen = openAnime,
                        onPlay = playOrOpen, onMore = { if (!loading) page++ },
                        onRetry = { refreshKey++ }
                    )
                    2 -> PlumLibraryScreen(library, openAnime, playOrOpen)
                    3 -> SourcesScreen(library)
                    else -> SettingsScreen(library, onReload = { refreshKey++ })
                }
            }
        }
    }
}
