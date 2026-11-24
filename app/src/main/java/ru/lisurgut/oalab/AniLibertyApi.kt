package ru.lisurgut.oalab

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt

/**
 * AniLiberty (AniLibria) v1 public streaming API.
 *
 * Both hosts are owned by the provider. No token, scraped private keys, or DRM bypass.
 * Respect the provider's geo/copyright flags and preserve HLS URL query parameters.
 */
object AniLibertyApi {
    private const val PRIMARY = "https://aniliberty.top"
    private const val MIRROR = "https://anilibria.top"
    private val hosts = listOf(PRIMARY, MIRROR)

    private fun params(values: List<Pair<String, String>>): String =
        values.joinToString("&") {
            URLEncoder.encode(it.first, "UTF-8") + "=" + URLEncoder.encode(it.second, "UTF-8")
        }

    private fun request(path: String): String {
        var last: Exception? = null
        for (host in hosts) {
            try {
                val url = URL(host + "/api/v1" + path)
                val conn = url.openConnection() as HttpURLConnection
                try {
                    conn.connectTimeout = 9_000
                    conn.readTimeout = 14_000
                    conn.setRequestProperty("Accept", "application/json")
                    conn.setRequestProperty("User-Agent", "OAlab/0.4 (Android; public API)")
                    val status = conn.responseCode
                    if (status !in 200..299) {
                        error("AniLiberty HTTP $status")
                    }
                    return conn.inputStream.bufferedReader().use { reader ->
                        val builder = StringBuilder()
                        val buffer = CharArray(4096)
                        while (true) {
                            val size = reader.read(buffer)
                            if (size < 0) break
                            builder.append(buffer, 0, size)
                            if (builder.length > 4_000_000) error("Слишком большой ответ AniLiberty")
                        }
                        builder.toString()
                    }
                } finally {
                    conn.disconnect()
                }
            } catch (error: Exception) {
                last = error
            }
        }
        throw IllegalStateException(
            "Видеокаталог AniLiberty сейчас недоступен: " +
                (last?.localizedMessage ?: "ошибка сети"), last
        )
    }

    private fun pathValue(raw: String?, host: String = PRIMARY): String {
        val value = raw?.takeIf { it.isNotBlank() && it != "null" } ?: return ""
        return when {
            value.startsWith("https://") -> value
            value.startsWith("//") -> "https:" + value
            value.startsWith("/") -> host + value
            else -> ""
        }
    }

    private fun poster(root: JSONObject): String {
        val poster = root.optJSONObject("poster") ?: return ""
        val optimized = poster.optJSONObject("optimized")
        val chosen = optimized?.optString("preview")?.takeIf { it.isNotBlank() && it != "null" }
            ?: poster.optString("preview").takeIf { it.isNotBlank() && it != "null" }
            ?: poster.optString("src")
        return pathValue(chosen)
    }

    private fun genres(root: JSONObject): List<String> {
        val result = mutableListOf<String>()
        val list = root.optJSONArray("genres") ?: return result
        for (i in 0 until list.length()) {
            val item = list.optJSONObject(i) ?: continue
            item.optString("name").takeIf { it.isNotBlank() && it != "null" }?.let(result::add)
        }
        return result
    }

    private fun allowed(root: JSONObject): Boolean {
        return !root.optBoolean("is_blocked_by_geo") &&
            !root.optBoolean("is_blocked_by_copyrights") &&
            root.optJSONObject("age_rating")?.optBoolean("is_adult", false) != true
    }

    private fun asAnime(value: JSONObject): Anime? {
        if (!allowed(value)) return null
        val id = value.optInt("id")
        if (id <= 0) return null
        val name = value.optJSONObject("name") ?: JSONObject()
        val title = name.optString("main").takeIf { it.isNotBlank() && it != "null" }
            ?: name.optString("english").takeIf { it.isNotBlank() && it != "null" }
            ?: return null
        val rating = value.optJSONObject("mal") ?: JSONObject()
        val scoreRaw = rating.optDouble("score", Double.NaN)
        val score = if (!scoreRaw.isNaN() && scoreRaw in 0.0..10.0)
            (scoreRaw * 10).roundToInt() else 0
        val image = poster(value)
        return Anime(
            id = "aniliberty:$id",
            malId = rating.optInt("id"),
            title = title,
            originalTitle = name.optString("english"),
            description = value.optString("description").takeIf { it != "null" } ?: "",
            cover = image,
            banner = image,
            score = score,
            year = value.optInt("year"),
            episodes = value.optInt("episodes_total"),
            genres = genres(value),
            format = value.optJSONObject("type")?.optString("value") ?: "TV"
        )
    }

