package ru.lisurgut.oalab

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

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
    val trailer: String = ""
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
            trailer = value.optString("trailer")
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
        page: Int = 1
    ): List<Anime> = withContext(Dispatchers.IO) {
        if (provider == "Jikan") jikan(query, category, sort, page)
        else anilist(query, category, sort, page)
    }

    private fun anilist(query: String, category: String, sort: String, page: Int): List<Anime> {
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
                media(type:ANIME,isAdult:false,sort:$sortId,search:@search,genre:@genre) {
                  id idMal
                  title { english romaji native }
                  coverImage { extraLarge large }
                  bannerImage
                  description(asHtml:false)
                  seasonYear episodes averageScore format genres
                  trailer { site id }
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
                    trailer.optString("id") else ""
            )
        }
    }

    private fun jikan(query: String, category: String, sort: String, page: Int): List<Anime> {
        val params = mutableListOf("page=" + page, "limit=24", "sfw=true")
        if (query.isNotBlank()) params += "q=" + URLEncoder.encode(query, "UTF-8")
        genres[category]?.second?.let { params += "genres=" + it }
        if (query.isBlank() && category == "Все" && sort == "Популярные") {
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
            trailer = trailer?.optString("youtube_id") ?: ""
        )
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
