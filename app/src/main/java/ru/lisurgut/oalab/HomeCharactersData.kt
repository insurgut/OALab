package ru.lisurgut.oalab

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

internal data class HomeCharacter(
    val id: Int,
    val name: String,
    val portrait: String,
    val role: String,
    val favorites: Int,
    val japaneseVoice: String?
)

internal data class CharacterProfile(val nativeName: String, val aliases: List<String>, val about: String)

internal object HomeCharactersData {
    private const val Base = "https://api.tenrai.org/v1"
    private const val MaxResponseChars = 2_000_000
    private const val CastCacheMs = 6 * 60 * 60 * 1000L

    suspend fun cast(context: Context, anime: Anime, refresh: Boolean): List<HomeCharacter>? =
        withContext(Dispatchers.IO) {
            val release = anime.id.removePrefix("aniliberty:").toIntOrNull() ?: return@withContext emptyList()
            val app = context.applicationContext
            val cache = File(app.cacheDir, "home-cast").apply { mkdirs() }
            val snapshot = File(cache, "release-$release.json")
            if (!refresh) return@withContext loadCache(snapshot)
            if (System.currentTimeMillis() - snapshot.lastModified() in 0L..<CastCacheMs) {
                loadCache(snapshot)?.let { return@withContext it }
            }
            val id = anime.malId.takeIf { it > 0 } ?: matchAnime(anime) ?: return@withContext emptyList()
            val data = request("/anime/$id/characters").optJSONArray("data")
                ?: throw IOException("Missing character list")
            val list = parseCast(data)
            runCatching {
                AtomicFile(snapshot).let { file ->
                    val output = file.startWrite()
                    try {
                        output.write(data.toString().toByteArray(Charsets.UTF_8))
                        file.finishWrite(output)
                    } catch (error: IOException) {
                        file.failWrite(output)
                        throw error
                    }
                }
                cache.listFiles { file -> file.name.endsWith(".json") }
                    ?.sortedByDescending(File::lastModified)?.drop(30)?.forEach { it.delete() }
            }
            list
        }

    suspend fun profile(characterId: Int): CharacterProfile = withContext(Dispatchers.IO) {
        val item = request("/characters/$characterId").optJSONObject("data")
            ?: throw IOException("Missing character profile")
        CharacterProfile(
            nativeName = item.text("name_kanji"),
            aliases = item.optJSONArray("nicknames").strings(),
            about = item.text("about")
                .replace(Regex("""(?is)\[spoiler(?:=[^\]]+)?].*?\[/spoiler]"""), "")
                .replace(Regex("""(?is)<spoiler(?:\s[^>]*)?>.*?</spoiler>"""), "").trim()
        )
    }

    suspend fun clearRetiredArtwork(context: Context) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val models = File(app.filesDir, "home-model")
        listOf("isnet-anime.onnx", "isnet-anime.download", "birefnext-aniseg.onnx", "birefnext-aniseg.download")
            .forEach { File(models, it).delete() }
        File(app.cacheDir, "home-characters").listFiles { file ->
            file.name.startsWith("isnet-v1-") || file.name.startsWith("silhouette-v2-")
        }?.forEach { it.delete() }
    }

    private fun loadCache(file: File): List<HomeCharacter>? = runCatching {
        AtomicFile(file).openRead().bufferedReader().use { parseCast(JSONArray(it.readText())) }
    }.getOrNull()

    private fun parseCast(data: JSONArray): List<HomeCharacter> = data.objects().mapNotNull { credit ->
        val character = credit.optJSONObject("character") ?: return@mapNotNull null
        val id = character.optInt("mal_id")
        val name = character.text("name")
        if (id <= 0 || name.isBlank()) return@mapNotNull null
        val voice = credit.optJSONArray("voice_actors").objects()
            .firstOrNull { it.text("language").equals("Japanese", true) }
            ?.optJSONObject("person")?.text("name")?.takeIf { it.isNotBlank() }
        HomeCharacter(
            id = id, name = name,
            portrait = character.optJSONObject("images")?.optJSONObject("jpg")
                ?.text("image_url")?.takeIf { it.startsWith("https://") && "/images/characters/" in it }.orEmpty(),
            role = credit.text("role"), favorites = credit.optInt("favorites"),
            japaneseVoice = voice
        )
    }.sortedWith(compareByDescending<HomeCharacter> { it.role == "Main" }.thenByDescending { it.favorites })
        .distinctBy { it.id }

    private fun matchAnime(anime: Anime): Int? {
        val queries = listOf(anime.originalTitle, anime.title).filter { it.isNotBlank() }.distinct()
        val names = queries.map(::normalize).filter { it.isNotEmpty() }.toSet()
        for (query in queries) {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val matches = request("/anime?q=$encoded&limit=12&sfw=true").optJSONArray("data").objects()
                .filter { item ->
                    val year = item.optInt("year")
                    val titles = listOf(item.text("title"), item.text("title_english")) +
                        item.optJSONArray("titles").objects().map { it.text("title") }
                    (anime.year == 0 || year == 0 || anime.year == year) &&
                        titles.any { normalize(it) in names }
                }
            if (matches.size == 1) return matches.single().optInt("mal_id").takeIf { it > 0 }
        }
        return null
    }

    private fun normalize(title: String) = title.lowercase().filter { it.isLetterOrDigit() }
    private fun JSONObject.text(key: String): String = optString(key).takeUnless { it == "null" }.orEmpty()
    private fun JSONArray?.objects() = (0 until (this?.length() ?: 0)).mapNotNull { this?.optJSONObject(it) }
    private fun JSONArray?.strings() = (0 until (this?.length() ?: 0)).mapNotNull {
        this?.optString(it)?.takeIf { value -> value.isNotBlank() && value != "null" }
    }

    private fun request(path: String): JSONObject {
        val connection = URL(Base + path).openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "OAlab Android")
            return connection.inputStream.bufferedReader().use { reader ->
                val content = StringBuilder()
                val buffer = CharArray(4096)
                while (true) {
                    val size = reader.read(buffer)
                    if (size < 0) break
                    if (content.length + size > MaxResponseChars) throw IOException("Character response too large")
                    content.append(buffer, 0, size)
                }
                JSONObject(content.toString())
            }
        } finally {
            connection.disconnect()
        }
    }
}
