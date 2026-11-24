package ru.lisurgut.oalab

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A continuous virtual timeline, with exact boundaries and no fabricated gaps. */
@Composable
fun CombinedTimelineBar(
    merged: Boolean,
    queue: List<StreamVariant>,
    durations: List<Long>,
    activeIndex: Int,
    positionMs: Long,
    durationMs: Long,
    pendingSeek: Long?,
    onPreview: (Long) -> Unit,
    onCommit: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val total = if (merged) EpisodeTimeline.totalDuration(durations) else null
    val validMerged = total != null && total > 0L
    val starts = if (validMerged) EpisodeTimeline.chapterStarts(durations) else emptyList()
    val fullLength = total ?: durationMs
    val position = if (validMerged) {
        EpisodeTimeline.globalPosition(durations, activeIndex, positionMs)
            ?: positionMs
    } else positionMs
    val activePreviewIndex = if (validMerged && pendingSeek != null) {
        EpisodeTimeline.locate(durations, pendingSeek)?.index ?: activeIndex
    } else activeIndex
    val accent = MaterialTheme.colorScheme.primary

    Column(modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        if (validMerged) {
            val chapterState = rememberLazyListState(
                initialFirstVisibleItemIndex = activeIndex.coerceIn(0, queue.lastIndex)
            )
            LazyRow(
                state = chapterState,
                modifier = Modifier.fillMaxWidth().height(31.dp),
                contentPadding = PaddingValues(horizontal = 1.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(starts, key = { index, _ -> queue[index].episode }) { index, startMs ->
                    val highlighted = index == activePreviewIndex
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(
                                if (highlighted) accent.copy(alpha = .30f)
                                else Color.White.copy(alpha = .10f)
                            )
                            .clickable { onSeek(startMs) }
                            .padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (highlighted) {
                            Icon(
                                Icons.Rounded.PlayArrow, null,
                                tint = accent, modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(2.dp))
                        }
                        Text(
                            "С" + queue[index].episode + " · " + episodeDuration(startMs),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = if (highlighted) FontWeight.SemiBold
                            else FontWeight.Normal
                        )
                    }
                }
            }
        } else if (merged) {
            Text(
                "Время отдельных серий ещё загружается · доступна перемотка внутри серии",
                fontSize = 10.sp, color = Color.White.copy(alpha = .75f),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            Modifier.fillMaxWidth().height(29.dp),
            contentAlignment = Alignment.Center
        ) {
            Slider(
                value = if (fullLength > 0L)
                    ((pendingSeek ?: position).toFloat() / fullLength).coerceIn(0f, 1f)
                else 0f,
                onValueChange = { fraction ->
                    if (fullLength > 0L) onPreview((fraction * fullLength).toLong())
                },
                onValueChangeFinished = onCommit,
                enabled = fullLength > 0L,
                modifier = Modifier.fillMaxWidth().height(29.dp),
                colors = SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                    inactiveTrackColor = Color.White.copy(alpha = .35f)
                )
            )
            if (validMerged) {
                Canvas(Modifier.fillMaxWidth().height(10.dp)) {
                    for (boundary in EpisodeTimeline.boundaryFractions(durations)) {
                        val horizontal = size.width * boundary
                        drawLine(
                            color = Color.White.copy(alpha = .85f),
                            start = androidx.compose.ui.geometry.Offset(horizontal, 0f),
                            end = androidx.compose.ui.geometry.Offset(horizontal, size.height),
                            strokeWidth = 1.5.dp.toPx()
                        )
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                episodeDuration(pendingSeek ?: position),
                color = Color.White, fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                if (validMerged) queue.size.toString() + " серий"
                else "Серия " + queue.getOrNull(activeIndex)?.episode.orEmptyEpisode(),
                color = Color.White.copy(alpha = .65f),
                fontSize = 9.sp
            )
            Text(
                if (fullLength > 0) episodeDuration(fullLength) else "—:—",
                color = Color.White.copy(alpha = .85f),
                fontSize = 10.sp
            )
        }
    }
}

private fun Int?.orEmptyEpisode(): String = this?.toString() ?: "—"

/**
 * Preview follows the actual chapter containing the drag point. A cached real frame
 * is preferred; AniLibria's real episode preview image is the safe fallback.
 */
@Composable
fun CombinedSeekPreview(
    stream: StreamVariant,
    localTimeMs: Long,
    globalTimeMs: Long,
    chapterStartMs: Long,
    showGlobal: Boolean,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val duration = stream.details?.durationMs ?: 0L
    val sampleIndex = if (duration > 0L)
        ((localTimeMs.toDouble() / duration) * 10).toInt().coerceIn(0, 9)
    else 0
    var cached by remember(stream.url) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(stream.url, sampleIndex) {
        cached = null
        cached = EpisodeFrames.cachedFrame(context.applicationContext, stream, localTimeMs)
    }
    Row(
        modifier.clip(RoundedCornerShape(15.dp))
            .background(Color(0xF019191F))
            .padding(9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.width(123.dp).height(70.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color(0xFF32323A)),
            contentAlignment = Alignment.Center
        ) {
            if (cached != null) {
                Image(
                    bitmap = cached!!.asImageBitmap(), contentScale = ContentScale.Fit,
                    contentDescription = "Кадр серии " + stream.episode,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (!stream.details?.previewUrl.isNullOrBlank()) {
                AsyncImage(
                    model = stream.details?.previewUrl,
                    contentScale = ContentScale.Fit,
                    contentDescription = "Предпросмотр серии " + stream.episode,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text("Серия " + stream.episode,
                    color = Color.White.copy(alpha = .7f), fontSize = 11.sp)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Серия " + stream.episode,
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Text(
                stream.details?.title?.ifBlank { "Без названия" } ?: "Без названия",
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = Color.White.copy(alpha = .85f),
                fontSize = 11.sp, lineHeight = 13.sp
            )
            Text(
                if (showGlobal)
                    episodeDuration(localTimeMs) + " · всего " + episodeDuration(globalTimeMs)
                else episodeDuration(localTimeMs),
                color = Color.White.copy(alpha = .9f), fontSize = 10.sp
            )
            if (showGlobal) {
                Text(
                    "Начало: " + episodeDuration(chapterStartMs),
                    color = Color.White.copy(alpha = .66f),
                    fontSize = 10.sp
                )
            }
        }
    }
}
