package ru.lisurgut.oalab

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt

data class OfficialWatch(val site: String, val url: String, val episode: String = "") {
    fun toJson() = JSONObject().put("site",site).put("url",url).put("episode",episode)
    companion object {
        fun parse(v:JSONObject) = OfficialWatch(v.optString("site"),v.optString("url"),v.optString("episode"))
    }
}

data class Anime(
    val id: String,
    val anilistId: Int = 0,
    val malId: Int = 0,
    val title: String,
    val originalTitle: String = "",
    val description: String = "",
    val cover: String = "",
    val banner: String = "",
    val score: Int = 0,
    val year: Int = 0,
    val episodes: Int = 0,
    val genres: List<String> = emptyList(),
    val format: String = "",
    val trailer: String = "",
    val official: List<OfficialWatch> = emptyList()
) {
    fun toJson() = JSONObject().apply {
        put("id", id)
        put("anilistId", anilistId)
        put("malId", malId)
        put("title", title)
        put("originalTitle", originalTitle)
        put("description", description)
        put("cover", cover)
        put("banner", banner)
        put("score", score)
        put("year", year)
        put("episodes", episodes)
        put("genres", JSONArray(genres))
        put("format", format)
        put("trailer", trailer)
        put("official", JSONArray(official.map { it.toJson() }))
    }

    companion object {
        fun fromJson(value: JSONObject): Anime = Anime(
            id = value.optString("id"),
            anilistId = value.optInt("anilistId"),
            malId = value.optInt("malId"),
            title = value.optString("title"),
            originalTitle = value.optString("originalTitle"),
            description = value.optString("description"),
            cover = value.optString("cover"),
            banner = value.optString("banner"),
            score = value.optInt("score"),
            year = value.optInt("year"),
            episodes = value.optInt("episodes"),
            genres = value.optJSONArray("genres")?.strings() ?: emptyList(),
            format = value.optString("format"),
            trailer = value.optString("trailer"),
            official = (value.optJSONArray("official") ?: JSONArray()).let { array ->
                (0 until array.length()).map { OfficialWatch.parse(array.getJSONObject(it)) }
            }
        )
    }
}

fun JSONArray.strings(): List<String> = (0 until length()).mapNotNull {
    optString(it).takeIf { text -> text.isNotBlank() }
}

object AnimeApi {
    // The metadata providers are intentionally separate from video sources.
    private val genres = mapOf(
        "Экшен" to Pair("Action", 1),
        "Приключения" to Pair("Adventure", 2),
        "Комедия" to Pair("Comedy", 4),
        "Драма" to Pair("Drama", 8),
        "Фэнтези" to Pair("Fantasy", 10),
        "Романтика" to Pair("Romance", 22),
        "Фантастика" to Pair("Sci-Fi", 24),
        "Повседневность" to Pair("Slice of Life", 36),
        "Спорт" to Pair("Sports", 30),
        "Детектив" to Pair("Mystery", 7)
    )
    val categories = listOf("Все") + genres.keys.toList()
    val sorting = listOf("Популярные", "В тренде", "Высокий рейтинг", "Новые")

    suspend fun load(
        provider: String,
        query: String = "",
        category: String = "Все",
        sort: String = "Популярные",
        page: Int = 1,
        fromYear: Int? = null,
        toYear: Int? = null
    ): List<Anime> = withContext(Dispatchers.IO) {
        // "Авто" is now the fully streamable AniLiberty catalog, not metadata-only titles.
        if (provider == "Авто" || provider == "AniLiberty") {
            return@withContext AniLibertyApi.catalog(
                query, category, sort, page, fromYear, toYear
            )
        }
        val providers = listOf(provider)
        var failure: Exception? = null
        for (source in providers) {
            try {
                val result = when(source) {
                    "Jikan" -> jikan(query,category,sort,page,fromYear,toYear)
                    "Kitsu" -> kitsu(query,category,sort,page,fromYear,toYear)
                    else -> anilist(query,category,sort,page,fromYear,toYear)
                }
                if(result.isNotEmpty()) return@withContext result
            } catch(e: Exception) { failure = e }
        }
        if (failure != null) throw failure
        emptyList()
    }

