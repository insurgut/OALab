package ru.lisurgut.oalab

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

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
        fun parse(value: JSONObject) = LibraryEntry(
            anime = Anime.fromJson(value.getJSONObject("anime")),
            status = value.optString("status", "Не смотрел"),
            favorite = value.optBoolean("favorite"),
            lastEpisode = value.optInt("lastEpisode", 1),
            lastPosition = value.optLong("lastPosition", 0L)
        )
    }
}

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

data class SourceEntry(
    val anilistId: Int,
    val malId: Int,
    val title: String,
    val variants: List<StreamVariant>
)

data class SourceFeed(
    val url: String,
    val name: String,
    val entries: List<SourceEntry>
) {
    val count: Int get() = entries.size

    fun forAnime(anime: Anime): List<StreamVariant> =
        entries.filter {
            (it.anilistId > 0 && anime.anilistId > 0 && it.anilistId == anime.anilistId) ||
                (it.malId > 0 && anime.malId > 0 && it.malId == anime.malId) ||
                (it.title.isNotBlank() && it.title.equals(anime.title, ignoreCase = true))
        }.flatMap { it.variants }

    companion object {
        fun parse(url: String, raw: String): SourceFeed {
            if (raw.trimStart().startsWith("#EXTM3U")) return parseM3u(url,raw)
            val root = JSONObject(raw)
            val name = root.optString("name").ifBlank { URLName(url) }
            val items = root.optJSONArray("entries")
                ?: error("Нет массива entries: проверьте формат фида")
            val entries = mutableListOf<SourceEntry>()
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val variants = mutableListOf<StreamVariant>()
                val eps = item.optJSONArray("episodes") ?: JSONArray()
                for (e in 0 until eps.length()) {
                    val episode = eps.getJSONObject(e)
                    val number = episode.optInt("number", e + 1)
                    val streams = episode.optJSONArray("streams") ?: JSONArray()
                    for (s in 0 until streams.length()) {
                        val stream = streams.getJSONObject(s)
                        val streamUrl = stream.optString("url")
                        if (!streamUrl.startsWith("https://")) continue
                        variants.add(
                            StreamVariant(
                                source = name,
                                episode = number,
                                language = stream.optString("language", "ja"),
                                team = stream.optString("team", "Оригинал"),
                                voices = stream.optInt("voices", 1).coerceAtLeast(0),
                                votes = stream.optInt("votes", 0).coerceAtLeast(0),
                                popularity = stream.optInt("popularity", 0).coerceAtLeast(0),
                                timing = stream.optString("timing", "не указано"),
                                url = streamUrl,
                                subtitle = stream.optString("subtitle")
                                    .takeIf { it.startsWith("https://") } ?: ""
                            )
                        )
                    }
                }
                entries.add(
                    SourceEntry(
                        anilistId = item.optInt("anilistId"),
                        malId = item.optInt("malId"),
                        title = item.optString("title"),
                        variants = variants
                    )
                )
            }
            return SourceFeed(url, name, entries)
        }
        private fun parseM3u(url: String, raw: String): SourceFeed {
            val feedName = URLName(url)
            val aggregate = linkedMapOf<String, MutableList<StreamVariant>>()
            val info = linkedMapOf<String, Triple<Int,Int,String>>()
            val lines = raw.lines()
            for (i in lines.indices) {
                val line = lines[i].trim()
                if (!line.startsWith("#EXTINF:")) continue
                val streamUrl = lines.drop(i+1).firstOrNull {
                    it.isNotBlank() && !it.trim().startsWith("#")
                }?.trim() ?: continue
                if(!streamUrl.startsWith("https://")) continue
                val attrs = mutableMapOf<String,String>()
                val attributePattern = Regex("([a-zA-Z-]+)=\\\"([^\\\"]*)\\\"")
                attributePattern.findAll(line).forEach { attrs[it.groupValues[1]] = it.groupValues[2] }
                val title = attrs["anime"] ?: attrs["group-title"] ?: line.substringAfterLast(",","")
                if(title.isBlank()) continue
                val mal = attrs["mal-id"]?.toIntOrNull() ?: 0
                val ani = attrs["anilist-id"]?.toIntOrNull() ?: 0
                val episode = attrs["episode"]?.toIntOrNull() ?: 1
                val key = (if(ani>0)"a:"+ani else if(mal>0)"m:"+mal else "t:"+title.lowercase())
                aggregate.getOrPut(key){ mutableListOf() }.add(StreamVariant(
                    source=feedName,episode=episode,language=attrs["language"] ?: "ja",
                    team=attrs["team"] ?: "Оригинал",
                    voices=attrs["voices"]?.toIntOrNull() ?: 1,
                    votes=attrs["votes"]?.toIntOrNull() ?: 0,
                    popularity=attrs["popularity"]?.toIntOrNull() ?: 0,
                    timing=attrs["timing"] ?: "не указано",
                    url=streamUrl,subtitle=attrs["subtitle"]?.takeIf{it.startsWith("https://")} ?: ""
                ))
                info[key] = Triple(ani,mal,title)
            }
            val entries = aggregate.map { (key, variants) ->
                val (ani,mal,title) = info.getValue(key)
                SourceEntry(ani,mal,title,variants)
            }
            return SourceFeed(url,feedName,entries)
        }

        private fun URLName(url: String): String =
            try { java.net.URL(url).host } catch (_: Exception) { "Мой источник" }
    }
}

