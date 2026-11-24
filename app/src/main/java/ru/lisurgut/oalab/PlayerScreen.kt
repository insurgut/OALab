package ru.lisurgut.oalab

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Canvas
import android.app.Activity
import android.app.PictureInPictureParams
import android.util.Rational
import android.os.Build
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.media3.session.MediaSession
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToLong

private fun mediaItem(variant: StreamVariant): MediaItem {
    val builder = MediaItem.Builder().setUri(variant.url)
        .setMediaId(variant.episode.toString())
    if (variant.url.contains(".m3u8", ignoreCase = true))
        builder.setMimeType(MimeTypes.APPLICATION_M3U8)
    return builder.build()
}

@OptIn(UnstableApi::class, ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(
    request: PlaybackRequest,
    library: LocalLibrary,
    onBack: () -> Unit,
    onFullscreen: (Boolean) -> Unit,
    pipMode: Boolean = false,
    onSelect: (StreamVariant) -> Unit
) {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            preloadConfiguration = ExoPlayer.PreloadConfiguration(5_000_000L)
        }
    }
    val mediaSession = remember(player) {
        MediaSession.Builder(context, player).build()
    }
    val now by rememberUpdatedState(request)
    val selectedCallback by rememberUpdatedState(onSelect)
    val streams = library.variantsFor(request.anime)
    val merged = library.mergeEpisodes
    val queue = if (merged) {
        streams.groupBy { it.episode }.toSortedMap().values.mapNotNull { choices ->
            library.selectQuality(choices)
        }.map {
            if (it.episode == request.episode) request.stream else it
        }
    } else listOf(request.stream)

    var applied by remember { mutableStateOf(emptyList<StreamVariant>()) }
    var appliedMerged by remember { mutableStateOf(false) }
    var appliedAnime by remember { mutableStateOf("") }
    var currentIndex by remember { mutableIntStateOf(0) }
    var oldMediaIndex by remember { mutableIntStateOf(0) }
    var time by remember { mutableLongStateOf(0L) }
    var length by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var controlsLocked by remember { mutableStateOf(false) }
    var pendingSeek by remember { mutableStateOf<Long?>(null) }
    var modeOptions by remember { mutableStateOf(false) }
    var qualityMenu by remember { mutableStateOf(false) }
    var lastGestureSeek by remember { mutableLongStateOf(0L) }
    var gestureHint by remember { mutableStateOf("") }
    var gestureCount by remember { mutableIntStateOf(0) }
    var showGestureHint by remember { mutableStateOf(false) }
    var speedBoost by remember { mutableStateOf(false) }
    var retryPending by remember { mutableStateOf(false) }
    var retryAttempts by remember { mutableIntStateOf(0) }
    var rebufferCount by remember { mutableIntStateOf(0) }
    var playedBefore by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(false) }
    var bufferAheadMs by remember { mutableLongStateOf(0L) }
    var controls by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var suppressedNext by remember { mutableStateOf(false) }
    val tried = remember { mutableStateListOf<String>() }

    val active = queue.getOrNull(currentIndex) ?: request.stream
    val episode = active.episode
    val episodeQualities = streams.filter { it.episode == episode && it.url.isNotBlank() }
        .distinctBy { it.popularity }.sortedByDescending { it.popularity }
    val recordedDurations = remember { mutableStateMapOf<Int, Long>() }
    val durations = queue.map { item ->
        // Media3's measured time overrides imperfect metadata after an item loads.
        recordedDurations[item.episode]?.takeIf { it > 0L }
            ?: (item.details?.durationMs ?: 0L).takeIf { it > 0L } ?: 0L
    }
    val completeDuration = EpisodeTimeline.totalDuration(durations) ?: 0L
    val episodeOffset = durations.take(currentIndex).sum()
    val totalPosition = if (merged)
        EpisodeTimeline.globalPosition(durations, currentIndex, time) ?: episodeOffset + time
    else time
    val displayDuration = if (merged && completeDuration > 0L)
        completeDuration else length
    val displayedPosition = if (merged && completeDuration > 0L)
        totalPosition else time

    val latestQueue by rememberUpdatedState(queue)

    DisposableEffect(player) {
        onFullscreen(true)
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
                if (isPlaying) playedBefore = true
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val nextIndex = player.currentMediaItemIndex.coerceAtLeast(0)
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO &&
                    nextIndex != oldMediaIndex) {
                    val completed = latestQueue.getOrNull(oldMediaIndex)
                    if (completed != null) library.markEpisodeFinished(
                        now.anime, completed.episode, completed.details?.durationMs ?: 0L
                    )
                }
                oldMediaIndex = nextIndex
                currentIndex = nextIndex
                controls = false
                suppressedNext = false
                tried.clear()
            }
            override fun onIsLoadingChanged(isLoading: Boolean) {
                buffering = isLoading && !player.isPlaying
            }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_BUFFERING && playedBefore &&
                    !library.dataSaver && library.videoQuality == 0) {
                    rebufferCount++
                    if (rebufferCount >= 3) {
                        rebufferCount = 0
                        val current = latestQueue.getOrNull(player.currentMediaItemIndex)
                            ?: now.stream
                        val lower = library.variantsFor(now.anime)
                            .filter {
                                it.episode == current.episode &&
                                    it.popularity < current.popularity
                            }.maxByOrNull { it.popularity }
                        if (lower != null) selectedCallback(lower)
                    }
                }
                if (state == Player.STATE_READY && player.isPlaying) {
                    retryAttempts = 0
                }
                if (state == Player.STATE_ENDED) {
                    val completed = latestQueue.getOrNull(
                        player.currentMediaItemIndex.coerceAtLeast(0)
                    )
                    if (completed != null) library.markEpisodeFinished(
                        now.anime, completed.episode, completed.details?.durationMs ?: 0L
                    )
                }
                if (state == Player.STATE_ENDED && !library.mergeEpisodes && library.autoplay) {
                    val next = library.variantsFor(now.anime)
                        .filter { it.episode == now.episode + 1 }
                        .maxByOrNull { it.popularity }
                    if (next != null) selectedCallback(next)
                }
            }
            override fun onPlayerError(cause: PlaybackException) {
                val networkProblem = cause.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                    cause.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                if (networkProblem && retryAttempts < 3) {
                    retryAttempts++
                    retryPending = true
                    error = "Восстанавливаем соединение…"
                    controls = true
                    return
                }
                val current = latestQueue.getOrNull(player.currentMediaItemIndex)
                    ?: now.stream
                if (current.url !in tried) tried.add(current.url)
                val backup = library.variantsFor(now.anime)
                    .filter {
                        it.source == "AniLiberty" &&
                            it.episode == current.episode &&
                            it.url !in tried
                    }.maxByOrNull { it.popularity }
                if (backup != null) {
                    error = null
                    selectedCallback(backup)
                } else {
                    error = cause.localizedMessage ?: "Не удалось воспроизвести серию"
                    controls = true
                }
            }
        }
        player.addListener(listener)
        onDispose {
            val idx = player.currentMediaItemIndex.coerceAtLeast(0)
            val stream = applied.getOrNull(idx) ?: now.stream
            val segmentPosition = player.currentPosition.coerceAtLeast(0L)
            library.recordPlayback(
                now.anime, stream.episode, segmentPosition,
                library.globalEpisodePosition(now.anime, stream.episode, segmentPosition)
            )
            player.removeListener(listener)
            mediaSession.release()
            player.release()
            onFullscreen(false)
        }
    }

    // Rebuild the queue only when its contents/mode change, preserving actual
    // per-episode playhead when the user switches merged playback on/off.
    LaunchedEffect(queue.map { it.url }, merged, request.episode, request.stream.url) {
        val oldStream = applied.getOrNull(player.currentMediaItemIndex)
        val sameAnime = appliedAnime == request.anime.id
        val continuePlayback = if (sameAnime) player.playWhenReady else true
        val toggleOnly = sameAnime && appliedMerged != merged
        val desiredEpisode = if (toggleOnly) oldStream?.episode ?: request.episode
            else request.episode
        val savedPosition = if (sameAnime && oldStream?.episode == desiredEpisode) {
            player.currentPosition.coerceAtLeast(0L)
        } else {
            library.episodePosition(request.anime, desiredEpisode)
                .takeIf { it > 0L } ?:
                if (library.entry(request.anime)?.lastEpisode == desiredEpisode)
                    library.entry(request.anime)?.lastPosition ?: 0L else 0L
        }
        val start = queue.indexOfFirst { it.episode == desiredEpisode }.coerceAtLeast(0)
        applied = queue
        appliedAnime = request.anime.id
        appliedMerged = merged
        currentIndex = start
        oldMediaIndex = start
        tried.clear()
        error = null
        retryAttempts = 0
        retryPending = false
        playedBefore = false
        rebufferCount = 0
        player.setMediaItems(queue.map(::mediaItem), start, savedPosition)
        player.prepare()
        player.playWhenReady = continuePlayback
    }

    LaunchedEffect(request.anime.id, episode) {
        suppressedNext = false
        pendingSeek = null
    }

    LaunchedEffect(player, episode) {
        var tick = 0
        while (isActive) {
            val position = player.currentPosition.coerceAtLeast(0L)
            time = position
            length = player.duration.takeIf { it > 0L } ?: 0L
            bufferAheadMs = player.totalBufferedDuration.coerceAtLeast(0L)
            if (length > 0L) recordedDurations[episode] = length

            val window = active.details
            val opening = window?.opening
            val ending = window?.ending
            val target = when {
                library.skipOpenings && opening?.contains(position) == true -> opening
                library.skipEndings && ending?.contains(position) == true -> ending
                else -> null
            }
            if (target != null && player.isPlaying) {
                if (merged && currentIndex < queue.lastIndex &&
                    target === ending && target.stopMs >= (window.durationMs - 2_000L)) {
                    player.seekToNextMediaItem()
                } else {
                    player.seekTo(target.stopMs)
                }
            }
            if (++tick % 12 == 0 && position > 0L) {
                library.recordPlayback(
                    request.anime, episode, position,
                    library.globalEpisodePosition(request.anime, episode, position)
                )
            }
            delay(500)
        }
    }

    LaunchedEffect(gestureCount) {
        if (gestureCount > 0) {
            showGestureHint = true
            delay(900)
            showGestureHint = false
        }
    }

    LaunchedEffect(retryPending, retryAttempts) {
        if (retryPending) {
            delay((retryAttempts * 1_500L).coerceAtMost(6_000L))
            retryPending = false
            error = null
            if (player.playbackState == Player.STATE_IDLE) {
                player.prepare()
                player.playWhenReady = true
            }
        }
    }

    LaunchedEffect(
        controls, request.stream.url, controlsLocked, modeOptions, qualityMenu, pendingSeek
    ) {
        if (controls && !controlsLocked && !modeOptions && !qualityMenu &&
            pendingSeek == null && error == null) {
            delay(4_000)
            controls = false
        }
    }

    val seek: (Long) -> Unit = { target ->
        if (!merged || completeDuration <= 0L) {
            player.seekTo(target.coerceIn(0L, length.coerceAtLeast(0L)))
        } else {
            val destination = EpisodeTimeline.locate(durations, target)
            if (destination != null) {
                player.seekTo(destination.index, destination.positionMs)
                currentIndex = destination.index
            }
        }
        controls = true
    }

    val previewPoint = pendingSeek?.let { target ->
        if (merged && completeDuration > 0L) EpisodeTimeline.locate(durations, target)
        else EpisodeSeek(currentIndex, target.coerceIn(0L, length.coerceAtLeast(0L)))
    }
    val previewStream = previewPoint?.let { queue.getOrNull(it.index) }
    val previewChapterStart = if (merged && completeDuration > 0L && previewPoint != null)
        EpisodeTimeline.chapterStarts(durations).getOrElse(previewPoint.index) { 0L }
    else 0L

    val nextStream = if (merged) null else streams.filter {
        it.episode > episode
    }.groupBy { it.episode }.toSortedMap().values.firstOrNull()
        ?.maxByOrNull { it.popularity }
    val remaining = if (length > 0L) length - time else Long.MAX_VALUE
    val revealNext = !merged && nextStream != null &&
        remaining in 1L..75_000L && !suppressedNext

    val activeOpening = active.details?.opening?.takeIf { it.contains(time) }
    val activeEnding = active.details?.ending?.takeIf { it.contains(time) }
    val skip = when {
        activeOpening != null && !library.skipOpenings -> activeOpening
        activeEnding != null && !library.skipEndings -> activeEnding
        else -> null
    }
    val skipText = if (skip === activeOpening) "Пропустить опенинг"
        else "Пропустить эндинг"

    val seekGesture: (Long) -> Unit = { delta ->
        seek(displayedPosition + delta)
        lastGestureSeek = delta
        gestureHint = if (delta > 0) "+" + delta / 1000L + "с"
        else delta.div(1000L).toString() + "с"
        gestureCount++
    }
    val gestureLayer = PlayerGestureLayer(
        playerKey = request.anime.id,
        seekSeconds = library.seekSeconds,
        onTap = { if (!controlsLocked) controls = !controls },
        onSeek = seekGesture,
        onHold = { held ->
            speedBoost = held
            player.setPlaybackSpeed(if (held) 2f else 1f)
        },
        enabled = !controlsLocked && !pipMode
    )

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
        if (!pipMode) Box(gestureLayer)

        if (buffering && !pipMode && error == null) {
            Row(
                Modifier.align(Alignment.TopEnd)
                    .padding(top = 39.dp, end = 18.dp)
                    .background(Color.Black.copy(alpha = .62f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp), strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(5.dp))
                Text("Буфер " + (bufferAheadMs / 1000L) + " с",
                    color = Color.White, fontSize = 10.sp)
            }
        }
        AnimatedVisibility(
            visible = showGestureHint && !pipMode,
            modifier = if (lastGestureSeek < 0)
                Modifier.align(Alignment.CenterStart).padding(start = 24.dp)
            else Modifier.align(Alignment.CenterEnd).padding(end = 24.dp),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Text(
                gestureHint,
                color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.background(
                    Color.Black.copy(alpha = .64f), RoundedCornerShape(12.dp)
                ).padding(horizontal = 17.dp, vertical = 10.dp)
            )
        }

        if (speedBoost && !pipMode && !modeOptions) {
            Text(
                "2×", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                modifier = Modifier.align(Alignment.TopCenter)
                    .padding(top = 36.dp)
                    .background(Color.Black.copy(alpha = .68f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            )
        }

        if (controls && !pipMode) {
            Box(Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = .7f),
                    .25f to Color.Transparent,
                    .68f to Color.Transparent,
                    1f to Color.Black.copy(alpha = .8f)
                )
            ))
            Row(
                Modifier.align(Alignment.TopStart).fillMaxWidth()
                    .padding(start = 9.dp, end = 18.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.ArrowBack, "Назад", tint = Color.White,
                        modifier = Modifier.size(21.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    request.anime.title, modifier = Modifier.weight(1f),
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    color = Color.White, maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Серия " + episode + if (merged) " · вместе" else "",
                    color = Color.White.copy(alpha = .8f), fontSize = 10.sp
                )
                Spacer(Modifier.width(5.dp))
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = .12f))
                        .clickable {
                            controls = true
                            modeOptions = false
                            qualityMenu = !qualityMenu
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (library.videoQuality == 0) "Авто " + active.popularity + "p"
                        else active.popularity.toString() + "p",
                        color = Color.White, fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(Icons.Rounded.KeyboardArrowDown,
                        "Выбрать качество", tint = Color.White,
                        modifier = Modifier.size(15.dp))
                }
                Box(
                    Modifier.size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .combinedClickable(
                            onClick = {
                                modeOptions = false
                                qualityMenu = false
                                library.updateMergeEpisodes(!merged)
                            },
                            onLongClick = {
                                controls = true
                                qualityMenu = false
                                modeOptions = !modeOptions
                            },
                            onLongClickLabel = "Выбрать обычный или непрерывный просмотр"
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (merged) Icons.Rounded.QueuePlayNext else Icons.Rounded.PlaylistPlay,
                        if (merged) "Непрерывный просмотр · зажми для выбора"
                        else "Обычный просмотр · зажми для выбора",
                        tint = if (merged) MaterialTheme.colorScheme.primary
                            else Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                }
                IconButton(
                    onClick = {
                        controlsLocked = !controlsLocked
                        controls = true
                    },
                    modifier = Modifier.size(31.dp)
                ) {
                    Icon(
                        if (controlsLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                        if (controlsLocked) "Разблокировать" else "Заблокировать",
                        tint = Color.White, modifier = Modifier.size(18.dp)
                    )
                }
                if (Build.VERSION.SDK_INT >= 26 && library.enablePip) {
                    IconButton(
                        onClick = {
                            val activity = context as? Activity
                            if (activity != null) {
                                try {
                                    activity.enterPictureInPictureMode(
                                        PictureInPictureParams.Builder()
                                            .setAspectRatio(Rational(16, 9)).build()
                                    )
                                } catch (_: Exception) { }
                            }
                        },
                        modifier = Modifier.size(31.dp)
                    ) {
                        Icon(Icons.Rounded.PictureInPictureAlt, "Картинка в картинке",
                            tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }

            if (!controlsLocked && pendingSeek == null &&
                !modeOptions && !qualityMenu) Row(
                Modifier.align(Alignment.Center)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, Color.White.copy(alpha = .2f), RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = .7f)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.width(64.dp).height(42.dp).clickable {
                    seek(displayedPosition - library.seekSeconds * 1_000L)
                }, contentAlignment = Alignment.Center) {
                    Text("−" + library.seekSeconds + "с", color = Color.White,
                        fontSize = 12.sp)
                }
                Box(
                    Modifier.width(154.dp).height(42.dp)
                        .background(MaterialTheme.colorScheme.primary)
                        .combinedClickable(
                            onClick = {
                                if (player.isPlaying) player.pause() else player.play()
                            },
                            onLongClick = {
                                controls = true
                                modeOptions = !modeOptions
                            },
                            onLongClickLabel = "Выбрать режим просмотра"
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            if (playing) "Пауза" else "Воспроизведение",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(21.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            if (playing) "Пауза" else "Смотреть",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.SemiBold, fontSize = 13.sp
                        )
                    }
                }
                Box(Modifier.width(64.dp).height(42.dp).clickable {
                    seek(displayedPosition + library.seekSeconds * 1_000L)
                }, contentAlignment = Alignment.Center) {
                    Text("+" + library.seekSeconds + "с", color = Color.White,
                        fontSize = 12.sp)
                }
            }
            if (!controlsLocked) {
                CombinedTimelineBar(
                    merged = merged,
                    queue = queue,
                    durations = durations,
                    activeIndex = currentIndex,
                    positionMs = time,
                    durationMs = length,
                    pendingSeek = pendingSeek,
                    onPreview = { target ->
                        pendingSeek = target
                        controls = true
                    },
                    onCommit = {
                        pendingSeek?.let { seek(it) }
                        pendingSeek = null
                    },
                    onSeek = { chapterStart ->
                        pendingSeek = null
                        seek(chapterStart)
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 6.dp)
                )
            }

        }

        AnimatedVisibility(
            visible = qualityMenu && !pipMode && !controlsLocked,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
            modifier = Modifier.align(Alignment.TopEnd)
                .padding(top = 51.dp, end = 37.dp)
        ) {
            PlayerQualityPanel(
                available = episodeQualities.map { it.popularity },
                preference = library.videoQuality,
                current = active.popularity,
                onSelect = { chosen ->
                    library.updateVideoQuality(chosen)
                    qualityMenu = false
                    val selected = library.selectQuality(
                        streams.filter { it.episode == episode }
                    )
                    if (selected != null && selected.url != active.url) {
                        selectedCallback(selected)
                    }
                },
                modifier = Modifier.width(227.dp)
            )
        }

        AnimatedVisibility(
            visible = modeOptions && !pipMode && !controlsLocked,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 49.dp)
        ) {
            PlaybackModeStrip(
                continuous = merged,
                onChoose = { selected ->
                    modeOptions = false
                    pendingSeek = null
                    library.updateMergeEpisodes(selected)
                },
                modifier = Modifier.width(314.dp),
                darkOverlay = true
            )
        }

        AnimatedVisibility(
            visible = pendingSeek != null && previewStream != null &&
                !pipMode && !controlsLocked,
            enter = fadeIn() + scaleIn(initialScale = .94f),
            exit = fadeOut() + scaleOut(targetScale = .96f),
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(bottom = if (merged && completeDuration > 0L) 100.dp else 71.dp)
        ) {
            if (previewPoint != null && previewStream != null) {
                CombinedSeekPreview(
                    stream = previewStream,
                    localTimeMs = previewPoint.positionMs,
                    globalTimeMs = pendingSeek ?: 0L,
                    chapterStartMs = previewChapterStart,
                    showGlobal = merged && completeDuration > 0L,
                    modifier = Modifier.width(335.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = skip != null && error == null && !pipMode &&
                !controlsLocked && pendingSeek == null &&
                !modeOptions && !qualityMenu,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 76.dp)
        ) {
            FilledTonalButton(
                onClick = {
                    if (skip != null) {
                        if (merged && skip === activeEnding &&
                            currentIndex < queue.lastIndex &&
                            skip.stopMs >= (active.details?.durationMs ?: Long.MAX_VALUE) - 2_000L
                        ) player.seekToNextMediaItem()
                        else player.seekTo(skip.stopMs)
                    }
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 3.dp),
                modifier = Modifier.height(35.dp),
                shape = RoundedCornerShape(11.dp)
            ) {
                Icon(Icons.Rounded.SkipNext, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(skipText, fontSize = 11.sp)
            }
        }

        AnimatedVisibility(
            visible = revealNext && error == null && !pipMode &&
                !controlsLocked && pendingSeek == null &&
                !modeOptions && !qualityMenu,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 3 }),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd)
                .padding(top = 54.dp, end = 17.dp)
        ) {
            if (nextStream != null) {
                Row(
                    Modifier.width(310.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = .95f))
                        .clickable { selectedCallback(nextStream) }
                        .padding(9.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnimatedEpisodePreview(
                        streams.filter { it.episode == nextStream.episode }
                            .minByOrNull { it.popularity } ?: nextStream,
                        Modifier.size(width = 110.dp, height = 67.dp),
                        active = !library.dataSaver
                    )
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                "Серия " + nextStream.episode,
                                modifier = Modifier.weight(1f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                Icons.Rounded.Close, "Скрыть",
                                modifier = Modifier.size(19.dp)
                                    .clickable { suppressedNext = true },
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            nextStream.details?.title ?: "Следующая серия",
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            episodeDuration(nextStream.details?.durationMs ?: 0L),
                            fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = { selectedCallback(nextStream) },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text("Следующая серия", fontSize = 11.sp)
                            Icon(Icons.Rounded.ChevronRight, null,
                                Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        if (error != null && !pipMode) {
            Column(
                Modifier.align(Alignment.Center).padding(20.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(15.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Ошибка воспроизведения", fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(error!!.take(180), maxLines = 3, fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { error = null; player.prepare(); player.play() }) {
                    Text("Повторить")
                }
            }
        }
    }
}
