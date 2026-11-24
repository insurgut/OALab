package ru.lisurgut.oalab

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

data class LibraryEntry(
    val anime: Anime,
    val status: String = "Не смотрел",
    val favorite: Boolean = false,
    val lastEpisode: Int = 1,
    val lastPosition: Long = 0L
) {
    fun toJson() = JSONObject().apply {
        put("anime", anime.toJson())
        put("status", status)
        put("favorite", favorite)
        put("lastEpisode", lastEpisode)
        put("lastPosition", lastPosition)
    }
    companion object {
        fun parse(value: JSONObject): LibraryEntry = LibraryEntry(
            anime = Anime.fromJson(value.getJSONObject("anime")),
            status = value.optString("status", "Не смотрел"),
            favorite = value.optBoolean("favorite"),
            lastEpisode = value.optInt("lastEpisode", 1),
            lastPosition = value.optLong("lastPosition", 0L)
        )
    }
}

/** The source field is always AniLiberty; qualities are fallback HLS variants, not dub choices. */
data class StreamVariant(
    val source: String,
    val episode: Int,
    val language: String,
    val team: String,
    val voices: Int,
    val votes: Int,
    val popularity: Int,
    val timing: String,
    val url: String,
    val subtitle: String
)

class LocalLibrary(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "oalab_preferences", Context.MODE_PRIVATE
    )
    val entries = mutableStateListOf<LibraryEntry>()
    private val streams = mutableStateMapOf<String, List<StreamVariant>>()
    val catalogProvider: String = "AniLibria"
    val autoFallback: Boolean = true
    val language: String = "ru"
    var autoplay by mutableStateOf(prefs.getBoolean("autoplay", false))
        private set
    var seekSeconds by mutableIntStateOf(prefs.getInt("seek", 10))
        private set

    init {
        try {
            val list = JSONArray(prefs.getString("library", "[]"))
            for (i in 0 until list.length())
                entries.add(LibraryEntry.parse(list.getJSONObject(i)))
        } catch (_: Exception) { }
    }

    private fun same(a: Anime, b: Anime): Boolean = a.id == b.id

    fun entry(anime: Anime): LibraryEntry? =
        entries.firstOrNull { same(it.anime, anime) }

    fun isFavorite(anime: Anime): Boolean = entry(anime)?.favorite ?: false
    fun status(anime: Anime): String = entry(anime)?.status ?: "Не смотрел"
    fun favorites(): List<Anime> = entries.filter { it.favorite }.map { it.anime }
    fun inProgress(): List<LibraryEntry> = entries.filter { it.status == "Смотрю" }
    fun watched(): List<Anime> = entries.filter { it.status == "Просмотрено" }.map { it.anime }

    private fun change(anime: Anime, transform: (LibraryEntry) -> LibraryEntry) {
        val index = entries.indexOfFirst { same(it.anime, anime) }
        val before = if (index >= 0) entries[index] else LibraryEntry(anime)
        val after = transform(before)
        if (index >= 0) entries[index] = after else entries.add(after)
        prefs.edit().putString("library", JSONArray(entries.map { it.toJson() }).toString()).apply()
    }

    fun toggleFavorite(anime: Anime) =
        change(anime) { it.copy(anime = anime, favorite = !it.favorite) }

    fun setStatus(anime: Anime, status: String) =
        change(anime) { it.copy(anime = anime, status = status) }

    fun rememberPosition(anime: Anime, episode: Int, position: Long) {
        val before = entry(anime)
        if (before?.lastEpisode == episode &&
            abs(before.lastPosition - position) < 5_000L) return
        change(anime) {
            it.copy(
                anime = anime,
                status = if (it.status == "Не смотрел") "Смотрю" else it.status,
                lastEpisode = episode, lastPosition = position
            )
        }
    }

    fun preferredGenres(): List<String> =
        entries.filter { it.favorite || it.status == "Просмотрено" || it.status == "Смотрю" }
            .flatMap { it.anime.genres }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.map { it.key }

    fun updateAutoplay(value: Boolean) {
        autoplay = value
        prefs.edit().putBoolean("autoplay", value).apply()
    }
    fun setSeek(value: Int) {
        seekSeconds = value.coerceIn(5, 30)
        prefs.edit().putInt("seek", seekSeconds).apply()
    }
    fun saveCache(list: List<Anime>) {
        prefs.edit().putString(
            "catalog_cache", JSONArray(list.map { it.toJson() }).toString()
        ).apply()
    }
    fun cachedCatalog(): List<Anime> = try {
        val list = JSONArray(prefs.getString("catalog_cache", "[]"))
        (0 until list.length()).map { Anime.fromJson(list.getJSONObject(it)) }
            .filter { it.id.startsWith("aniliberty:") }
    } catch (_: Exception) { emptyList() }

    fun saveResolvedStreams(anime: Anime, variants: List<StreamVariant>) {
        streams[anime.id] = variants.filter { it.source == "AniLiberty" }
    }
    fun variantsFor(anime: Anime): List<StreamVariant> =
        streams[anime.id].orEmpty().filter { it.source == "AniLiberty" }
}
