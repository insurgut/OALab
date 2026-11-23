package ru.lisurgut.oalab

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
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
    var search by remember { mutableStateOf("") }
    var page by remember { mutableIntStateOf(1) }
    var catalog by remember { mutableStateOf(library.cachedCatalog()) }
    var loading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var selectedAnime by remember { mutableStateOf<Anime?>(null) }
    var playback by remember { mutableStateOf<PlaybackRequest?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(library.catalogProvider, category, sorting, search, page, refreshKey) {
        if (search.isNotBlank()) delay(380)
        loading = true
        loadError = null
        try {
            val result = AnimeApi.load(library.catalogProvider, search, category, sorting, page)
            catalog = if (page == 1) result else (catalog + result).distinctBy { it.id }
            if (page == 1 && search.isBlank() && category == "Все")
                library.saveCache(result)
        } catch (error: Exception) {
            loadError = error.localizedMessage ?: "Каталог временно недоступен"
            if (catalog.isEmpty()) catalog = library.cachedCatalog()
        } finally {
            loading = false
        }
    }

    val openAnime: (Anime) -> Unit = { selectedAnime = it }
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
                    0 -> HomeScreen(
                        catalog = catalog,
                        library = library,
                        loading = loading,
                        error = loadError,
                        onOpen = openAnime,
                        onCategory = showCatalogue,
                        onViewAll = { showCatalogue("Все") },
                        onRetry = { refreshKey++ }
                    )
                    1 -> CatalogScreen(
                        catalog = catalog,
                        library = library,
                        search = search,
                        onSearch = { search = it; page = 1 },
                        category = category,
                        onCategory = { category = it; page = 1 },
                        sorting = sorting,
                        onSorting = { sorting = it; page = 1 },
                        loading = loading,
                        error = loadError,
                        onOpen = openAnime,
                        onMore = { if (!loading) page++ },
                        onRetry = { refreshKey++ }
                    )
                    2 -> MyLibraryScreen(library, openAnime)
                    3 -> SourcesScreen(library)
                    else -> SettingsScreen(library, onReload = { refreshKey++ })
                }
            }
        }
    }
}
