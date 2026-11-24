package ru.lisurgut.oalab

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class WatchRequest(
    val anime: Anime,
    val episode: Int = 1,
    val preferredUrl: String? = null
)

/** Playback is resolved exclusively from AniLiberty API v1. Never switch to other services. */
@Composable
fun WatchResolverScreen(
    request: WatchRequest,
    library: LocalLibrary,
    onBack: () -> Unit,
    onNativePlay: (StreamVariant) -> Unit
) {
    var error by remember(request.anime.id, request.episode) {
        mutableStateOf<String?>(null)
    }
    var refresh by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(true) }

    LaunchedEffect(request.anime.id, request.episode, refresh) {
        error = null
        busy = true
        try {
            val streams = AniLibertyApi.resolve(request.anime)
            library.saveResolvedStreams(request.anime, streams)
            val variants = streams.filter { it.episode == request.episode }
            val preferred = variants.firstOrNull { it.url == request.preferredUrl }
            val selected = preferred ?: variants.maxByOrNull { it.popularity }
            if (selected == null) {
                error = "У AniLibria пока нет доступной серии " + request.episode +
                    " для этого аниме."
            } else {
                onNativePlay(selected)
            }
        } catch (e: Exception) {
            error = e.localizedMessage ?: "Не удалось загрузить серии AniLibria"
        } finally {
            busy = false
        }
    }

    Column(
        Modifier.fillMaxSize().background(Ink).statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, "Назад", tint = Paper)
            }
            Spacer(Modifier.width(6.dp))
            Text(
                request.anime.title, modifier = Modifier.weight(1f),
                maxLines = 1, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp
            )
            Text("Серия " + request.episode, color = Muted, fontSize = 11.sp)
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (busy) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(29.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                    Spacer(Modifier.height(13.dp))
                    Text("Получаем серию AniLibria", color = Paper, fontSize = 13.sp)
                }
            } else {
                Column(
                    Modifier.fillMaxWidth().padding(22.dp)
                        .background(Panel, RoundedCornerShape(18.dp))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Серия недоступна", color = Paper, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(9.dp))
                    Text(
                        error ?: "Нет видеопотока",
                        color = Muted, fontSize = 12.sp, lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { refresh++ }) {
                        Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Повторить", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
