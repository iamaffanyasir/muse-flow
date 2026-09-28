package com.muse.player.online

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.muse.player.Graph
import com.muse.player.MainActivity
import com.muse.player.data.AppSettings
import com.muse.player.data.DlFormat
import com.muse.player.data.Track
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class DlState { QUEUED, RUNNING, DONE, FAILED, CANCELED }

data class DlTask(
    val song: Song,
    val state: DlState,
    val progress: Float = 0f,
    val etaSec: Long = 0L,
    val message: String = "",
    val added: Long = System.currentTimeMillis(),
) {
    val key get() = song.id
    val active get() = state == DlState.QUEUED || state == DlState.RUNNING
}

/**
 * Download queue. Each job runs yt-dlp into a private temp folder with the user's format settings,
 * then publishes the finished file to Music/Muse Flow through MediaStore so every player sees it.
 */
object Downloader {
    const val FOLDER = "Muse Flow"
    private val AUDIO_EXTS = setOf("mp3", "m4a", "opus", "ogg", "webm", "mka", "aac", "flac", "wav")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var appContext: Context

    private val _tasks = MutableStateFlow<List<DlTask>>(emptyList())
    val tasks = _tasks.asStateFlow()

    /** Bumped after each finished file so the library rescans. */
    private val _completed = MutableStateFlow(0)
    val completed = _completed.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun stateOf(songId: String): DlTask? = _tasks.value.find { it.key == songId }

    /** [queued] songs started downloading; [inLibrary] were already on the phone and got linked instead. */
    data class EnqueueResult(val queued: Int, val inLibrary: Int, val alreadyQueued: Int)

    /** Queues every song that isn't already downloaded, in the library, or in the queue. */
    fun enqueue(songs: List<Song>): EnqueueResult {
        var inLibrary = 0
        val candidates = songs.filter { s ->
            if (s.url == null || Playlists.localUriFor(s.id) != null) return@filter false
            val match = libraryMatch(s)
            if (match != null) {
                // Same song already on the phone (maybe from a different link) — reuse that file.
                Playlists.markDownloaded(s.id, match.uri.toString())
                inLibrary++
                false
            } else true
        }
        var added = 0
        _tasks.update { list ->
            val active = list.filter { it.active }
            val busyIds = active.map { it.key }.toHashSet()
            val busyKeys = active.map { matchKey(it.song.title, it.song.artist) }.toHashSet()
            val fresh = candidates.filter { s ->
                s.id !in busyIds && busyKeys.add(matchKey(s.title, s.artist)) && busyIds.add(s.id)
            }
            added = fresh.size
            list.filterNot { t -> fresh.any { it.id == t.key } } + fresh.map { DlTask(it, DlState.QUEUED) }
        }
        if (added > 0) {
            startService()
            pump()
        }
        return EnqueueResult(added, inLibrary, candidates.size - added)
    }

    // ------------------------------------------------------------ library duplicate check

    private val bracketed = Regex("\\s*[(\\[][^)\\]]*[)\\]]")
    private val featuring = Regex("\\s+(feat\\.?|ft\\.?|featuring)\\s.*$", RegexOption.IGNORE_CASE)
    private val nonWord = Regex("[^\\p{L}\\p{N}]+")

    private fun norm(s: String) = s.lowercase().replace(bracketed, " ").replace(featuring, " ").replace(nonWord, " ").trim()
    private fun matchKey(title: String, artist: String) = norm(title) + "|" + norm(artist)

    private fun artistsMatch(a: String, b: String): Boolean {
        val x = norm(a)
        val y = norm(b)
        if (x.isEmpty() || y.isEmpty() || x == "unknown artist" || y == "unknown artist") return true
        if (x == y || x.contains(y) || y.contains(x)) return true
        val tx = x.split(' ').filter { it.length > 1 }.toSet()
        return tx.isNotEmpty() && y.split(' ').any { it in tx }
    }

    @Volatile private var index: Pair<List<Track>, Map<String, List<Track>>>? = null

    /** A track already on the phone that is the same song (title + artist, similar length). */
    fun libraryMatch(s: Song): Track? {
        val tracks = Graph.library.tracks.value
        val idx = index?.takeIf { it.first === tracks }?.second
            ?: tracks.groupBy { norm(it.title) }.also { index = tracks to it }
        val candidates = idx[norm(s.title)] ?: return null
        return candidates.firstOrNull { t ->
            artistsMatch(t.artist, s.artist) &&
                (s.durationMs <= 0 || t.durationMs <= 0 || kotlin.math.abs(t.durationMs - s.durationMs) <= 8_000)
        }
    }