class LocalLibrary(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("oalab_preferences", Context.MODE_PRIVATE)

    val entries = mutableStateListOf<LibraryEntry>()
    val feeds = mutableStateListOf<SourceFeed>()
    var language by mutableStateOf(prefs.getString("language", "ru") ?: "ru")
        private set
    var catalogProvider by mutableStateOf(prefs.getString("catalog", "Авто") ?: "Авто")
        private set
    var voiceSort by mutableStateOf(prefs.getString("voice_sort", "Популярность") ?: "Популярность")
        private set
    var autoFallback by mutableStateOf(prefs.getBoolean("auto_fallback", true))
        private set
    var autoplay by mutableStateOf(prefs.getBoolean("autoplay", false))
        private set
    var seekSeconds by mutableStateOf(prefs.getInt("seek", 10))
        private set

    init {
        try {
            val values = JSONArray(prefs.getString("library", "[]"))
            for (i in 0 until values.length()) entries.add(LibraryEntry.parse(values.getJSONObject(i)))
        } catch (_: Exception) {
            // Corrupt local entries must not prevent the application from starting.
        }
        try {
            val urls = JSONArray(prefs.getString("feeds", "[]"))
            for (i in 0 until urls.length()) {
                val url = urls.optString(i)
                val file = feedFile(url)
                if (file.isFile) feeds.add(SourceFeed.parse(url, file.readText()))
            }
        } catch (_: Exception) {
            // Bad feeds may be re-imported from the Sources tab.
        }
    }

    private fun same(a: Anime, b: Anime) =
        a.id == b.id || (a.malId != 0 && a.malId == b.malId) ||
            (a.anilistId != 0 && a.anilistId == b.anilistId)

    fun entry(anime: Anime): LibraryEntry? = entries.firstOrNull { same(it.anime, anime) }
    fun isFavorite(anime: Anime): Boolean = entry(anime)?.favorite ?: false
    fun status(anime: Anime): String = entry(anime)?.status ?: "Не смотрел"
    fun favorites(): List<Anime> = entries.filter { it.favorite }.map { it.anime }
    fun inProgress(): List<LibraryEntry> = entries.filter { it.status == "Смотрю" }
    fun watched(): List<Anime> = entries.filter { it.status == "Просмотрено" }.map { it.anime }

    private fun change(anime: Anime, edit: (LibraryEntry) -> LibraryEntry) {
        val index = entries.indexOfFirst { same(it.anime, anime) }
        val old = if (index >= 0) entries[index] else LibraryEntry(anime)
        val edited = edit(old)
        if (index >= 0) entries[index] = edited else entries.add(edited)
        prefs.edit().putString("library", JSONArray(entries.map { it.toJson() }).toString()).apply()
    }

    fun toggleFavorite(anime: Anime) =
        change(anime) { it.copy(anime = anime, favorite = !it.favorite) }

    fun setStatus(anime: Anime, status: String) =
        change(anime) { it.copy(anime = anime, status = status) }

    fun rememberPosition(anime: Anime, episode: Int, position: Long) {
        val previous = entry(anime)
        if (previous?.lastEpisode == episode && kotlin.math.abs(previous.lastPosition - position) < 5_000L)
            return
        change(anime) {
            it.copy(anime = anime, status = if (it.status == "Не смотрел") "Смотрю" else it.status,
                lastEpisode = episode, lastPosition = position)
        }
    }

    fun preferredGenres(): List<String> =
        entries.filter { it.favorite || it.status == "Просмотрено" || it.status == "Смотрю" }
            .flatMap { it.anime.genres }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.map { it.key }

    fun updateLanguage(value: String) { language = value; prefs.edit().putString("language", value).apply() }
    fun setProvider(value: String) { catalogProvider = value; prefs.edit().putString("catalog", value).apply() }
    fun updateVoiceSort(value: String) { voiceSort = value; prefs.edit().putString("voice_sort", value).apply() }
    fun updateAutoFallback(value: Boolean) {
        autoFallback = value
        prefs.edit().putBoolean("auto_fallback",value).apply()
    }
    fun updateAutoplay(value: Boolean) { autoplay = value; prefs.edit().putBoolean("autoplay", value).apply() }
    fun setSeek(value: Int) { seekSeconds = value; prefs.edit().putInt("seek", value).apply() }

    fun saveCache(list: List<Anime>) {
        prefs.edit().putString("catalog_cache", JSONArray(list.map { it.toJson() }).toString()).apply()
    }
    fun cachedCatalog(): List<Anime> = try {
        val values = JSONArray(prefs.getString("catalog_cache", "[]"))
        (0 until values.length()).map { Anime.fromJson(values.getJSONObject(it)) }
    } catch (_: Exception) { emptyList() }

    private fun feedFile(url: String) = File(
        context.filesDir, "feed_" + url.hashCode().toUInt().toString(16) + ".json"
    )

    fun putFeed(url: String, raw: String): SourceFeed {
        val feed = SourceFeed.parse(url, raw)
        // Parsing before saving protects existing feeds from bad updates.
        feedFile(url).writeText(raw)
        val index = feeds.indexOfFirst { it.url == url }
        if (index >= 0) feeds[index] = feed else feeds.add(feed)
        persistFeeds()
        return feed
    }

    fun removeFeed(feed: SourceFeed) {
        feeds.removeAll { it.url == feed.url }
        feedFile(feed.url).delete()
        persistFeeds()
    }

    private fun persistFeeds() {
        prefs.edit().putString("feeds", JSONArray(feeds.map { it.url }).toString()).apply()
    }

    fun variantsFor(anime: Anime): List<StreamVariant> =
        feeds.flatMap { it.forAnime(anime) }.distinctBy { it.episode.toString() + "|" + it.url }

    fun rankVariants(streams: List<StreamVariant>, order: String): List<StreamVariant> {
        val comparator = when (order) {
            "Голоса" -> compareByDescending<StreamVariant> { it.voices }
            "Оценки" -> compareByDescending<StreamVariant> { it.votes }
            "Тайминги" -> compareByDescending<StreamVariant> { it.timing != "не указано" }
                .thenByDescending { it.votes }
            "По алфавиту" -> compareBy<StreamVariant> { it.team.lowercase() }
            else -> compareByDescending<StreamVariant> { it.popularity }
                .thenByDescending { it.votes }
        }
        return streams.sortedWith(
            compareByDescending<StreamVariant> { it.language == language }.then(comparator)
        )
    }
}
