package ru.lisurgut.oalab

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
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
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToLong

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
    var current by remember { mutableLongStateOf(0L) }
    var total by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var controls by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var chooseVoice by remember { mutableStateOf(false) }
    var audioMenu by remember { mutableStateOf(false) }
    var subtitleMenu by remember { mutableStateOf(false) }
    var speedMenu by remember { mutableStateOf(false) }
    var full by remember { mutableStateOf(false) }
    var trackVersion by remember { mutableIntStateOf(0) }
    var urlBefore by remember { mutableStateOf("") }
    var episodeBefore by remember { mutableIntStateOf(-1) }
    val sources = library.variantsFor(request.anime)
    val alternatives = library.rankVariants(
        sources.filter { it.episode == request.episode },
        library.voiceSort
    )

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) { trackVersion++ }
            override fun onPlayerError(playbackError: PlaybackException) {
                error = playbackError.localizedMessage ?: "Не удалось воспроизвести видеопоток"
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED && library.autoplay) {
                    val next = sources.filter { it.episode == request.episode + 1 }
                    library.rankVariants(next, library.voiceSort).firstOrNull()?.let(onSelect)
                }
            }
        }
        player.addListener(listener)
        onDispose {
            library.rememberPosition(request.anime, request.episode, player.currentPosition.coerceAtLeast(0L))
            player.removeListener(listener)
            player.release()
            onFullscreen(false)
        }
    }
    LaunchedEffect(request.stream.url, request.episode) {
        val resume = when {
            urlBefore.isNotBlank() && episodeBefore == request.episode -> player.currentPosition
            library.entry(request.anime)?.lastEpisode == request.episode ->
                library.entry(request.anime)?.lastPosition ?: 0L
            else -> 0L
        }
        urlBefore = request.stream.url
        episodeBefore = request.episode
        error = null
        val media = MediaItem.Builder()
            .setUri(request.stream.url)
            .apply {
                if (request.stream.subtitle.isNotBlank()) {
                    val mime = if (request.stream.subtitle.contains(".srt", ignoreCase = true))
                        MimeTypes.APPLICATION_SUBRIP else MimeTypes.TEXT_VTT
                    setSubtitleConfigurations(
                        listOf(
                            MediaItem.SubtitleConfiguration.Builder(Uri.parse(request.stream.subtitle))
                                .setMimeType(mime)
                                .setLanguage("ru")
                                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                                .build()
                        )
                    )
                }
            }
            .build()
        player.setMediaItem(media)
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setPreferredAudioLanguage(library.language)
            .build()
        player.prepare()
        if (resume > 0L) player.seekTo(resume)
        player.playWhenReady = true
    }
    LaunchedEffect(player, request.episode) {
        var ticks = 0
        while (isActive) {
            current = player.currentPosition.coerceAtLeast(0L)
            total = player.duration.takeIf { it > 0 } ?: 0L
            if (++ticks % 10 == 0 && current > 0L)
                library.rememberPosition(request.anime, request.episode, current)
            delay(500)
        }
    }
    val audioLanguages = remember(trackVersion) {
        player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
            .flatMap { group ->
                (0 until group.length).map { index ->
                    group.getTrackFormat(index).language ?: "und"
                }
            }.distinct()
    }
    val subtitleLanguages = remember(trackVersion) {
        player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
            .flatMap { group ->
                (0 until group.length).map { index ->
                    group.getTrackFormat(index).language ?: "und"
                }
            }.distinct()
    }

    Column(
        Modifier.fillMaxSize().background(Ink).statusBarsPadding().navigationBarsPadding()
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val landscape = maxWidth > maxHeight && maxHeight < 520.dp
            Box(
                modifier = if (landscape)
                    Modifier.fillMaxWidth().height(maxHeight - 14.dp)
                else Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            keepScreenOn = true
                            resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                            this.player = player
                        }
                    },
                    update = { it.player = player },
                    modifier = Modifier.fillMaxSize().background(Color.Black)
                )
                Box(
                    Modifier.fillMaxSize().clickable { controls = !controls }
                )
                if (controls) {
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = .74f), Color.Transparent, Color.Black.copy(alpha = .87f))
                            )
                        )
                    )
                    Row(
                        Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Rounded.ArrowBack, "Назад", tint = Paper)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                request.anime.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text("Серия " + request.episode + " · " + request.stream.team, fontSize = 10.sp, color = Muted)
                        }
                        IconButton(onClick = { full = !full; onFullscreen(full) }) {
                            Icon(
                                if (full) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                                "Полный экран", tint = Paper
                            )
                        }
                    }
                    Row(
                        Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(22.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { player.seekTo((player.currentPosition - library.seekSeconds * 1000L).coerceAtLeast(0L)) }) {
                            Icon(Icons.Rounded.Replay10, "Назад", Modifier.size(36.dp), tint = Paper)
                        }
                        IconButton(
                            onClick = { if (player.isPlaying) player.pause() else player.play() },
                            modifier = Modifier.size(55.dp).clip(CircleShape)
                                .background(Paper.copy(alpha = .92f))
                        ) {
                            Icon(
                                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                if (playing) "Пауза" else "Играть",
                                Modifier.size(32.dp), tint = Ink
                            )
                        }
                        IconButton(onClick = {
                            val target = player.currentPosition + library.seekSeconds * 1000L
                            player.seekTo(if (total > 0L) target.coerceAtMost(total) else target)
                        }) {
                            Icon(Icons.Rounded.Forward10, "Вперёд", Modifier.size(36.dp), tint = Paper)
                        }
                    }
                    Column(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .padding(start = 15.dp, end = 15.dp, bottom = 6.dp)
                    ) {
                        Slider(
                            value = if (total > 0) (current.toFloat() / total).coerceIn(0f, 1f) else 0f,
                            onValueChange = { fraction ->
                                if (total > 0L) {
                                    val position = (fraction * total).roundToLong()
                                    current = position
                                    player.seekTo(position)
                                }
                            },
                            enabled = total > 0,
                            modifier = Modifier.fillMaxWidth().height(24.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = Paper,
                                activeTrackColor = Paper,
                                inactiveTrackColor = Muted.copy(alpha = .45f)
                            )
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                formatTime(current) + " / " + formatTime(total),
                                fontSize = 10.sp, color = Paper,
                                modifier = Modifier.weight(1f)
                            )
                            PlayerMenuButton(Icons.Rounded.GraphicEq, "Озвучка") { chooseVoice = true }
                            Box {
                                PlayerMenuButton(Icons.Rounded.Language, "Аудиодорожки") { audioMenu = true }
                                DropdownMenu(
                                    expanded = audioMenu,
                                    onDismissRequest = { audioMenu = false },
                                    containerColor = PanelAlt
                                ) {
                                    audioLanguages.forEach { lang ->
                                        DropdownMenuItem(
                                            text = { Text(lang.uppercase()) },
                                            onClick = {
                                                player.trackSelectionParameters =
                                                    player.trackSelectionParameters.buildUpon()
                                                        .setPreferredAudioLanguage(lang).build()
                                                audioMenu = false
                                            }
                                        )
                                    }
                                    if (audioLanguages.isEmpty())
                                        DropdownMenuItem(text = { Text("Нет других дорожек") }, onClick = { audioMenu = false })
                                }
                            }
                            Box {
                                PlayerMenuButton(Icons.Rounded.Subtitles, "Субтитры") { subtitleMenu = true }
                                DropdownMenu(
                                    expanded = subtitleMenu,
                                    onDismissRequest = { subtitleMenu = false },
                                    containerColor = PanelAlt
                                ) {
                                    DropdownMenuItem(text = { Text("Выкл") }, onClick = {
                                        player.trackSelectionParameters = player.trackSelectionParameters
                                            .buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                                        subtitleMenu = false
                                    })
                                    subtitleLanguages.forEach { lang ->
                                        DropdownMenuItem(text = { Text(lang.uppercase()) }, onClick = {
                                            player.trackSelectionParameters = player.trackSelectionParameters
                                                .buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                                .setPreferredTextLanguage(lang).build()
                                            subtitleMenu = false
                                        })
                                    }
                                }
                            }
                            Box {
                                PlayerMenuButton(Icons.Rounded.Speed, "Скорость") { speedMenu = true }
                                DropdownMenu(
                                    expanded = speedMenu,
                                    onDismissRequest = { speedMenu = false },
                                    containerColor = PanelAlt
                                ) {
                                    listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                                        DropdownMenuItem(text = { Text(speed.toString() + "×") }, onClick = {
                                            player.setPlaybackSpeed(speed)
                                            speedMenu = false
                                        })
                                    }
                                }
                            }
                        }
                    }
                }
                if (error != null) {
                    Column(
                        Modifier.align(Alignment.Center).padding(22.dp)
                            .clip(RoundedCornerShape(20.dp)).background(PanelAlt.copy(alpha = .97f))
                            .padding(15.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Rounded.ErrorOutline, null, tint = Paper)
                        Spacer(Modifier.height(8.dp))
                        Text("Ошибка воспроизведения", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(Modifier.height(5.dp))
                        Text(error!!.take(220), fontSize = 11.sp, color = Muted)
                        Spacer(Modifier.height(10.dp))
                        TextButton(onClick = { error = null; player.prepare(); player.play() }) {
                            Text("Повторить")
                        }
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 19.dp)) {
            Eyebrow("Сейчас играет")
            Spacer(Modifier.height(8.dp))
            Text(request.anime.title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(9.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MetricChip("Серия " + request.episode, Icons.Rounded.Movie)
                Spacer(Modifier.width(8.dp))
                MetricChip(request.stream.language.uppercase(), Icons.Rounded.Language)
            }
            Spacer(Modifier.height(17.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel)
                    .clickable { chooseVoice = true }.padding(17.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.GraphicEq, null, tint = Paper)
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(request.stream.team, fontWeight = FontWeight.SemiBold)
                    Text(
                        request.stream.source + " · " + request.stream.voices + " голоса",
                        color = Muted, fontSize = 12.sp
                    )
                }
                Icon(Icons.Rounded.UnfoldMore, "Изменить")
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Прогресс сохраняется локально. Переключай озвучку, субтитры и дорожки прямо во время просмотра.",
                color = Muted, fontSize = 12.sp, lineHeight = 18.sp
            )
        }
    }
    if (chooseVoice) {
        VoiceSelectorSheet(
            variants = alternatives,
            selected = request.stream,
            library = library,
            onDismiss = { chooseVoice = false },
            onSelect = { stream ->
                chooseVoice = false
                onSelect(stream)
            }
        )
    }
}

@Composable
private fun PlayerMenuButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(icon, contentDescription = description, tint = Paper, modifier = Modifier.size(19.dp))
    }
}

private fun formatTime(millis: Long): String {
    val seconds = millis.coerceAtLeast(0L) / 1000
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val sec = seconds % 60
    return if (hours > 0L)
        "%d:%02d:%02d".format(hours, minutes, sec)
    else "%02d:%02d".format(minutes, sec)
}
