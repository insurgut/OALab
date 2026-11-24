package ru.lisurgut.oalab

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToLong

/** Fullscreen landscape Media3 player. All streams are AniLiberty HLS. */
@Composable
fun PlayerScreen(
    request: PlaybackRequest,
    library: LocalLibrary,
    onBack: () -> Unit,
    onFullscreen: (Boolean) -> Unit,
    onSelect: (StreamVariant) -> Unit
) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }
    val activeRequest by rememberUpdatedState(request)
    val onSelectCurrent by rememberUpdatedState(onSelect)
    val tried = remember { mutableStateListOf<String>() }
    var time by remember { mutableLongStateOf(0L) }
    var length by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var show by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var lastEpisode by remember { mutableIntStateOf(-1) }
    var lastUrl by remember { mutableStateOf("") }

    DisposableEffect(player) {
        onFullscreen(true)
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlayerError(playbackError: PlaybackException) {
                val active = activeRequest
                if (active.stream.url !in tried) tried.add(active.stream.url)
                val replacement = library.variantsFor(active.anime)
                    .filter {
                        it.source == "AniLiberty" && it.episode == active.episode &&
                            it.url !in tried
                    }.maxByOrNull { it.popularity }
                if (replacement != null) {
                    error = null
                    onSelectCurrent(replacement)
                } else {
                    error = playbackError.localizedMessage ?: "Видеопоток недоступен"
                    show = true
                }
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED && library.autoplay) {
                    val active = activeRequest
                    val next = library.variantsFor(active.anime)
                        .filter {
                            it.source == "AniLiberty" && it.episode == active.episode + 1
                        }.maxByOrNull { it.popularity }
                    if (next != null) onSelectCurrent(next)
                }
            }
        }
        player.addListener(listener)
        onDispose {
            val active = activeRequest
            library.rememberPosition(
                active.anime, active.episode, player.currentPosition.coerceAtLeast(0L)
            )
            player.removeListener(listener)
            player.release()
            onFullscreen(false)
        }
    }

    LaunchedEffect(request.episode) { tried.clear() }

    LaunchedEffect(request.stream.url, request.episode) {
        val resume = when {
            lastUrl.isNotBlank() && lastEpisode == request.episode ->
                player.currentPosition.coerceAtLeast(0L)
            library.entry(request.anime)?.lastEpisode == request.episode ->
                library.entry(request.anime)?.lastPosition ?: 0L
            else -> 0L
        }
        lastUrl = request.stream.url
        lastEpisode = request.episode
        error = null
        val builder = MediaItem.Builder().setUri(request.stream.url)
        if (request.stream.url.contains(".m3u8", ignoreCase = true))
            builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        player.setMediaItem(builder.build())
        player.prepare()
        if (resume > 0L) player.seekTo(resume)
        player.playWhenReady = true
    }

    LaunchedEffect(player, request.episode) {
        var count = 0
        while (isActive) {
            time = player.currentPosition.coerceAtLeast(0L)
            length = player.duration.takeIf { it > 0L } ?: 0L
            if (++count % 12 == 0 && time > 0L)
                library.rememberPosition(request.anime, request.episode, time)
            delay(500)
        }
    }
    LaunchedEffect(show, request.stream.url) {
        if (show && error == null) {
            delay(4_000)
            show = false
        }
    }

    val accent = MaterialTheme.colorScheme.primary
    val accentContent = MaterialTheme.colorScheme.onPrimary
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    keepScreenOn = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    this.player = player
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize()
        )
        Box(Modifier.fillMaxSize().clickable { show = !show })
        if (show) {
            Box(Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = .65f),
                    .28f to Color.Transparent,
                    .70f to Color.Transparent,
                    1f to Color.Black.copy(alpha = .78f)
                )
            ))
            Row(
                Modifier.align(Alignment.TopStart).fillMaxWidth()
                    .padding(start = 9.dp, end = 15.dp, top = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.ArrowBack, "Назад", tint = Color.White,
                        modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    request.anime.title, modifier = Modifier.weight(1f),
                    color = Color.White, fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(10.dp))
                Text("Серия " + request.episode, color = Color.White.copy(alpha = .82f),
                    fontSize = 10.sp)
            }
            // One contiguous control with 64 / 154 / 64 dp touch surfaces.
            Row(
                Modifier.align(Alignment.Center)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, Color.White.copy(alpha = .2f), RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = .7f)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                Box(Modifier.width(64.dp).height(42.dp).clickable {
                    player.seekTo(
                        (player.currentPosition - library.seekSeconds * 1_000L)
                            .coerceAtLeast(0L)
                    )
                    show = true
                }, contentAlignment = Alignment.Center) {
                    Text("−" + library.seekSeconds + "с", color = Color.White,
                        fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                Box(
                    Modifier.width(154.dp).height(42.dp)
                        .background(accent)
                        .clickable { if (player.isPlaying) player.pause() else player.play() },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            if (playing) "Пауза" else "Воспроизвести",
                            tint = accentContent, modifier = Modifier.size(21.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(if (playing) "Пауза" else "Смотреть",
                            color = accentContent, fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp)
                    }
                }
                Box(Modifier.width(64.dp).height(42.dp).clickable {
                    val target = player.currentPosition + library.seekSeconds * 1_000L
                    player.seekTo(if (length > 0L) target.coerceAtMost(length) else target)
                    show = true
                }, contentAlignment = Alignment.Center) {
                    Text("+" + library.seekSeconds + "с", color = Color.White,
                        fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)) {
                Slider(
                    value = if (length > 0L)
                        (time.toFloat() / length).coerceIn(0f, 1f) else 0f,
                    onValueChange = { progress ->
                        if (length > 0L) {
                            val target = (progress * length).roundToLong()
                            time = target
                            player.seekTo(target)
                        }
                    },
                    enabled = length > 0L,
                    modifier = Modifier.fillMaxWidth().height(20.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = accent, activeTrackColor = accent,
                        inactiveTrackColor = Color.White.copy(alpha = .35f)
                    )
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(playerTime(time), color = Color.White.copy(alpha = .8f), fontSize = 10.sp)
                    Text(playerTime(length), color = Color.White.copy(alpha = .8f), fontSize = 10.sp)
                }
            }
        }

        if (error != null) {
            Column(
                Modifier.align(Alignment.Center).padding(20.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface).padding(15.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Ошибка воспроизведения", fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(5.dp))
                Text(error!!.take(160), maxLines = 3, fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = {
                    tried.clear()
                    error = null
                    player.prepare()
                    player.play()
                }) {
                    Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Повторить", fontSize = 12.sp)
                }
            }
        }
    }
}

private fun playerTime(millis: Long): String {
    val seconds = millis.coerceAtLeast(0L) / 1_000
    val hours = seconds / 3_600
    val minutes = (seconds % 3_600) / 60
    val remainder = seconds % 60
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, remainder)
    else "%02d:%02d".format(minutes, remainder)
}