    fun cancel(songId: String) {
        val t = stateOf(songId) ?: return
        if (t.state == DlState.RUNNING) runCatching { YoutubeDL.getInstance().destroyProcessById(songId) }
        set(songId) { it.copy(state = DlState.CANCELED, message = "Canceled") }
        pump()
    }

    fun retry(songId: String) {
        val t = stateOf(songId) ?: return
        if (t.state == DlState.RUNNING) return
        set(songId) { it.copy(state = DlState.QUEUED, progress = 0f, message = "") }
        startService()
        pump()
    }

    fun clearFinished() = _tasks.update { list -> list.filter { it.active } }

    private fun set(key: String, block: (DlTask) -> DlTask) =
        _tasks.update { list -> list.map { if (it.key == key) block(it) else it } }

    @Synchronized
    private fun pump() {
        val parallel = Graph.current.parallel.coerceIn(1, 3)
        val list = _tasks.value
        var running = list.count { it.state == DlState.RUNNING }
        for (t in list) {
            if (running >= parallel) break
            if (t.state != DlState.QUEUED) continue
            set(t.key) { it.copy(state = DlState.RUNNING, progress = 0f, message = "Starting…") }
            running++
            scope.launch { run(t.song, Graph.current) }
        }
    }

    private suspend fun run(song: Song, s: AppSettings) {
        val tmp = File(appContext.cacheDir, "dl/" + song.id.replace(Regex("[^A-Za-z0-9_-]"), "_"))
        try {
            if (!Ytdl.awaitReady()) error("Downloader isn't available")
            tmp.deleteRecursively()
            tmp.mkdirs()
            var file = try {
                execute(song, s, tmp, embedThumb = s.embedMeta)
            } catch (e: YoutubeDL.CanceledException) {
                throw e
            } catch (e: Exception) {
                // Post-processing (usually cover embedding) can fail after the audio is already done.
                findAudio(tmp, s.dlFormat) ?: if (s.embedMeta) {
                    tmp.listFiles()?.forEach { it.delete() }
                    execute(song, s, tmp, embedThumb = false)
                } else throw e
            }
            if (file == null) file = findAudio(tmp, s.dlFormat)
            file ?: error("yt-dlp finished but produced no audio file")
            val uri = publish(file, song)
            Playlists.markDownloaded(song.id, uri.toString())
            set(song.id) { it.copy(state = DlState.DONE, progress = 1f, message = file.extension.uppercase()) }
            _completed.update { it + 1 }
        } catch (_: YoutubeDL.CanceledException) {
            set(song.id) { it.copy(state = DlState.CANCELED, message = "Canceled") }
        } catch (e: Throwable) {
            if (stateOf(song.id)?.state != DlState.CANCELED) {
                set(song.id) { it.copy(state = DlState.FAILED, message = friendlyError(e)) }
            }
        } finally {
            tmp.deleteRecursively()
            pump()
        }
    }

    private fun execute(song: Song, s: AppSettings, dir: File, embedThumb: Boolean): File? {
        val req = YoutubeDLRequest(song.url!!).apply {
            addOption("--no-playlist")
            addOption("--no-mtime")
            addOption("--no-warnings")
            addOption("--socket-timeout", "20")
            addOption("-R", "3")
            addOption("-P", dir.absolutePath)
            addOption("-o", "audio.%(ext)s")
            addOption("-x")
            when (s.dlFormat) {
                DlFormat.MP3 -> {
                    addOption("-f", "bestaudio/best")
                    addOption("--audio-format", "mp3")
                    addOption("--audio-quality", "${s.mp3Kbps}K")
                }
                DlFormat.M4A -> {
                    addOption("-f", "bestaudio[ext=m4a]/bestaudio/best")
                    addOption("--audio-format", "m4a")
                    addOption("--audio-quality", "0")
                }
                DlFormat.OPUS -> {
                    addOption("-f", "bestaudio[acodec=opus]/bestaudio/best")
                    addOption("--audio-format", "opus")
                    addOption("--audio-quality", "0")
                }
            }
            if (s.embedMeta) {
                addOption("--embed-metadata")
                addOption("--parse-metadata", "%(album,playlist_title,title)s:%(meta_album)s")
                // Prefer the clean names we already have (YouTube Music gives real artist/title).
                literal(song.artist)?.let { addOption("--parse-metadata", "$it:%(meta_artist)s") }
                literal(song.title)?.let { addOption("--parse-metadata", "$it:%(meta_title)s") }
            }
            if (embedThumb) {
                addOption("--embed-thumbnail")
                addOption("--convert-thumbnails", "jpg")
                if (s.cropArt) {
                    addOption("--ppa", "ThumbnailsConvertor+FFmpeg_o:-c:v mjpeg -vf crop=\"'if(gt(ih,iw),iw,ih)':'if(gt(iw,ih),ih,iw)'\"")
                }
            }
        }
        YoutubeDL.getInstance().execute(req, song.id) { progress, eta, line ->
            set(song.id) {
                it.copy(
                    progress = (progress / 100f).coerceIn(0f, 1f),
                    etaSec = eta,
                    message = when {
                        "ExtractAudio" in line || "Destination" in line && progress >= 100f -> "Converting…"
                        "EmbedThumbnail" in line || "Metadata" in line -> "Tagging…"
                        progress > 0f -> "Downloading"
                        else -> "Starting…"
                    },
                )
            }
        }
        return findAudio(dir, s.dlFormat)
    }