    private fun anilist(query: String, category: String, sort: String, page: Int, fromYear: Int?, toYear: Int?): List<Anime> {
        val dates = listOfNotNull(
            fromYear?.let { "startDate_greater:" + ((it - 1)*10000 + 1231) },
            toYear?.let { "startDate_lesser:" + ((it + 1)*10000 + 101) }
        ).joinToString(",")
        val dateArguments = if (dates.isBlank()) "" else "," + dates
        val sortId = when (sort) {
            "В тренде" -> "TRENDING_DESC"
            "Высокий рейтинг" -> "SCORE_DESC"
            "Новые" -> "START_DATE_DESC"
            else -> "POPULARITY_DESC"
        }
        val request = JSONObject().put(
            "query", """
            query Catalog(@page:Int,@search:String,@genre:String) {
              Page(page:@page,perPage:24) {
                media(type:ANIME,isAdult:false,sort:$sortId,search:@search,genre:@genre$dateArguments) {
                  id idMal
                  title { english romaji native }
                  coverImage { extraLarge large }
                  bannerImage
                  description(asHtml:false)
                  seasonYear episodes averageScore format genres
                  trailer { site id }
                  streamingEpisodes { site title url }
                  externalLinks { site url }
                }
              }
            }
            """.trimIndent().replace('@', 36.toChar())
        )
        request.put("variables", JSONObject().apply {
            put("page", page)
            if (query.isNotBlank()) put("search", query)
            genres[category]?.first?.let { put("genre", it) }
        })
        val data = http("https://graphql.anilist.co", request)
            .getJSONObject("data").getJSONObject("Page").getJSONArray("media")
        return (0 until data.length()).map { i ->
            val item = data.getJSONObject(i)
            val titles = item.optJSONObject("title") ?: JSONObject()
            val cover = item.optJSONObject("coverImage") ?: JSONObject()
            val trailer = item.optJSONObject("trailer")
            val id = item.getInt("id")
            Anime(
                id = "anilist:" + id,
                anilistId = id,
                malId = item.optInt("idMal"),
                title = titles.optString("english").takeIf { it.isNotBlank() && it != "null" }
                    ?: titles.optString("romaji", "Без названия"),
                originalTitle = titles.optString("native"),
                description = item.optString("description").replace(Regex("<[^>]*>"), "")
                    .replace("&quot;", "\"").replace("&amp;", "&"),
                cover = cover.optString("extraLarge").takeIf { it.isNotBlank() && it != "null" }
                    ?: cover.optString("large"),
                banner = item.optString("bannerImage").takeIf { it != "null" } ?: "",
                score = item.optInt("averageScore"),
                year = item.optInt("seasonYear"),
                episodes = item.optInt("episodes"),
                genres = item.optJSONArray("genres")?.strings() ?: emptyList(),
                format = item.optString("format"),
                trailer = if (trailer?.optString("site") == "youtube")
                    trailer.optString("id") else "",
                official = buildList {
                    val episodes = item.optJSONArray("streamingEpisodes") ?: JSONArray()
                    for (index in 0 until episodes.length()) {
                        val ep = episodes.getJSONObject(index)
                        val url = ep.optString("url")
                        if (url.startsWith("https://")) add(
                            OfficialWatch(ep.optString("site","Онлайн"),url,ep.optString("title"))
                        )
                    }
                    val sites = item.optJSONArray("externalLinks") ?: JSONArray()
                    for (index in 0 until sites.length()) {
                        val link = sites.getJSONObject(index)
                        val url = link.optString("url")
                        if (url.startsWith("https://")) add(OfficialWatch(link.optString("site","Сервис"),url))
                    }
                }.distinctBy { it.url }
            )
        }
    }

    private fun jikan(query: String, category: String, sort: String, page: Int, fromYear: Int?, toYear: Int?): List<Anime> {
        val params = mutableListOf("page=" + page, "limit=24", "sfw=true")
        if (query.isNotBlank()) params += "q=" + URLEncoder.encode(query, "UTF-8")
        genres[category]?.second?.let { params += "genres=" + it }
        fromYear?.let { params += "start_date=" + it + "-01-01" }
        toYear?.let { params += "end_date=" + it + "-12-31" }
        if (query.isBlank() && category == "Все" && sort == "Популярные" && fromYear == null && toYear == null) {
            // Jikan's top/anime is its stable ranked endpoint.
            val result = http("https://api.jikan.moe/v4/top/anime?" + params.joinToString("&"))
            return parseJikan(result.getJSONArray("data"))
        }
        params += when (sort) {
            "Высокий рейтинг" -> "order_by=score&sort=desc"
            "Новые" -> "order_by=start_date&sort=desc"
            "В тренде" -> "order_by=popularity&sort=asc"
            else -> "order_by=popularity&sort=asc"
        }
        return parseJikan(http("https://api.jikan.moe/v4/anime?" + params.joinToString("&")).getJSONArray("data"))
    }

