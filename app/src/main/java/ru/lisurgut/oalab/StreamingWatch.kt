package ru.lisurgut.oalab

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class WatchRequest(
    val anime: Anime,
    val episode: Int = 1,
    val preferredUrl: String? = null
)

data class OfficialEpisode(val title: String, val site: String, val url: String) {
    fun episodeNumber(): Int? {
        val label = title.lowercase()
        return Regex("""(?:episode|ep\.?|серия|эпизод)\s*[:#-]?\s*(\d+)""")
            .find(label)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Regex("""^\s*(\d+)\s*[-:.]""").find(label)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    fun isVideoFile(): Boolean {
        val path = android.net.Uri.parse(url).path?.lowercase() ?: return false
        return path.endsWith(".mp4") || path.endsWith(".m3u8") || path.endsWith(".mpd") || path.endsWith(".mkv")
    }
}

object OfficialPlayback {
    /** AniList's streamingEpisodes contains only legal external streaming episodes.
     * It does NOT provide a license to extract, download or bypass the site's player.
     */
    suspend fun episodes(anime: Anime): List<OfficialEpisode> = withContext(Dispatchers.IO) {
        val cached = anime.official.filter { it.episode.isNotBlank() && it.url.startsWith("https://") }
            .map { OfficialEpisode(it.episode, it.site, it.url) }
        if (cached.isNotEmpty()) return@withContext cached

        val id = anime.anilistId.takeIf { it > 0 }
        val malId = anime.malId.takeIf { it > 0 }
        val query = """
            query Episodes(@id:Int,@mal:Int,@search:String) {
              Media(id:@id,idMal:@mal,search:@search,type:ANIME) {
                streamingEpisodes { title site url }
              }
            }
        """.trimIndent().replace('@', 36.toChar())
        val args = JSONObject().apply {
            if (id != null) put("id", id)
            else if (malId != null) put("mal", malId)
            else put("search", anime.title)
        }
        val request = JSONObject().put("query", query).put("variables", args)
        val media = AnimeApi.http("https://graphql.anilist.co", request)
            .optJSONObject("data")?.optJSONObject("Media") ?: return@withContext emptyList()
        val episodes = media.optJSONArray("streamingEpisodes") ?: return@withContext emptyList()
        (0 until episodes.length()).mapNotNull { index ->
            val episode = episodes.optJSONObject(index) ?: return@mapNotNull null
            val url = episode.optString("url")
            if (!url.startsWith("https://")) return@mapNotNull null
            OfficialEpisode(
                episode.optString("title"),
                episode.optString("site", "Официальный плеер"),
                url
            )
        }.distinctBy { it.url }
    }

    fun select(episodes: List<OfficialEpisode>, number: Int): OfficialEpisode? =
        episodes.firstOrNull { it.episodeNumber() == number }
            ?: episodes.firstOrNull { it.episodeNumber() == null && number == 1 }
            ?: episodes.firstOrNull()

    fun directStream(anime: Anime, ep: Int, source: OfficialEpisode): StreamVariant =
        StreamVariant(
            source = source.site, episode = source.episodeNumber() ?: ep,
            language = "ja", team = source.site, voices = 0, votes = 0,
            popularity = 0, timing = "Официальная серия", url = source.url, subtitle = ""
        )
}

@Composable
fun WatchResolverScreen(
    request: WatchRequest,
    library: LocalLibrary,
    onBack: () -> Unit,
    onNativePlay: (StreamVariant) -> Unit
) {
    var pageUrl by remember(request.anime.id, request.episode) { mutableStateOf<String?>(null) }
    var pageSite by remember(request.anime.id, request.episode) { mutableStateOf("") }
    var errorText by remember(request.anime.id, request.episode) { mutableStateOf<String?>(null) }
    var loading by remember(request.anime.id, request.episode) { mutableStateOf(true) }

    LaunchedEffect(request.anime.id, request.episode, request.preferredUrl) {
        loading = true
        errorText = null
        val available = library.rankVariants(
            library.variantsFor(request.anime).filter { it.episode == request.episode },
            library.voiceSort
        )
        val native = available.firstOrNull { it.url == request.preferredUrl } ?: available.firstOrNull()
        if (native != null) {
            onNativePlay(native)
            return@LaunchedEffect
        }
        try {
            val episodes = OfficialPlayback.episodes(request.anime)
            val best = OfficialPlayback.select(episodes, request.episode)
            if (best != null) {
                if (best.isVideoFile()) {
                    onNativePlay(OfficialPlayback.directStream(request.anime, request.episode, best))
                    return@LaunchedEffect
                }
                pageSite = best.site
                pageUrl = best.url
            } else {
                errorText = "Для этого тайтла пока нет доступной бесплатной серии в официальных источниках. " +
                    "Каталог аниме не означает доступность лицензированного видео."
            }
        } catch (e: Exception) {
            errorText = "Не удалось найти доступную серию: " +
                (e.localizedMessage ?: "ошибка соединения")
        } finally {
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().background(Ink).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, "Назад", tint = Paper)
            }
            Column(Modifier.weight(1f)) {
                Text(request.anime.title, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold)
                Text(
                    "Серия " + request.episode +
                        if (pageSite.isBlank()) "" else " · " + pageSite,
                    fontSize = 11.sp, color = Muted
                )
            }
        }
        when {
            pageUrl != null -> OfficialWebPlayer(pageUrl!!, Modifier.weight(1f))
            loading -> Box(
                Modifier.fillMaxSize(), contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.fillMaxWidth().padding(22.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Panel).padding(21.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Rounded.ErrorOutline, null, tint = Muted)
                    Text("Серия недоступна", fontWeight = FontWeight.SemiBold)
                    Text(errorText ?: "Нет доступного видео",
                        fontSize = 13.sp, color = Muted, lineHeight = 19.sp)
                    TextButton(onClick = onBack) { Text("К каталогу") }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun OfficialWebPlayer(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val browser = remember(url) {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.setSupportMultipleWindows(false)
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                settings.safeBrowsingEnabled = true
            }
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            loadUrl(url)
        }
    }
    DisposableEffect(browser) {
        onDispose {
            browser.stopLoading()
            browser.destroy()
        }
    }
    BackHandler(enabled = browser.canGoBack()) { browser.goBack() }
    AndroidView(factory = { browser }, modifier = modifier.fillMaxWidth())
}
