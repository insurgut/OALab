package ru.lisurgut.oalab

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.net.URLEncoder
import java.nio.FloatBuffer
import java.security.DigestInputStream
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection
import kotlin.math.roundToInt

internal object HomeCharacterArtwork {
    private const val Catalog = "https://api.tenrai.org/v1"
    // BiRefNext AniSeg by nkta, distributed by its author under the DINOv3 license.
    private const val ModelUrl =
        "https://huggingface.co/nkta/birefnext-aniseg-ONNX/resolve/main/birefnext-aniseg-int8-v0.1.onnx"
    private const val ModelBytes = 117_239_813L
    private const val ModelSha256 = "75642769ce491b9d76e2cd2506c9b4e26852f7e0c0a09f438c965a233de094d5"
    private const val ModelMaxEdge = 512
    private const val InferenceMemoryBytes = 1_000_000_000L
    private const val PortraitCdn = "https://cdn.myanimelist.net/images/characters/"
    private val officialArtwork = mapOf(
        60732 to "https://mizuzokusei-anime.com/assets/images/character/character-ryo-v2.png"
    )
    private const val RetryAfterMs = 10 * 60 * 1000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val worker = Mutex()
    private val guard = Any()
    private val pending = mutableMapOf<String, Deferred<File?>>()
    private val failedAt = mutableMapOf<String, Long>()

    suspend fun load(context: Context, anime: Anime): File? {
        val release = anime.id.removePrefix("aniliberty:")
        if (release.toIntOrNull() == null) return null
        val app = context.applicationContext
        val directory = File(app.cacheDir, "home-characters")
        val output = File(directory, "silhouette-v2-$release.png")
        if (output.isFile) return output
        val task = synchronized(guard) {
            val now = SystemClock.elapsedRealtime()
            failedAt.entries.removeAll { now - it.value >= RetryAfterMs }
            if (release in failedAt) return null
            pending[release] ?: scope.async(start = CoroutineStart.LAZY) {
                worker.withLock {
                    try {
                        if (output.isFile) output else render(app, anime, output)
                    } catch (error: Exception) {
                        Log.w("HomeCharacterArtwork", "Could not prepare a character cutout (${error.javaClass.simpleName})")
                        null
                    } catch (_: LinkageError) {
                        null
                    }
                }.also {
                    if (it == null) synchronized(guard) {
                        failedAt[release] = SystemClock.elapsedRealtime()
                    }
                }
            }.also { job ->
                pending[release] = job
                job.invokeOnCompletion { synchronized(guard) { pending.remove(release) } }
            }
        }
        return task.await()
    }

    private fun render(context: Context, anime: Anime, output: File): File? {
        val titleId = anime.malId.takeIf { it > 0 } ?: matchTitle(anime) ?: return null
        officialArtwork[titleId]?.let { url ->
            readPortrait(url)?.let { portrait ->
                try {
                    if (portrait.hasAlpha()) return save(output, portrait)
                } finally {
                    portrait.recycle()
                }
            }
        }

        val activityManager = context.getSystemService(ActivityManager::class.java) ?: return null
        val memory = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        if (Build.SUPPORTED_64_BIT_ABIS.isEmpty() || activityManager.isLowRamDevice ||
            memory.availMem < InferenceMemoryBytes
        ) return null

        val candidates = portraitCandidates(titleId)
        if (candidates.isEmpty()) return null
        val env = OrtEnvironment.getEnvironment()
        val options = OrtSession.SessionOptions()
        options.use {
            it.setIntraOpNumThreads(2)
            it.setMemoryPatternOptimization(false)
            it.setCPUArenaAllocator(false)
            env.createSession(modelFile(context).absolutePath, it).use { session ->
                for (url in candidates) {
                    val portrait = readPortrait(url) ?: continue
                    val cutout = try {
                        segment(env, session, portrait)
                    } finally {
                        portrait.recycle()
                    } ?: continue
                    try {
                        return save(output, cutout)
                    } finally {
                        cutout.recycle()
                    }
                }
            }
        }
        return null
    }

