package ru.lisurgut.oalab

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

data class ChangeLine(val kind: Char, val message: String)
data class UpdateInfo(
    val version: String,
    val apkUrl: String,
    val lines: List<ChangeLine>
)
data class UpdateScan(
    val update: UpdateInfo? = null,
    val error: String? = null,
    val checked: Boolean = false
)

object OAlabUpdates {
    private const val RELEASES = "https://api.github.com/repos/insurgut/OALab/releases?per_page=15"
    val changes = listOf('+', '-', '*')

    fun parseChangelog(raw: String): List<ChangeLine> = raw.lines()
        .map { it.trim() }
        .filter { it.length > 1 && it[0] in changes }
        .map { ChangeLine(it[0], it.substring(1).trim().trimStart(':').trim()) }
        .filter { it.message.isNotBlank() }

    private fun semver(version: String): List<Int> =
        version.removePrefix("v").substringBefore("-").split(".")
            .map { it.toIntOrNull() ?: 0 }

    private fun newer(remote: String, installed: String): Boolean {
        val a = semver(remote)
        val b = semver(installed)
        for (index in 0..3) {
            val x = a.getOrElse(index) { 0 }
            val y = b.getOrElse(index) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun getText(uri: String): String {
        require(uri.startsWith("https://")) { "Требуется HTTPS" }
        val connection = URL(uri).openConnection() as HttpURLConnection
        connection.connectTimeout = 12_000
        connection.readTimeout = 12_000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "OAlab-Android-Updater")
        try {
            val status = connection.responseCode
            if (status == 404) error("GitHub-репозиторий закрыт для публичной проверки обновлений.")
            if (status !in 200..299) error("GitHub HTTP " + status)
            return connection.inputStream.bufferedReader().use { reader ->
                val out = StringBuilder()
                val buffer = CharArray(4096)
                while (true) {
                    val length = reader.read(buffer)
                    if (length < 0) break
                    out.append(buffer, 0, length)
                    if (out.length > 512_000) error("Слишком большой ответ")
                }
                out.toString()
            }
        } finally { connection.disconnect() }
    }

    suspend fun check(context: Context): UpdateScan = withContext(Dispatchers.IO) {
        try {
            val installed = context.packageManager.getPackageInfo(context.packageName, 0)
                .versionName ?: "0.0.0"
            val versions = JSONArray(getText(RELEASES))
            for (index in 0 until versions.length()) {
                val item = versions.getJSONObject(index)
                if (item.optBoolean("draft")) continue
                val tag = item.optString("tag_name")
                if (!newer(tag, installed)) continue
                val assets = item.optJSONArray("assets") ?: JSONArray()
                var apk: String? = null
                var notes: String? = null
                for (a in 0 until assets.length()) {
                    val asset = assets.getJSONObject(a)
                    val url = asset.optString("browser_download_url")
                    if (!url.startsWith("https://github.com/insurgut/OALab/releases/download/")) continue
                    val name = asset.optString("name")
                    if (name.endsWith(".apk", ignoreCase = true)) apk = url
                    if (name == "lupd.txt") notes = url
                }
                if (apk == null) continue
                val changelog = if (notes == null) item.optString("body") else
                    try { getText(notes) } catch (_: Exception) { item.optString("body") }
                return@withContext UpdateScan(
                    update = UpdateInfo(tag.removePrefix("v"), apk, parseChangelog(changelog)),
                    checked = true
                )
            }
            UpdateScan(checked = true)
        } catch (e: Exception) {
            UpdateScan(error = e.localizedMessage ?: "Ошибка проверки обновлений", checked = true)
        }
    }

    fun download(context: Context, update: UpdateInfo): Long {
        require(update.apkUrl.startsWith(
            "https://github.com/insurgut/OALab/releases/download/"
        )) { "Недоверенный адрес загрузки" }
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val file = "OAlab-" + update.version.replace(Regex("[^0-9A-Za-z.-]"), "") + ".apk"
        return manager.enqueue(DownloadManager.Request(Uri.parse(update.apkUrl)).apply {
            setTitle("OAlab " + update.version)
            setDescription("Обновление OAlab")
            setMimeType("application/vnd.android.package-archive")
            setAllowedOverMetered(true)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, file)
        })
    }