    private fun arrayOf(input: String): JSONArray {
        val raw = input.trimStart()
        return when {
            raw.startsWith("[") -> JSONArray(raw)
            raw.startsWith("{") -> JSONObject(raw).optJSONArray("data") ?: JSONArray()
            else -> JSONArray()
        }
    }

    private val genreNames = mapOf(
        "Экшен" to listOf("экшен", "боевик", "action"),
        "Приключения" to listOf("приключен", "adventure"),
        "Комедия" to listOf("комеди", "comedy"),
        "Драма" to listOf("драм", "drama"),
        "Фэнтези" to listOf("фэнтези", "fantasy"),
        "Романтика" to listOf("романтик", "romance"),
        "Фантастика" to listOf("фантастик", "sci-fi", "science fiction"),
        "Повседневность" to listOf("повседнев", "slice of life"),
        "Спорт" to listOf("спорт", "sports"),
        "Детектив" to listOf("детектив", "мистери", "mystery")
    )

    fun catalog(
        search: String,
        category: String,
        sort: String,
        page: Int,
        fromYear: Int?,
        toYear: Int?
    ): List<Anime> {
        val query = mutableListOf(
            "page" to page.coerceAtLeast(1).toString(),
            "limit" to "35"
        )
        if (search.isNotBlank()) query += "f[search]" to search.trim()
        if (fromYear != null) query += "f[years][from_year]" to fromYear.toString()
        if (toYear != null) query += "f[years][to_year]" to toYear.toString()
        val order = when (sort) {
            "Новые" -> "YEAR_DESC"
            "Высокий рейтинг" -> "RATING_DESC"
            else -> "FRESH_AT_DESC"
        }
        query += "f[sorting]" to order
        val json = request("/anime/catalog/releases?" + params(query))
        val aliases = genreNames[category].orEmpty()
        val releases = arrayOf(json)
        return (0 until releases.length())
            .mapNotNull { releases.optJSONObject(it)?.let(::asAnime) }
            .filter { entry ->
                aliases.isEmpty() || entry.genres.any { genre ->
                    aliases.any { alias -> genre.lowercase().contains(alias) }
                }
            }
    }

    private fun normalize(text: String): String =
        text.lowercase().replace('ё', 'е')
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim().replace(Regex("\\s+"), " ")

    private fun matchingRelease(anime: Anime): Int? {
        val variants = listOf(anime.title, anime.originalTitle)
            .filter { it.isNotBlank() && it != "null" }
            .map(::normalize).filter { it.isNotEmpty() }.distinct()
        for (title in listOf(anime.originalTitle, anime.title).filter { it.isNotBlank() }) {
            val url = "/app/search/releases?" + params(listOf("query" to title))
            val releases = try { arrayOf(request(url)) } catch (_: Exception) { continue }
            val matches = (0 until releases.length()).mapNotNull { releases.optJSONObject(it) }
                .filter(::allowed)
            val best = matches.firstOrNull { release ->
                val name = release.optJSONObject("name") ?: JSONObject()
                val names = listOf(name.optString("main"), name.optString("english")) +
                    name.optString("alternative").split(",")
                val normalized = names.map(::normalize).filter { it.isNotEmpty() }
                val yearMatches = anime.year == 0 || release.optInt("year") == 0 ||
                    anime.year == release.optInt("year")
                yearMatches && normalized.any { it in variants }
            }
            if (best != null) return best.optInt("id").takeIf { it > 0 }
        }
        return null
    }


    private fun skipWindow(root: JSONObject?, durationMs: Long): SkipWindow? {
        if (root == null || root.isNull("start") || root.isNull("stop")) return null
        val begin = root.optDouble("start", Double.NaN)
        val stop = root.optDouble("stop", Double.NaN)
        if (!begin.isFinite() || !stop.isFinite() || begin < 0 ||
            stop <= begin || stop - begin > 15 * 60
        ) return null
        val a = (begin * 1000).toLong()
        val b = (stop * 1000).toLong()
        if (durationMs > 0 && b > durationMs + 1_000L) return null
        return SkipWindow(a, b)
    }

