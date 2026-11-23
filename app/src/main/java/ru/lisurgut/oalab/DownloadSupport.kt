package ru.lisurgut.oalab

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class DownloadEntry(
    val id: Long,
    val animeId: String,
    val title: String,
    val episode: Int,
    val file: String,
    val language: String,
    val team: String
)

object DownloadSupport {
    private const val KEY = "downloads"

    private fun prefs(context: Context) =
        context.getSharedPreferences("oalab_downloads",Context.MODE_PRIVATE)

    fun list(context: Context): List<DownloadEntry> {
        return try {
            val array = JSONArray(prefs(context).getString(KEY,"[]"))
            (0 until array.length()).map { index ->
                val j = array.getJSONObject(index)
                DownloadEntry(
                    id = j.getLong("id"), animeId = j.getString("animeId"),
                    title = j.getString("title"), episode = j.getInt("episode"),
                    file = j.getString("file"), language = j.optString("language","ja"),
                    team = j.optString("team","Оригинал")
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun persist(context: Context, entries: List<DownloadEntry>) {
        prefs(context).edit().putString(KEY,JSONArray(entries.map {
            JSONObject().put("id",it.id).put("animeId",it.animeId)
                .put("title",it.title).put("episode",it.episode)
                .put("file",it.file).put("language",it.language).put("team",it.team)
        }).toString()).apply()
    }

    fun canDownload(stream: StreamVariant): Boolean =
        stream.url.startsWith("https://") &&
            (Uri.parse(stream.url).path?.endsWith(".mp4",ignoreCase=true) == true)

    fun enqueue(context: Context, anime: Anime, stream: StreamVariant): Long {
        require(canDownload(stream)) { "Загрузка доступна только для прямых HTTPS MP4" }
        val safeName = anime.title.replace(Regex("[^a-zA-Zа-яА-Я0-9._-]+"),"_").take(50)
            .ifBlank { "Anime" }
        val filename = safeName + "-ep" + stream.episode +
            "-" + System.currentTimeMillis() + ".mp4"
        val request = DownloadManager.Request(Uri.parse(stream.url)).apply {
            setTitle(anime.title + " — серия " + stream.episode)
            setDescription(stream.team)
            setMimeType("video/mp4")
            setAllowedOverMetered(true)
            setAllowedOverRoaming(false)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(context,Environment.DIRECTORY_MOVIES,filename)
        }
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val id = manager.enqueue(request)
        val item = DownloadEntry(
            id, anime.id, anime.title, stream.episode, filename, stream.language, stream.team
        )
        persist(context,list(context) + item)
        return id
    }

    fun status(context: Context, id: Long): Int {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return manager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
            if(cursor.moveToFirst())
                cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            else -1
        } ?: -1
    }

    fun offlineFor(context: Context, anime: Anime): List<StreamVariant> =
        list(context).filter {
            it.animeId == anime.id && status(context,it.id) == DownloadManager.STATUS_SUCCESSFUL
        }.mapNotNull { entry ->
            val base = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: return@mapNotNull null
            val file = File(base,entry.file)
            if(!file.exists()) return@mapNotNull null
            StreamVariant(
                source = "Скачано · офлайн",
                episode = entry.episode,
                language = entry.language,
                team = entry.team,
                voices = 1, votes = 0, popularity = 999,
                timing = "Локально",
                url = Uri.fromFile(file).toString(), subtitle = ""
            )
        }

    fun remove(context: Context, entry: DownloadEntry) {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.remove(entry.id)
        persist(context,list(context).filter { it.id != entry.id })
    }
}