    private fun portraitCandidates(titleId: Int): List<String> =
        get("$Catalog/anime/$titleId/characters")
            .objects()
            .sortedWith(
                compareByDescending<JSONObject> { it.optString("role") == "Main" }
                    .thenByDescending { it.optInt("favorites") }
            )
            .mapNotNull { it.optJSONObject("character") }
            .take(2)
            .flatMap { character ->
                val id = character.optInt("mal_id")
                val gallery = if (id > 0) {
                    try {
                        get("$Catalog/characters/$id/pictures").objects()
                            .mapNotNull { it.optJSONObject("jpg")?.optString("image_url") }
                    } catch (_: IOException) {
                        emptyList()
                    }
                } else {
                    emptyList()
                }
                val portrait = character.optJSONObject("images")
                    ?.optJSONObject("jpg")?.optString("image_url")
                (gallery.filter { it != portrait } + listOfNotNull(portrait))
                    .filter { it.startsWith(PortraitCdn) }.distinct().take(4)
            }.distinct()

    private fun matchTitle(anime: Anime): Int? {
        val queries = listOf(anime.originalTitle, anime.title).filter { it.isNotBlank() }.distinct()
        val names = queries.map { normalize(it) }.filter { it.isNotEmpty() }.toSet()
        for (query in queries) {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val matches = get("$Catalog/anime?q=$encoded&limit=12&sfw=true").objects()
                .filter { item ->
                    val year = item.optInt("year", 0)
                    val titles = listOf(item.optString("title"), item.optString("title_english")) +
                        item.optJSONArray("titles").objects().map { it.optString("title") }
                    (anime.year == 0 || year == 0 || anime.year == year) &&
                        titles.any { normalize(it) in names }
                }
            if (matches.size == 1) return matches.single().optInt("mal_id").takeIf { it > 0 }
        }
        return null
    }

    private fun normalize(title: String) = title.lowercase().filter { it.isLetterOrDigit() }

    private fun get(url: String): JSONArray? = JSONObject(
        fetch(url) { readLimited(it, 4 * 1024 * 1024).toString(Charsets.UTF_8) }
    ).optJSONArray("data")

    private fun JSONArray?.objects(): List<JSONObject> =
        (0 until (this?.length() ?: 0)).mapNotNull { this?.optJSONObject(it) }

    private fun readPortrait(url: String): Bitmap? {
        val data = try {
            fetch(url) { readLimited(it, 5 * 1024 * 1024) }
        } catch (_: IOException) {
            return null
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        if (minOf(bounds.outWidth, bounds.outHeight) < 48) return null
        return BitmapFactory.decodeByteArray(data, 0, data.size, BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            val edge = maxOf(bounds.outWidth, bounds.outHeight)
            while (edge / inSampleSize.coerceAtLeast(1) > 800) {
                inSampleSize = inSampleSize.coerceAtLeast(1) * 2
            }
        })
    }

    private fun readLimited(input: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var size = input.read(buffer)
        while (size != -1) {
            if (output.size() + size > limit) throw IOException("Response exceeds size limit")
            output.write(buffer, 0, size)
            size = input.read(buffer)
        }
        return output.toByteArray()
    }

    private fun <T> fetch(url: String, read: (InputStream) -> T): T {
        val connection = URL(url).openConnection() as HttpsURLConnection
        return try {
            connection.connectTimeout = 12_000
            connection.readTimeout = 25_000
            connection.setRequestProperty("User-Agent", "OAlab Android")
            connection.inputStream.use(read)
        } finally {
            connection.disconnect()
        }
    }

