package ru.lisurgut.oalab

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material3.MaterialTheme
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
import kotlinx.coroutines.isActive
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

data class PlaybackRequest(val anime: Anime, val episode: Int, val stream: StreamVariant)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val library = remember { LocalLibrary(applicationContext) }
            var immersivePlayer by remember { mutableStateOf(false) }
            OALabTheme {
                val background = MaterialTheme.colorScheme.background
                val dark = isSystemInDarkTheme()
                SideEffect {
                    window.statusBarColor = background.toArgb()
                    window.navigationBarColor = background.toArgb()
                    window.decorView.systemUiVisibility = if (immersivePlayer) {
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                            View.SYSTEM_UI_FLAG_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    } else if (dark) 0
                    else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                        View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                }
                OALabApp(
                    library = library,
                    fullscreen = { enabled ->
                        immersivePlayer = enabled
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
    var watch by remember { mutableStateOf<WatchRequest?>(null) }
    var updatesOpen by remember { mutableStateOf(false) }
    var updateScan by remember { mutableStateOf(UpdateScan()) }
    var updateRefresh by remember { mutableIntStateOf(0) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // The UI polls every 5 seconds while in the foreground.
    // Network requests are cached/backed off to respect GitHub API limits.
    LaunchedEffect(updateRefresh, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var first = true
            while (isActive) {
                updateScan = OAlabUpdates.check(context, force = first && updateRefresh > 0)
                first = false
                delay(5_000)
            }
        }
    }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(library.catalogProvider, category, sorting, search, page, fromYear, toYear, refreshKey) {
        loading = true
        delay(1_000) // Debounce searches and all filters before network request.
        loadError = null
        try {
            val result = AnimeApi.load(library.catalogProvider, search, category, sorting, page, fromYear, toYear)
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

    BackHandler(enabled = updatesOpen || playback != null || watch != null || selectedAnime != null) {
        if (updatesOpen) {
            updatesOpen = false
        } else if (playback != null) {
            fullscreen(false)
            playback = null
        } else if (watch != null) {
            watch = null
        } else {
            selectedAnime = null
        }
    }

    val openAnime: (Anime) -> Unit = { selectedAnime = it }
    val playOrOpen: (Anime) -> Unit = { anime ->
        watch = WatchRequest(anime, library.entry(anime)?.lastEpisode ?: 1)
    }
    val showCatalogue: (String) -> Unit = {
        category = it
        search = ""
        sorting = "Популярные"
        page = 1
        tab = 1
    }

    when {
        updatesOpen -> {
            UpdatesScreen(scan = updateScan,
                onBack = { updatesOpen = false },
                onRescan = { updateRefresh++ })
        }
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
        watch != null -> {
            val pending = watch!!
            WatchResolverScreen(
                request = pending,
                library = library,
                onBack = { watch = null },
                onNativePlay = { stream ->
                    watch = null
                    playback = PlaybackRequest(pending.anime, stream.episode, stream)
                }
            )
        }
        selectedAnime != null -> {
            val anime = selectedAnime!!
            DetailScreen(
                anime = anime,
                library = library,
                onBack = { selectedAnime = null },
                onWatch = { episode, preferredUrl ->
                    watch = WatchRequest(anime, episode, preferredUrl)
                }
            )
        }
        else -> {
            Scaffold(
                containerColor = Ink,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    BottomPill(
                        selected = tab, onSelect = { tab = it },
                        updateAvailable = updateScan.update != null,
                        onOpenUpdate = { updatesOpen = true }
                    )
                }
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
                    else -> SettingsScreen(
                        library = library, onReload = { refreshKey++ },
                        onUpdates = { updatesOpen = true }
                    )
                }
            }
        }
    }
}
