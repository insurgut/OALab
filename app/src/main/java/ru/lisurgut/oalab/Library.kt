package ru.lisurgut.oalab

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
    var dataSaver by mutableStateOf(prefs.getBoolean("data_saver", false))
        private set
    /** 0 = network-adaptive auto, otherwise exact requested stream resolution. */
    var videoQuality by mutableIntStateOf(
        prefs.getInt("video_quality", 0).takeIf { it in setOf(0, 480, 720, 1080) } ?: 0
    )
        private set
    var hiddenRecommendations by mutableStateOf(
        prefs.getStringSet("hidden_recommendations", emptySet())?.toSet() ?: emptySet()
    )
        private set
    var recommendationGenres by mutableStateOf(
        prefs.getStringSet("recommendation_genres", emptySet())?.toSet() ?: emptySet()
    )
        private set
    var recommendationMode by mutableStateOf(
        runCatching {
            RecommendationMode.valueOf(
                prefs.getString("recommendation_mode", "PERSONAL") ?: "PERSONAL"
            )
        }.getOrDefault(RecommendationMode.PERSONAL)
    )
        private set
    var enablePip by mutableStateOf(prefs.getBoolean("pip_enabled", true))
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

    fun globalEpisodePosition(anime: Anime, episode: Int, timeMs: Long): Long {
        val details = episodeDetails(anime)
        val index = details.indexOfFirst { it.episode == episode }
        if (index < 0) return timeMs
        return EpisodeTimeline.globalPosition(details.map { it.durationMs }, index, timeMs)
            ?: timeMs
    }

    fun markEpisodeFinished(anime: Anime, episode: Int, durationMs: Long) {
        if (durationMs <= 0) return
        prefs.edit()
            .putLong("episode|" + anime.id + "|" + episode, durationMs)
            .apply()
    }

    fun updateDataSaver(value: Boolean) {
        dataSaver = value
        prefs.edit().putBoolean("data_saver", value).apply()
    }
    fun updatePip(value: Boolean) {
        enablePip = value
        prefs.edit().putBoolean("pip_enabled", value).apply()
    }
    fun updateVideoQuality(quality: Int) {
        require(quality in setOf(0, 480, 720, 1080))
        videoQuality = quality
        prefs.edit().putInt("video_quality", quality).apply()
    }

    fun hideRecommendation(anime: Anime) {
        hiddenRecommendations = hiddenRecommendations + anime.id
        prefs.edit().putStringSet(
            "hidden_recommendations", hiddenRecommendations.toSet()
        ).apply()
    }
    fun resetHiddenRecommendations() {
        hiddenRecommendations = emptySet()
        prefs.edit().remove("hidden_recommendations").apply()
    }
    fun unhideRecommendation(anime: Anime) {
        if (anime.id !in hiddenRecommendations) return
        hiddenRecommendations = hiddenRecommendations - anime.id
        prefs.edit().putStringSet(
            "hidden_recommendations", hiddenRecommendations.toSet()
        ).apply()
    }
    fun clearRecommendationGenres() {
        recommendationGenres = emptySet()
        prefs.edit().remove("recommendation_genres").apply()
    }
    fun updateRecommendationMode(mode: RecommendationMode) {
        recommendationMode = mode
        prefs.edit().putString("recommendation_mode", mode.name).apply()
    }
    fun toggleRecommendationGenre(genre: String) {
        val sanitized = genre.trim().take(75)
        if (sanitized.isBlank()) return
        recommendationGenres = if (sanitized in recommendationGenres)
            recommendationGenres - sanitized else recommendationGenres + sanitized
        prefs.edit().putStringSet(
            "recommendation_genres", recommendationGenres.toSet()
        ).apply()
    }
    fun recommendationCandidates(): List<Anime> = try {
        val data = prefs.getString("recommendation_candidates", "[]") ?: "[]"
        val array = JSONArray(data)
        (0 until array.length()).mapNotNull {
            array.optJSONObject(it)?.let(Anime::fromJson)
        }.filter { it.id.startsWith("aniliberty:") }
    } catch (_: Exception) { emptyList() }

    fun recommendationCacheFresh(): Boolean {
        val cachedAt = prefs.getLong("recommendation_cached_at", 0L)
        val elapsed = System.currentTimeMillis() - cachedAt
        return recommendationCandidates().isNotEmpty() && elapsed in 0..(30 * 60 * 1000L)
    }
    fun saveRecommendationCandidates(animes: List<Anime>) {
        val valid = animes.asSequence().filter { it.id.startsWith("aniliberty:") }
            .distinctBy { it.id }.take(250).toList()
        prefs.edit().putString(
            "recommendation_candidates",
            JSONArray(valid.map { it.toJson() }).toString()
        ).putLong("recommendation_cached_at", System.currentTimeMillis()).apply()
    }

    fun selectQuality(streams: List<StreamVariant>): StreamVariant? {
        val sorted = streams.sortedByDescending { it.popularity }
        if (videoQuality != 0) {
            return sorted.firstOrNull { it.popularity == videoQuality }
                ?: sorted.firstOrNull { it.popularity < videoQuality }
                ?: sorted.lastOrNull()
        }
        if (dataSaver) return sorted.firstOrNull { it.popularity <= 480 }
            ?: sorted.lastOrNull()
        val capabilities = try {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as ConnectivityManager
            manager.getNetworkCapabilities(manager.activeNetwork)
        } catch (_: Exception) { null }
        // Starting quality is conservative on mobile or low-bandwidth links.
        val downstream = capabilities?.linkDownstreamBandwidthKbps ?: 0
        val cellular = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val preferred = when {
            downstream in 1..2200 -> 480
            downstream in 2201..5000 || cellular -> 720
            else -> 1080
        }
        return sorted.firstOrNull { it.popularity <= preferred }
            ?: sorted.lastOrNull()
    }
    fun progressPercent(anime: Anime, episode: Int, durationMs: Long): Int {
        if (durationMs <= 0L) return 0
        return (episodePosition(anime, episode).toDouble() / durationMs * 100.0)
            .toInt().coerceIn(0, 100)
    }
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

    /** Last known AniLibria weekday schedule, retained for offline home screen. */
    fun cachedWeeklySchedule(): List<ScheduledAnime> = try {
        val raw = JSONArray(prefs.getString("weekly_schedule", "[]") ?: "[]")
        (0 until raw.length()).mapNotNull {
            raw.optJSONObject(it)?.let(ScheduledAnime::fromJson)
        }
    } catch (_: Exception) { emptyList() }

    fun weeklyScheduleCacheFresh(): Boolean {
        val saved = prefs.getLong("weekly_schedule_saved_at", 0L)
        val age = System.currentTimeMillis() - saved
        return age in 0L..(30L * 60_000L) && cachedWeeklySchedule().isNotEmpty()
    }

    fun saveWeeklySchedule(schedule: List<ScheduledAnime>) {
        val valid = schedule
            .filter { it.dayOfWeek in 1..7 && it.anime.id.startsWith("aniliberty:") }
            .distinctBy { it.dayOfWeek to it.anime.id }
        prefs.edit()
            .putString("weekly_schedule", JSONArray(valid.map { it.toJson() }).toString())
            .putLong("weekly_schedule_saved_at", System.currentTimeMillis())
            .apply()
    }

    fun saveResolvedStreams(anime: Anime, variants: List<StreamVariant>) {
        streams[anime.id] = variants.filter { it.source == "AniLiberty" }
    }
    fun variantsFor(anime: Anime): List<StreamVariant> =
        streams[anime.id].orEmpty().filter { it.source == "AniLiberty" }
}