    private fun modelFile(context: Context): File {
        val dir = File(context.filesDir, "home-model").apply { mkdirs() }
        val model = File(dir, "birefnext-aniseg.onnx")
        if (model.isFile && model.length() == ModelBytes) return model
        val temp = File(dir, "birefnext-aniseg.download")
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            fetch(ModelUrl) { stream ->
                DigestInputStream(stream, digest).use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        var read = input.read(buffer)
                        while (read != -1) {
                            total += read
                            check(total <= ModelBytes)
                            output.write(buffer, 0, read)
                            read = input.read(buffer)
                        }
                        check(total == ModelBytes)
                    }
                }
            }
            check(digest.digest().joinToString("") { "%02x".format(it) } == ModelSha256)
            check(temp.renameTo(model))
            File(dir, "isnet-anime.onnx").delete()
        } finally {
            temp.delete()
        }
        return model
    }

    private fun segment(env: OrtEnvironment, session: OrtSession, portrait: Bitmap): Bitmap? {
        val scale = ModelMaxEdge.toFloat() / maxOf(portrait.width, portrait.height)
        val width = (portrait.width * scale / 32).roundToInt().coerceAtLeast(2) * 32
        val height = (portrait.height * scale / 32).roundToInt().coerceAtLeast(2) * 32
        val plane = width * height
        val pixels = IntArray(plane)
        val input = Bitmap.createScaledBitmap(portrait, width, height, true)
        try {
            input.getPixels(pixels, 0, width, 0, 0, width, height)
        } finally {
            if (input !== portrait) input.recycle()
        }

        val tensor = FloatArray(plane * 3)
        for (i in pixels.indices) {
            val color = pixels[i]
            tensor[i] = (Color.red(color) / 255f - .485f) / .229f
            tensor[plane + i] = (Color.green(color) / 255f - .456f) / .224f
            tensor[2 * plane + i] = (Color.blue(color) / 255f - .406f) / .225f
        }

        val shape = longArrayOf(1, 3, height.toLong(), width.toLong())
        val inputName = session.inputNames.single()
        val maskName = session.outputNames.single()
        val mask = OnnxTensor.createTensor(env, FloatBuffer.wrap(tensor), shape).use { value ->
            session.run(mapOf(inputName to value), setOf(maskName)).use { result ->
                FloatArray(plane).also { (result[0] as OnnxTensor).floatBuffer.get(it) }
            }
        }
        if (mask.any { !it.isFinite() }) return null
        for (i in mask.indices) {
            val opacity = (((mask[i] - .06f) / .88f).coerceIn(0f, 1f) * 255f).roundToInt()
            pixels[i] = Color.argb(opacity, 255, 255, 255)
        }
        val matte = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        return try {
            applyMatte(portrait, matte)
        } finally {
            matte.recycle()
        }
    }

    private fun applyMatte(portrait: Bitmap, matte: Bitmap): Bitmap? {
        val width = portrait.width
        val height = portrait.height
        val scaledMask = Bitmap.createScaledBitmap(matte, width, height, true)
        val alpha = IntArray(width * height)
        try {
            scaledMask.getPixels(alpha, 0, width, 0, 0, width, height)
        } finally {
            if (scaledMask !== matte) scaledMask.recycle()
        }
        val bounds = visiblePortrait(alpha, width, height) ?: return null
        val colors = IntArray(alpha.size)
        portrait.getPixels(colors, 0, width, 0, 0, width, height)
        for (i in colors.indices) {
            colors[i] = (colors[i] and 0x00FFFFFF) or (alpha[i] and 0xFF000000.toInt())
        }
        return Bitmap.createBitmap(
            colors, bounds.top * width + bounds.left, width,
            bounds.right - bounds.left, bounds.bottom - bounds.top, Bitmap.Config.ARGB_8888
        )
    }

    private fun save(file: File, bitmap: Bitmap): File {
        val directory = checkNotNull(file.parentFile)
        directory.mkdirs()
        val temp = File.createTempFile("portrait", ".tmp", directory)
        try {
            temp.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            check(temp.renameTo(file))
        } finally {
            temp.delete()
        }

        directory.listFiles()?.filter { it.extension == "png" }?.sortedByDescending(File::lastModified)
            ?.drop(24)?.forEach { it.delete() }
        return file
    }
}
