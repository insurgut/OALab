package ru.lisurgut.oalab

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import kotlin.math.abs

/**
 * Ten real frames at evenly spaced time offsets if the device can decode HLS/TS.
 * Decoding/download is off-main and limited to two visible cards concurrently.
 * When a device cannot decode a segment, show AniLibria's real episode preview
 * rather than fabricating ten duplicate frames.
 */
object EpisodeFrames {
    private val workers = Semaphore(2)
    private const val MAX_DISK = 90L * 1024 * 1024
    private const val MAX_SEGMENT = 2_400_000

    private fun folder(context: Context) =
        File(context.cacheDir, "episode_frames").apply { mkdirs() }

    private fun file(context: Context, url: String, frame: Int): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(32)
        return File(folder(context), hash + "_" + frame + ".jpg")
    }

    private fun trim(context: Context) {
        var size = 0L
        folder(context).listFiles()?.filter { it.extension == "jpg" }
            ?.sortedByDescending { it.lastModified() }?.forEach {
                size += it.length()
                if (size > MAX_DISK) it.delete()
            }
    }

    private fun frame(retriever: MediaMetadataRetriever, timeMs: Long): Bitmap? {
        val result = if (Build.VERSION.SDK_INT >= 27)
            retriever.getScaledFrameAtTime(
                timeMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 320, 180
            )
        else retriever.getFrameAtTime(
            timeMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC
        )
        if (result == null) return null
        if (result.width <= 320 && result.height <= 180) return result
        val scale = minOf(320f / result.width, 180f / result.height)
        return Bitmap.createScaledBitmap(
            result, (result.width * scale).toInt().coerceAtLeast(1),
            (result.height * scale).toInt().coerceAtLeast(1), true
        ).also { if (it !== result) result.recycle() }
    }

    private fun save(path: File, image: Bitmap) {
        val temporary = File(path.parentFile, path.name + ".tmp")
        try {
            temporary.outputStream().use { image.compress(Bitmap.CompressFormat.JPEG, 70, it) }
            if (!temporary.renameTo(path)) {
                temporary.copyTo(path, overwrite = true)
                temporary.delete()
            }
        } catch (_: Exception) { temporary.delete() }
    }

    private fun text(url: String): String {
        val request = URL(url).openConnection() as HttpURLConnection
        request.connectTimeout = 5_000
        request.readTimeout = 8_000
        try {
            if (request.responseCode !in 200..299) error("HLS HTTP " + request.responseCode)
            return request.inputStream.bufferedReader().use { reader ->
                val builder = StringBuilder()
                while (builder.length < 180_000) {
                    val line = reader.readLine() ?: break
                    builder.append(line).append('\n')
                }
                builder.toString()
            }
        } finally { request.disconnect() }
    }

    private fun hlsSegments(uri: String): List<Pair<Long, String>>? {
        var current = uri
        for (i in 0..2) {
            val lines = text(current).lines().map(String::trim)
            if (!lines.firstOrNull().orEmpty().startsWith("#EXTM3U")) return null
            // Never attempt to bypass media encryption or DRM.
            if (lines.any { it.startsWith("#EXT-X-KEY") }) return null
            val alternatives = lines.indices.mapNotNull { j ->
                if (!lines[j].startsWith("#EXT-X-STREAM-INF")) return@mapNotNull null
                val next = lines.getOrNull(j + 1) ?: return@mapNotNull null
                if (next.startsWith("#")) return@mapNotNull null
                val bandwidth = Regex("""BANDWIDTH=(\d+)""")
                    .find(lines[j])?.groupValues?.get(1)?.toLongOrNull() ?: Long.MAX_VALUE
                bandwidth to URI(current).resolve(next).toString()
            }
            if (alternatives.isNotEmpty()) {
                current = alternatives.minByOrNull { it.first }!!.second
                continue
            }
            var elapsed = 0.0
            var seconds: Double? = null
            val result = mutableListOf<Pair<Long, String>>()
            for (line in lines) {
                if (line.startsWith("#EXTINF:")) {
                    seconds = line.substringAfter(":").substringBefore(",").toDoubleOrNull()
                } else if (!line.startsWith("#") && seconds != null) {
                    result.add((elapsed * 1000).toLong() to URI(current).resolve(line).toString())
                    elapsed += seconds
                    seconds = null
                }
            }
            return result.takeIf { it.isNotEmpty() }
        }
        return null
    }

    private fun segmentFrame(
        context: Context, segments: List<Pair<Long, String>>, offset: Long
    ): Bitmap? {
        val selection = segments.minByOrNull { abs(it.first - offset) } ?: return null
        if (!selection.second.startsWith("https://")) return null
        val request = URL(selection.second).openConnection() as HttpURLConnection
        request.connectTimeout = 5_000
        request.readTimeout = 8_000
        var tmp: File? = null
        try {
            if (request.responseCode !in 200..299 ||
                request.contentLengthLong > MAX_SEGMENT) return null
            tmp = File.createTempFile("episode_segment", ".ts", context.cacheDir)
            request.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(8192)
                    var count = 0
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        count += n
                        if (count > MAX_SEGMENT) return null
                        out.write(buf, 0, n)
                    }
                }
            }
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(tmp.absolutePath)
                frame(retriever, 500)
            } finally { retriever.release() }
        } finally {
            request.disconnect()
            tmp?.delete()
        }
    }

    suspend fun load(
        context: Context, stream: StreamVariant,
        onFrame: suspend (Int, Bitmap) -> Unit
    ) {
        if (!stream.url.startsWith("https://")) return
        workers.withPermit {
            withContext(Dispatchers.IO) {
                val duration = stream.details?.durationMs ?: 0L
                val missing = mutableListOf<Int>()
                for (i in 0 until 10) {
                    if (!currentCoroutineContext().isActive) return@withContext
                    val path = file(context, stream.url, i)
                    val cached = if (path.isFile) BitmapFactory.decodeFile(path.absolutePath) else null
                    if (cached != null) onFrame(i, cached) else missing.add(i)
                }
                if (missing.isEmpty() || duration <= 0L) return@withContext
                val retriever = MediaMetadataRetriever()
                val direct = try {
                    retriever.setDataSource(stream.url, mapOf("User-Agent" to "OAlab/0.5"))
                    true
                } catch (_: Exception) { false }
                val segments = if (!direct) try { hlsSegments(stream.url) }
                    catch (_: Exception) { null } else null

                for (i in missing) {
                    if (!currentCoroutineContext().isActive) break
                    val target = ((i + 0.5) * duration / 10).toLong()
                    val screenshot = try {
                        if (direct) frame(retriever, target)
                        else if (segments != null) segmentFrame(context, segments, target)
                        else null
                    } catch (_: Exception) { null }
                    if (screenshot != null) {
                        save(file(context, stream.url, i), screenshot)
                        onFrame(i, screenshot)
                    }
                }
                try { retriever.release() } catch (_: Exception) { }
                trim(context)
            }
        }
    }
}