    private fun parseJikan(data: JSONArray): List<Anime> = (0 until data.length()).map { index ->
        val item = data.getJSONObject(index)
        val id = item.optInt("mal_id")
        val images = item.optJSONObject("images")?.optJSONObject("jpg") ?: JSONObject()
        val trailer = item.optJSONObject("trailer")
        val genres = item.optJSONArray("genres") ?: JSONArray()
        Anime(
            id = "mal:" + id,
            malId = id,
            title = item.optString("title_english").takeIf { it.isNotBlank() && it != "null" }
                ?: item.optString("title", "Без названия"),
            originalTitle = item.optString("title_japanese"),
            description = item.optString("synopsis"),
            cover = images.optString("large_image_url"),
            banner = images.optString("large_image_url"),
            score = (item.optDouble("score", 0.0) * 10).toInt(),
            year = item.optInt("year"),
            episodes = item.optInt("episodes"),
            genres = (0 until genres.length()).map { genres.getJSONObject(it).optString("name") },
            format = item.optString("type"),
            trailer = trailer?.optString("youtube_id") ?: "",
            official = buildList {
                val external = item.optJSONArray("streaming") ?: JSONArray()
                for (i in 0 until external.length()) {
                    val entry = external.optJSONObject(i) ?: continue
                    val url = entry.optString("url")
                    if (url.startsWith("https://"))
                        add(OfficialWatch(entry.optString("name","Смотреть"),url))
                }
            }
        )
    }

    private fun kitsu(query: String, category: String, sort: String, page: Int, fromYear: Int?, toYear: Int?): List<Anime> {
        val options = mutableListOf(
            "page%5Blimit%5D=24",
            "page%5Boffset%5D=" + (page - 1).coerceAtLeast(0) * 24,
            "sort=" + when(sort) {
                "Высокий рейтинг" -> "-averageRating"
                "Новые" -> "-startDate"
                else -> "-userCount"
            }
        )
        if(query.isNotBlank()) options += "filter%5Btext%5D=" + URLEncoder.encode(query, "UTF-8")
        val items = http("https://kitsu.io/api/edge/anime?" + options.joinToString("&")).getJSONArray("data")
        return (0 until items.length()).mapNotNull { i ->
            val item = items.getJSONObject(i)
            val attributes = item.optJSONObject("attributes") ?: return@mapNotNull null
            val id = item.optString("id")
            val date = attributes.optString("startDate").take(4).toIntOrNull() ?: 0
            val cover = attributes.optJSONObject("posterImage") ?: JSONObject()
            val banner = attributes.optJSONObject("coverImage") ?: JSONObject()
            Anime(
                id = "kitsu:" + id,
                title = attributes.optString("canonicalTitle").ifBlank { "Без названия" },
                originalTitle = attributes.optJSONObject("titles")?.optString("ja_jp") ?: "",
                description = attributes.optString("synopsis"),
                cover = cover.optString("original"),
                banner = banner.optString("original"),
                score = attributes.optString("averageRating").toFloatOrNull()?.roundToInt() ?: 0,
                year = date, episodes = attributes.optInt("episodeCount"),
                format = attributes.optString("subtype").uppercase(),
                official = listOf(OfficialWatch("Kitsu", "https://kitsu.app/anime/" + id))
            )
        }.filter { item ->
            (fromYear == null || item.year >= fromYear) &&
            (toYear == null || item.year <= toYear)
        }
    }

    fun fetchText(url: String): String {
        require(url.startsWith("https://")) { "Поддерживается только HTTPS" }
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 12000
            connection.readTimeout = 12000
            connection.setRequestProperty("User-Agent","OALab/0.2 Android")
            if (connection.responseCode !in 200..299) error("HTTP " + connection.responseCode)
            return connection.inputStream.bufferedReader().use { reader ->
                val text = StringBuilder()
                val chars = CharArray(4096)
                while(true) {
                    val count = reader.read(chars)
                    if(count < 0) break
                    text.append(chars,0,count)
                    if(text.length > 3_000_000) error("Источник слишком большой (3 МБ)")
                }
                text.toString()
            }
        } finally { connection.disconnect() }
    }

    fun http(url: String, body: JSONObject? = null): JSONObject {
        require(url.startsWith("https://")) { "Поддерживается только HTTPS" }
        val connection = (URL(url).openConnection() as HttpURLConnection)
        try {
            connection.connectTimeout = 12000
            connection.readTimeout = 12000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "OALab/0.1 Android")
            if (body != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            if (connection.responseCode !in 200..299)
                error("HTTP " + connection.responseCode + ": " + connection.responseMessage)
            val response = connection.inputStream.bufferedReader().use { reader ->
                // Limit the amount of untrusted remote JSON we parse.
                val output = StringBuilder()
                val chars = CharArray(4096)
                while (true) {
                    val count = reader.read(chars)
                    if (count < 0) break
                    output.append(chars, 0, count)
                    if (output.length > 3_000_000) error("Фид слишком большой (лимит 3 МБ)")
                }
                output.toString()
            }
            return JSONObject(response)
        } finally {
            connection.disconnect()
        }
    }
}
