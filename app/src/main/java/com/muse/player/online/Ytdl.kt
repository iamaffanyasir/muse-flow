package com.muse.player.online

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** Wrapper around the embedded yt-dlp + ffmpeg (same engine Seal uses). */
object Ytdl {
    sealed interface LinkInfo {
        data class Single(val song: Song) : LinkInfo
        data class Collection(val title: String, val songs: List<Song>) : LinkInfo
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ready = CompletableDeferred<Boolean>()
    private lateinit var appContext: Context

    private val _status = MutableStateFlow("Setting up downloader…")
    val status = _status.asStateFlow()
    private val _version = MutableStateFlow<String?>(null)
    val version = _version.asStateFlow()

    private val streamCache = ConcurrentHashMap<String, Pair<String, Long>>()
    private const val STREAM_TTL = 4 * 60 * 60 * 1000L
    private const val UPDATE_EVERY = 3 * 24 * 60 * 60 * 1000L

    fun init(context: Context) {
        appContext = context.applicationContext
        scope.launch {
            val ok = runCatching {
                YoutubeDL.getInstance().init(appContext)
                FFmpeg.getInstance().init(appContext)
            }.isSuccess
            _version.value = runCatching { YoutubeDL.getInstance().version(appContext) }.getOrNull()
            _status.value = if (ok) "Ready" else "Downloader failed to start"
            isReady = ok
            ready.complete(ok)
            if (ok) autoUpdate()
        }
    }

    suspend fun awaitReady(): Boolean = ready.await()
    @Volatile var isReady = false
        private set

    /** YouTube changes often; keep yt-dlp fresh in the background. */
    private fun autoUpdate() {
        val prefs = appContext.getSharedPreferences("ytdl", Context.MODE_PRIVATE)
        val last = prefs.getLong("last_update", 0L)
        if (System.currentTimeMillis() - last < UPDATE_EVERY) return
        scope.launch {
            if (runCatching { update() }.isSuccess) prefs.edit().putLong("last_update", System.currentTimeMillis()).apply()
        }
    }

    suspend fun update(): String = withContext(Dispatchers.IO) {
        if (!awaitReady()) error("Downloader isn't available")
        _status.value = "Updating yt-dlp…"
        try {
            val result = YoutubeDL.getInstance().updateYoutubeDL(appContext, YoutubeDL.UpdateChannel.STABLE)
            _version.value = runCatching { YoutubeDL.getInstance().version(appContext) }.getOrNull()
            if (result == YoutubeDL.UpdateStatus.DONE) "Updated to ${_version.value}" else "Already up to date"
        } finally {
            _status.value = "Ready"
        }
    }

    // ------------------------------------------------------------ link info

    suspend fun fetchInfo(url: String): LinkInfo = withContext(Dispatchers.IO) {
        if (!awaitReady()) error("Downloader isn't available")
        val req = YoutubeDLRequest(url).apply {
            addOption("--flat-playlist")
            addOption("-J")
            addOption("--no-warnings")
            addOption("--socket-timeout", "15")
        }
        val out = YoutubeDL.getInstance().execute(req, "info-" + url.hashCode(), null).out
        val json = JSONObject(out.substring(out.indexOf('{')))
        if (json.optString("_type") == "playlist") {
            val entries = json.optJSONArray("entries")
            val songs = (0 until (entries?.length() ?: 0)).mapNotNull { i -> entries!!.optJSONObject(i)?.let { songFrom(it, null) } }
            LinkInfo.Collection(json.optString("title").ifBlank { "Playlist" }, songs.distinctBy { it.id })
        } else {
            LinkInfo.Single(songFrom(json, url) ?: error("Nothing playable at this link"))
        }
    }

    private fun songFrom(o: JSONObject, fallbackUrl: String?): Song? {
        val rawId = o.optString("id").takeIf { it.isNotBlank() } ?: return null
        val extractor = (o.optString("ie_key").ifBlank { o.optString("extractor_key") }).lowercase()
        val isYoutube = extractor.startsWith("youtube")
        val url = o.optString("webpage_url").takeIf { it.startsWith("http") }
            ?: o.optString("url").takeIf { it.startsWith("http") }
            ?: (if (isYoutube) "https://music.youtube.com/watch?v=$rawId" else fallbackUrl)
            ?: return null

        var title = o.optString("track").ifBlank { o.optString("title") }
        val artistsArr = o.optJSONArray("artists")
        var artist = when {
            artistsArr != null && artistsArr.length() > 0 -> (0 until artistsArr.length()).joinToString(", ") { artistsArr.optString(it) }
            o.optString("artist").isNotBlank() -> o.optString("artist")
            o.optString("creator").isNotBlank() -> o.optString("creator")
            else -> cleanArtist(o.optString("channel").ifBlank { o.optString("uploader") })
        }
        if (o.optString("track").isBlank() && " - " in title) {
            // "Artist - Title (Official Video)" style uploads
            artist = title.substringBefore(" - ").trim()
            title = title.substringAfter(" - ").trim()
        }
        title = title.replace(Regex("\\s*[(\\[](official|lyric|lyrics|audio|video|visualizer|hd|4k)[^)\\]]*[)\\]]", RegexOption.IGNORE_CASE), "").trim()

        val thumbs = o.optJSONArray("thumbnails")
        var thumb: String? = null
        var bestW = -1
        if (thumbs != null) for (i in 0 until thumbs.length()) {
            val t = thumbs.optJSONObject(i) ?: continue
            val w = t.optInt("width", t.optInt("preference", 0))
            if (w >= bestW && t.optString("url").startsWith("http")) { bestW = w; thumb = t.optString("url") }
        }
        thumb = thumb ?: o.optString("thumbnail").takeIf { it.startsWith("http") }
            ?: if (isYoutube) "https://i.ytimg.com/vi/$rawId/hqdefault.jpg" else null

        return Song(
            id = if (isYoutube) "yt:$rawId" else "web:" + sha1(url),
            title = title.ifBlank { rawId },
            artist = artist,
            album = o.optString("album"),
            durationMs = (o.optDouble("duration", 0.0) * 1000).toLong(),
            thumb = thumb,
            url = url,
        )
    }

    // ------------------------------------------------------------ streaming

    /** Direct audio URL for a page. Blocking — call from the player's loader thread. */
    fun resolveStream(pageUrl: String): String {
        val now = System.currentTimeMillis()
        streamCache[pageUrl]?.let { (u, t) -> if (now - t < STREAM_TTL) return u }
        if (!runBlocking { awaitReady() }) throw java.io.IOException("Downloader isn't available")
        val req = YoutubeDLRequest(pageUrl).apply {
            addOption("-f", "bestaudio[ext=m4a]/bestaudio/best")
            addOption("-g")
            addOption("--no-playlist")
            addOption("--no-warnings")
        }
        val out = YoutubeDL.getInstance().execute(req, "stream-" + pageUrl.hashCode(), null).out
        val url = out.lineSequence().map { it.trim() }.firstOrNull { it.startsWith("http") }
            ?: throw java.io.IOException("No playable stream")
        streamCache[pageUrl] = url to now
        return url
    }

    fun forgetStream(pageUrl: String) {
        streamCache.remove(pageUrl)
    }

    private fun sha1(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray())
        .joinToString("") { "%02x".format(it) }.take(16)
}
