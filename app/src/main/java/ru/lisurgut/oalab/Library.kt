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


data class SkipWindow(val startMs: Long, val stopMs: Long) {
    fun contains(positionMs: Long): Boolean = positionMs in startMs until stopMs
}

data class EpisodeDetails(
    val episode: Int,
    val title: String,
    val durationMs: Long,
    val opening: SkipWindow? = null,
    val ending: SkipWindow? = null,
    val previewUrl: String = ""
)

/** AniLibria is the only video source; qualities are HLS variants, not dubs. */
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
    val subtitle: String,
    val details: EpisodeDetails? = null
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
    var skipOpenings by mutableStateOf(prefs.getBoolean("skip_openings", false))
        private set
    var skipEndings by mutableStateOf(prefs.getBoolean("skip_endings", false))
        private set
    var mergeEpisodes by mutableStateOf(prefs.getBoolean("merge_episodes", false))
        private set
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
    fun favorites(): List<Anime> = entries.filter { it.favorite && it.anime.id.startsWith("aniliberty:") }.map { it.anime }
    fun inProgress(): List<LibraryEntry> = entries.filter { it.status == "Смотрю" && it.anime.id.startsWith("aniliberty:") }
    fun watched(): List<Anime> = entries.filter { it.status == "Просмотрено" && it.anime.id.startsWith("aniliberty:") }.map { it.anime }

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
        entries.filter { it.anime.id.startsWith("aniliberty:") &&
            (it.favorite || it.status == "Просмотрено" || it.status == "Смотрю") }
            .flatMap { it.anime.genres }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.map { it.key }

    fun updateSkipOpenings(value: Boolean) {
        skipOpenings = value
        prefs.edit().putBoolean("skip_openings", value).apply()
    }
    fun updateSkipEndings(value: Boolean) {
        skipEndings = value
        prefs.edit().putBoolean("skip_endings", value).apply()
    }
    fun updateMergeEpisodes(value: Boolean) {
        mergeEpisodes = value
        prefs.edit().putBoolean("merge_episodes", value).apply()
    }

    /** Save a separate position for every episode, plus a virtual cumulative position. */
    fun episodePosition(anime: Anime, episode: Int): Long =
        prefs.getLong("episode|" + anime.id + "|" + episode, 0L)

    fun totalPosition(anime: Anime): Long =
        prefs.getLong("total|" + anime.id, 0L)

    fun recordPlayback(
        anime: Anime, episode: Int, position: Long,
        virtualPosition: Long, updateLast: Boolean = true
    ) {
        prefs.edit()
            .putLong("episode|" + anime.id + "|" + episode, position.coerceAtLeast(0L))
            .putLong("total|" + anime.id, virtualPosition.coerceAtLeast(0L))
            .apply()
        if (updateLast) rememberPosition(anime, episode, position)
    }

    fun episodeDetails(anime: Anime): List<EpisodeDetails> =
        variantsFor(anime).mapNotNull { it.details }.distinctBy { it.episode }
            .sortedBy { it.episode }

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