    fun downloadStatus(context: Context, id: Long): Int {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return manager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
            if (cursor.moveToFirst())
                cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            else -1
        } ?: -1
    }

    fun install(context: Context, id: Long) {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = manager.getUriForDownloadedFile(id) ?: error("APK не найден")
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }
}

@Composable
fun UpdateBubble(onClick: () -> Unit) {
    SmallFloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = CircleShape,
        modifier = Modifier.size(52.dp)
    ) { Icon(Icons.Rounded.Update, contentDescription = "Доступно обновление") }
}

@Composable
fun UpdatesScreen(
    scan: UpdateScan,
    onBack: () -> Unit,
    onRescan: () -> Unit
) {
    val context = LocalContext.current
    var downloadId by remember { mutableLongStateOf(-1L) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var installing by remember { mutableStateOf(false) }
    var downloaded by remember { mutableStateOf(false) }
    var percent by remember { mutableIntStateOf(0) }

    LaunchedEffect(downloadId) {
        if (downloadId < 0) return@LaunchedEffect
        while (true) {
            val status = OAlabUpdates.downloadStatus(context, downloadId)
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                downloaded = true
                try {
                    OAlabUpdates.install(context, downloadId)
                    installing = true
                } catch (e: Exception) {
                    downloadError = "Не удалось открыть установщик: " + e.localizedMessage
                }
                return@LaunchedEffect
            }
            if (status == DownloadManager.STATUS_FAILED || status == -1) {
                downloadError = "APK не удалось скачать"
                return@LaunchedEffect
            }
            delay(1_000)
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().background(Ink).statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 19.dp, vertical = 15.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, "Назад", tint = Paper)
                }
                Spacer(Modifier.width(5.dp))
                Text("Обновления", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(24.dp))
        }
        if (scan.update != null) {
            val release = scan.update
            item {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                        .background(Panel).padding(19.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Icon(Icons.Rounded.AutoAwesome, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(33.dp))
                        Text("Версия " + release.version, fontSize = 23.sp,
                            fontWeight = FontWeight.Bold)
                        Text("Доступно обновление приложения",
                            color = Muted, fontSize = 13.sp)
                        Button(
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            enabled = downloadId < 0 && !installing,
                            shape = RoundedCornerShape(15.dp),
                            onClick = {
                                try {
                                    downloadId = OAlabUpdates.download(context, release)
                                } catch (e: Exception) {
                                    downloadError = e.localizedMessage
                                }
                            }
                        ) {
                            Icon(Icons.Rounded.Download, null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when {
                                    installing -> "Открылся установщик"
                                    downloaded -> "APK загружен"
                                    downloadId >= 0 -> "Загрузка обновления…"
                                    else -> "Скачать и установить"
                                }
                            )
                        }
                        Text(
                            "Android попросит подтвердить установку. Для обновления поверх старой " +
                                "версии APK должны быть подписаны одним ключом.",
                            color = Muted, fontSize = 11.sp, lineHeight = 16.sp
                        )
                        if (downloadError != null) Text(downloadError!!,
                            color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text("Что изменилось", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                if (release.lines.isEmpty()) Text("Для этой версии изменений не указано",
                    color = Muted, fontSize = 13.sp)
                else release.lines.forEach { line ->
                    val color = when (line.kind) {
                        '+' -> MaterialTheme.colorScheme.primary
                        '-' -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.tertiary
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(line.kind.toString(),
                            modifier = Modifier.width(25.dp),
                            color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(line.message, modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 13.sp, lineHeight = 20.sp)
                    }
                }
            }
        } else {
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(21.dp))
                        .background(Panel).padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    Icon(
                        if (scan.error != null) Icons.Rounded.ErrorOutline
                        else Icons.Rounded.CheckCircle,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        when {
                            !scan.checked -> "Проверяем обновления"
                            scan.error != null -> "Проверка недоступна"
                            else -> "Установлена актуальная версия"
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                    if (scan.error != null)
                        Text(scan.error, fontSize = 12.sp, color = Muted)
                    OutlinedButton(onClick = onRescan) { Text("Проверить ещё раз") }
                }
            }
        }
    }
}