    private fun episodePreview(ep: JSONObject): String {
        val img = ep.optJSONObject("preview") ?: return ""
        val optimized = img.optJSONObject("optimized")
        return pathValue(
            optimized?.optString("preview")?.takeIf { it.isNotBlank() && it != "null" }
                ?: img.optString("preview").takeIf { it.isNotBlank() && it != "null" }
                ?: img.optString("src")
        )
    }

    fun streams(anime: Anime): List<StreamVariant> {
        val id = if (anime.id.startsWith("aniliberty:"))
            anime.id.substringAfter("aniliberty:").toIntOrNull()
        else matchingRelease(anime)
        if (id == null || id <= 0) return emptyList()
        val release = JSONObject(request("/anime/releases/$id"))
        if (!allowed(release)) return emptyList()
        val episodes = release.optJSONArray("episodes") ?: return emptyList()
        val result = mutableListOf<StreamVariant>()
        for (i in 0 until episodes.length()) {
            val ep = episodes.optJSONObject(i) ?: continue
            val ordinal = ep.optDouble("ordinal", Double.NaN)
            if (ordinal.isNaN() || ordinal < 1 || ordinal % 1.0 != 0.0) continue

            val number = ordinal.toInt()
            val seconds = ep.optLong("duration", 0L)
            val durationMs = seconds.coerceIn(0L, 12L * 60 * 60) * 1_000L
            val episodeTitle = ep.optString("name").takeUnless { it.isBlank() || it == "null" }
                ?: ep.optString("name_english").takeUnless { it.isBlank() || it == "null" }
                ?: "Серия $number"
            val details = EpisodeDetails(
                episode = number,
                title = episodeTitle,
                durationMs = durationMs,
                opening = skipWindow(ep.optJSONObject("opening"), durationMs),
                ending = skipWindow(ep.optJSONObject("ending"), durationMs),
                previewUrl = episodePreview(ep)
            )
            // Use only published, per-episode timing metadata from AniLibria.
            for ((quality, name) in listOf(
                "hls_1080" to "1080p", "hls_720" to "720p", "hls_480" to "480p"
            )) {
                val stream = ep.optString(quality)
                if (!stream.startsWith("https://")) continue
                result.add(StreamVariant(
                    source = "AniLiberty",
                    episode = number,
                    language = "ru",
                    team = "AniLibria · $name",
                    voices = 0,
                    votes = 0,
                    popularity = when (quality) {
                        "hls_1080" -> 1080
                        "hls_720" -> 720
                        else -> 480
                    },
                    timing = ep.optString("name").takeIf { it.isNotBlank() && it != "null" }
                        ?: "Оригинальные тайминги",
                    url = stream,
                    subtitle = "",
                    details = details
                ))
            }
        }
        return result.distinctBy { it.episode.toString() + "|" + it.url }
    }

    /**
     * Provider's schedule returns weekly slots, not precise publication dates/times.
     * Live /week is a raw array; its documented format can also wrap it in data.
     * published_release_episode describes the current week, not the NEXT episode.
     */
    fun weeklySchedule(): List<ScheduledAnime> {
        val rows = arrayOf(request("/anime/schedule/week"))
        val result = mutableListOf<ScheduledAnime>()
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val release = row.optJSONObject("release") ?: continue
            if (!allowed(release) || row.optBoolean("full_season_is_released", false)) continue
            val anime = asAnime(release) ?: continue
            val dayRaw = release.opt("publish_day")
            val weekday = when (dayRaw) {
                is JSONObject -> dayRaw.opt("value")?.toString()?.toIntOrNull()
                is Number -> dayRaw.toInt()
                is String -> dayRaw.toIntOrNull()
                else -> null
            } ?: continue
            if (weekday !in 1..7) continue
            val episode = row.opt("next_release_episode_number")
                ?.toString()?.toDoubleOrNull()
                ?.takeIf { it.isFinite() && it > 0.0 && it % 1.0 == 0.0 }
                ?.toInt()
            result += ScheduledAnime(
                anime = anime,
                dayOfWeek = weekday,
                nextEpisode = episode
            )
        }
        return result.distinctBy { it.anime.id to it.dayOfWeek }
    }

    suspend fun resolveWeeklySchedule(): List<ScheduledAnime> =
        withContext(Dispatchers.IO) { weeklySchedule() }

    suspend fun resolve(anime: Anime): List<StreamVariant> = withContext(Dispatchers.IO) {
        streams(anime)
    }
}
