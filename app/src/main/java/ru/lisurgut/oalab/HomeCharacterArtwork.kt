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
    private const val ModelUrl =
        "https://github.com/danielgatis/rembg/releases/download/v0.0.0/isnet-anime.onnx"
    private const val ModelBytes = 176_069_933L
    private const val ModelSha256 = "f15622d853e8260172812b657053460e20806f04b9e05147d49af7bed31a6e99"
    private const val ModelSize = 1024
    private const val InferenceMemoryBytes = 1_250_000_000L
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
        val output = File(directory, "isnet-v1-$release.png")
        if (output.isFile) return output
        val activityManager = app.getSystemService(ActivityManager::class.java) ?: return null
        val memory = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        if (Build.SUPPORTED_64_BIT_ABIS.isEmpty() || activityManager.isLowRamDevice ||
            memory.availMem < InferenceMemoryBytes
        ) return null

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
        val candidates = get("$Catalog/anime/$titleId/characters")
            .objects()
            .sortedWith(
                compareByDescending<JSONObject> { it.optString("role") == "Main" }
                    .thenByDescending { it.optInt("favorites") }
            )
            .mapNotNull {
                it.optJSONObject("character")?.optJSONObject("images")
                    ?.optJSONObject("jpg")?.optString("image_url")
                    ?.takeIf { url -> url.startsWith("https://cdn.myanimelist.net/images/characters/") }
            }.distinct().take(3)
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
        val model = File(dir, "isnet-anime.onnx")
        if (model.isFile && model.length() == ModelBytes) return model
        val temp = File(dir, "isnet-anime.download")
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
        } finally {
            temp.delete()
        }
        return model
    }

    private fun segment(env: OrtEnvironment, session: OrtSession, portrait: Bitmap): Bitmap? {
        val plane = ModelSize * ModelSize
        val pixels = IntArray(plane)
        val input = Bitmap.createScaledBitmap(portrait, ModelSize, ModelSize, true)
        input.getPixels(pixels, 0, ModelSize, 0, 0, ModelSize, ModelSize)
        if (input !== portrait) input.recycle()

        // ISNet Anime uses the maximum input sample and ImageNet RGB means, as in rembg.
        var maxSample = 1
        for (color in pixels) {
            maxSample = maxOf(maxSample, maxOf(Color.red(color), Color.green(color), Color.blue(color)))
        }
        val tensor = FloatArray(plane * 3)
        for (i in pixels.indices) {
            val color = pixels[i]
            tensor[i] = Color.red(color).toFloat() / maxSample - .485f
            tensor[plane + i] = Color.green(color).toFloat() / maxSample - .456f
            tensor[2 * plane + i] = Color.blue(color).toFloat() / maxSample - .406f
        }

        val shape = longArrayOf(1, 3, ModelSize.toLong(), ModelSize.toLong())
        val inputName = session.inputNames.single()
        val maskName = session.outputNames.first()
        val mask = OnnxTensor.createTensor(env, FloatBuffer.wrap(tensor), shape).use { value ->
            session.run(mapOf(inputName to value), setOf(maskName)).use { result ->
                FloatArray(plane).also { (result[0] as OnnxTensor).floatBuffer.get(it) }
            }
        }
        val min = mask.minOrNull() ?: return null
        val span = (mask.maxOrNull() ?: return null) - min
        if (!span.isFinite() || span <= .00001f) return null
        for (i in mask.indices) {
            pixels[i] = Color.argb((((mask[i] - min) / span) * 255).roundToInt(), 255, 255, 255)
        }
        val matte = Bitmap.createBitmap(pixels, ModelSize, ModelSize, Bitmap.Config.ARGB_8888)
        return try {
            applyMatte(portrait, matte)
        } finally {
            matte.recycle()
        }
    }

    private fun applyMatte(portrait: Bitmap, matte: Bitmap): Bitmap? {
        val width = portrait.width
        val height = portrait.height
        val croppedMask = Bitmap.createScaledBitmap(matte, width, height, true)
        val colors = IntArray(width * height)
        val alpha = IntArray(colors.size)
        portrait.getPixels(colors, 0, width, 0, 0, width, height)
        croppedMask.getPixels(alpha, 0, width, 0, 0, width, height)
        if (croppedMask !== matte) croppedMask.recycle()

        var left = width
        var right = -1
        var top = height
        var bottom = -1
        var foreground = 0
        for (index in colors.indices) {
            val opacity = Color.alpha(alpha[index])
            colors[index] = (colors[index] and 0x00FFFFFF) or (opacity shl 24)
            if (opacity >= 32) {
                val x = index % width
                val y = index / width
                left = minOf(left, x)
                right = maxOf(right, x)
                top = minOf(top, y)
                bottom = maxOf(bottom, y)
            }
            if (opacity >= 128) foreground++
        }
        if (foreground.toFloat() / colors.size !in 0.04f..0.985f) return null
        return Bitmap.createBitmap(
            colors, top * width + left, width, right - left + 1, bottom - top + 1,
            Bitmap.Config.ARGB_8888
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