@Composable
fun AnimatedEpisodePreview(
    stream: StreamVariant, modifier: Modifier = Modifier, active: Boolean = true
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val frames = remember(stream.url) {
        mutableStateListOf<Bitmap?>().apply { repeat(10) { add(null) } }
    }
    var selected by remember(stream.url) { mutableIntStateOf(0) }
    LaunchedEffect(stream.url, active) {
        if (!active) return@LaunchedEffect
        EpisodeFrames.load(context.applicationContext, stream) { index, bitmap ->
            withContext(Dispatchers.Main) { frames[index] = bitmap }
        }
    }
    val available = frames.indices.filter { frames[it] != null }
    LaunchedEffect(available, active) {
        while (active && available.size > 1) {
            delay(2_500)
            selected = (selected + 1) % available.size
        }
    }
    Box(
        modifier.clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        if (available.isNotEmpty()) {
            Crossfade(
                targetState = available[selected.coerceAtMost(available.lastIndex)],
                animationSpec = tween(550), label = "Episode slideshow"
            ) { index ->
                val frame = frames[index]
                if (frame != null) Image(
                    bitmap = frame.asImageBitmap(),
                    contentDescription = "Кадр серии " + stream.episode,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else if (!stream.details?.previewUrl.isNullOrBlank()) {
            AsyncImage(
                model = stream.details?.previewUrl,
                contentDescription = "Кадр серии " + stream.episode,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