    /** yt-dlp treats "%(…)" in literal parse-metadata text as a template; escape it. */
    private fun literal(s: String): String? {
        if (s.isBlank() || ':' in s) return null
        // yt-dlp reads a bare word like "Believer" as a field name (→ "NA"), so wrap it in a
        // template that always prints the word.
        if (Regex("[a-zA-Z_]+").matches(s)) return "%(id&$s)s"
        return s.replace("%", "%%")
    }

    private fun findAudio(dir: File, fmt: DlFormat): File? {
        val files = dir.listFiles()?.filter { it.isFile && it.extension.lowercase() in AUDIO_EXTS && it.length() > 0 }.orEmpty()
        return files.firstOrNull { it.extension.equals(fmt.ext, true) } ?: files.maxByOrNull { it.length() }
    }

    private fun publish(file: File, song: Song): Uri {
        val ext = file.extension.lowercase()
        val base = "${song.artist} - ${song.title}".replace(Regex("[\\\\/:*?\"<>|]"), "_").take(150).trim()
        val name = "$base.$ext"
        val mime = when (ext) {
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "opus", "ogg" -> "audio/ogg"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "audio/*"
        }
        val resolver = appContext.contentResolver
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, name)
                put(MediaStore.Audio.Media.MIME_TYPE, mime)
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/$FOLDER")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
                ?: error("Couldn't create the file in Music/$FOLDER")
            try {
                resolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
            return uri
        }
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), FOLDER).apply { mkdirs() }
        val dest = File(dir, name)
        file.copyTo(dest, overwrite = true)
        MediaScannerConnection.scanFile(appContext, arrayOf(dest.absolutePath), arrayOf(mime), null)
        return Uri.fromFile(dest)
    }

    private fun friendlyError(e: Throwable): String {
        val msg = e.message.orEmpty()
        val line = msg.lines().lastOrNull { it.contains("ERROR") } ?: msg.lines().firstOrNull { it.isNotBlank() } ?: "Download failed"
        return when {
            "Sign in to confirm" in msg -> "YouTube blocked this request — try again later"
            "Video unavailable" in msg -> "This song isn't available"
            "Unable to resolve host" in msg || "getaddrinfo" in msg -> "No internet connection"
            else -> line.removePrefix("ERROR:").trim().take(140)
        }
    }

    private fun startService() {
        val intent = Intent(appContext, DownloadService::class.java)
        runCatching { ContextCompat.startForegroundService(appContext, intent) }
    }
}

/** Keeps the process alive while the queue runs and shows progress in the notification shade. */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val channel = "downloads"
    private val id = 4101

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(channel, "Downloads", NotificationManager.IMPORTANCE_LOW))
        scope.launch {
            Downloader.tasks.collect { list ->
                if (list.none { it.active }) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    val done = list.count { it.state == DlState.DONE }
                    if (done > 0) nm.notify(id + 1, simple("Downloads finished", "$done songs saved to Music/${Downloader.FOLDER}"))
                    stopSelf()
                } else {
                    nm.notify(id, progress(list))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = progress(Downloader.tasks.value)
        if (Build.VERSION.SDK_INT >= 29) startForeground(id, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(id, n)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun openApp() = PendingIntent.getActivity(
        this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
    )

    private fun progress(list: List<DlTask>): Notification {
        val active = list.filter { it.active }
        val running = active.filter { it.state == DlState.RUNNING }
        val first = running.firstOrNull()
        return NotificationCompat.Builder(this, channel)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(if (active.size > 1) "Downloading ${active.size} songs" else "Downloading")
            .setContentText(first?.let { "${it.song.title} · ${(it.progress * 100).toInt()}%" } ?: "Waiting…")
            .setProgress(100, ((first?.progress ?: 0f) * 100).toInt(), first == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp())
            .build()
    }

    private fun simple(title: String, text: String) = NotificationCompat.Builder(this, channel)
        .setSmallIcon(android.R.drawable.stat_sys_download_done)
        .setContentTitle(title)
        .setContentText(text)
        .setAutoCancel(true)
        .setContentIntent(openApp())
        .build()
}
