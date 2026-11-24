package ru.lisurgut.oalab

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Only AniLiberty/AniLibria is queried for catalogue metadata or episode media. */
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
    fun toJson(): JSONObject = JSONObject().apply {
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
        fun fromJson(item: JSONObject): Anime = Anime(
            id = item.optString("id"),
            anilistId = item.optInt("anilistId"),
            malId = item.optInt("malId"),
            title = item.optString("title", "Аниме"),
            originalTitle = item.optString("originalTitle"),
            description = item.optString("description"),
            cover = item.optString("cover"),
            banner = item.optString("banner"),
            score = item.optInt("score"),
            year = item.optInt("year"),
            episodes = item.optInt("episodes"),
            genres = item.optJSONArray("genres")?.strings() ?: emptyList(),
            format = item.optString("format"),
            trailer = item.optString("trailer")
        )
    }
}

fun JSONArray.strings(): List<String> = (0 until length()).mapNotNull {
    optString(it).takeIf { value -> value.isNotBlank() }
}

object AnimeApi {
    val categories = listOf(
        "Все", "Экшен", "Приключения", "Комедия", "Драма", "Фэнтези",
        "Романтика", "Фантастика", "Повседневность", "Спорт", "Детектив"
    )
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
        // Parameter retained for existing call sites; no provider switch exists.
        AniLibertyApi.catalog(query, category, sort, page, fromYear, toYear)
    }
}
