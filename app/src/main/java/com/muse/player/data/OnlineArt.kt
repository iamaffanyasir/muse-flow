package com.muse.player.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Finds artwork that isn't embedded in the files: album/track covers from iTunes (falling back to Deezer)
 * and artist photos from Deezer. Everything is cached on disk, so it only needs the network once.
 */
object OnlineArt {
    enum class Status { FOUND, NOT_FOUND, ERROR }
    class Outcome(val status: Status, val file: File?)

    private class HttpException(code: Int) : Exception("HTTP $code")

    private const val MISS_TTL = 5L * 24 * 60 * 60 * 1000
    private lateinit var dir: File
    private lateinit var appContext: Context

    @Volatile var enabled = true
    @Volatile var artistImages = true
    @Volatile var wifiOnly = false
    @Volatile private var offlineUntil = 0L

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val net = Dispatchers.IO.limitedParallelism(3)
    private val inflight = ConcurrentHashMap<String, Deferred<Outcome>>()

    fun init(context: Context) {
        appContext = context.applicationContext
        dir = File(context.filesDir, "art").apply { mkdirs() }
    }

    // ------------------------------------------------------------ keys

    private data class Query(val key: String, val artist: String, val album: String?, val title: String)

    private val genericAlbums = setOf("download", "downloads", "music", "audio", "songs", "whatsapp audio", "recordings", "bluetooth", "sounds")

    private fun query(t: Track): Query? {
        var artist = t.artist
        var title = t.title
        if (artist == "Unknown Artist") {
            // "Artist - Title.mp3" style files
            if (" - " !in title) return null
            artist = title.substringBefore(" - ").trim()
            title = title.substringAfter(" - ").trim()
        }
        val album = t.album.takeIf {
            it != "Unknown Album" && !it.equals(t.folder, ignoreCase = true) && it.lowercase() !in genericAlbums
        }
        val key = if (album != null) "al:${norm(artist)}|${norm(album)}" else "tr:${norm(artist)}|${norm(title)}"
        return Query(key, artist, album, title)
    }

    private fun artistKey(name: String) = "ar:${norm(name)}"
    private fun fileFor(key: String) = File(dir, sha1(key) + ".jpg")
    private fun missFor(key: String) = File(dir, sha1(key) + ".miss")

    fun cachedAlbumArt(t: Track): File? = query(t)?.let { fileFor(it.key) }?.takeIf { it.length() > 0 }
    fun cachedArtist(name: String): File? = fileFor(artistKey(name)).takeIf { it.length() > 0 }

    // ------------------------------------------------------------ public api

    suspend fun albumArt(t: Track): Outcome {
        if (!enabled) return Outcome(Status.ERROR, null)
        val q = query(t) ?: return Outcome(Status.NOT_FOUND, null)
        return fetch(q.key) { f -> findAlbumArt(q, f) }
    }

    suspend fun artistImage(name: String): Outcome {
        if (!enabled || !artistImages) return Outcome(Status.ERROR, null)
        if (name == "Unknown Artist" || name.isBlank()) return Outcome(Status.NOT_FOUND, null)
        return fetch(artistKey(name)) { f -> f.attempt { deezerArtist(name) } }
    }

    /** A cover that's already known by URL (online songs). Ignores the Wi-Fi-only setting — they're tiny. */
    suspend fun image(url: String): Outcome = fetch("url:$url", respectWifiOnly = false) { url }

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    fun cacheSizeBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    // ------------------------------------------------------------ fetching

    private class Finder {
        var hadError = false
        inline fun attempt(block: () -> String?): String? = try {
            block()
        } catch (e: IOException) {
            throw e
        } catch (_: Exception) {
            hadError = true
            null
        }
    }

    private fun networkAllowed(respectWifiOnly: Boolean): Boolean {
        val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        return !(respectWifiOnly && wifiOnly && cm.isActiveNetworkMetered)
    }

    private suspend fun fetch(key: String, respectWifiOnly: Boolean = true, find: (Finder) -> String?): Outcome {
        val file = fileFor(key)
        if (file.length() > 0) return Outcome(Status.FOUND, file)
        val miss = missFor(key)
        val now = System.currentTimeMillis()
        if (miss.exists() && now - miss.lastModified() < MISS_TTL) return Outcome(Status.NOT_FOUND, null)
        if (now < offlineUntil || !networkAllowed(respectWifiOnly)) return Outcome(Status.ERROR, null)

        val job = inflight.getOrPut(key) {
            scope.async(net, start = CoroutineStart.LAZY) {
                try {
                    val finder = Finder()
                    val url = find(finder)
                    when {
                        url != null && download(url, file) -> Outcome(Status.FOUND, file)
                        url == null && !finder.hadError -> {
                            miss.writeText("")
                            Outcome(Status.NOT_FOUND, null)
                        }
                        else -> Outcome(Status.ERROR, null)
                    }
                } catch (_: IOException) {
                    offlineUntil = System.currentTimeMillis() + 60_000
                    Outcome(Status.ERROR, null)
                } catch (_: Exception) {
                    Outcome(Status.ERROR, null)
                } finally {
                    inflight.remove(key)
                }
            }
        }
        job.start()
        return job.await()
    }

    private fun findAlbumArt(q: Query, f: Finder): String? {
        if (q.album != null) {
            f.attempt { itunesAlbum(q.artist, q.album) }?.let { return it }
            f.attempt { deezerAlbum(q.artist, q.album) }?.let { return it }
        }
        f.attempt { itunesSong(q.artist, q.title) }?.let { return it }
        f.attempt { deezerTrack(q.artist, q.title) }?.let { return it }
        return null
    }

    private fun itunes(term: String, entity: String): JSONArray =
        JSONObject(get("https://itunes.apple.com/search?media=music&entity=$entity&limit=8&term=${enc(term)}"))
            .optJSONArray("results") ?: JSONArray()

    private fun String.hiRes() = replace("100x100bb", "600x600bb")

    private fun itunesAlbum(artist: String, album: String): String? {
        val arr = itunes("$artist $album", "album")
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (similar(o.optString("collectionName"), album) && similarArtist(o.optString("artistName"), artist)) {
                return o.optString("artworkUrl100").takeIf { it.isNotBlank() }?.hiRes()
            }
        }
        return null
    }

    private fun itunesSong(artist: String, title: String): String? {
        val arr = itunes("$artist $title", "song")
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (similar(o.optString("trackName"), title) && similarArtist(o.optString("artistName"), artist)) {
                return o.optString("artworkUrl100").takeIf { it.isNotBlank() }?.hiRes()
            }
        }
        return null
    }

    private fun deezer(path: String, q: String): JSONArray =
        JSONObject(get("https://api.deezer.com/search/$path?limit=8&q=${enc(q)}")).optJSONArray("data") ?: JSONArray()

    private fun deezerAlbum(artist: String, album: String): String? {
        val arr = deezer("album", "artist:\"$artist\" album:\"$album\"")
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val a = o.optJSONObject("artist")?.optString("name").orEmpty()
            if (similar(o.optString("title"), album) && similarArtist(a, artist)) {
                return o.optString("cover_xl").takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    private fun deezerTrack(artist: String, title: String): String? {
        val arr = deezer("track", "artist:\"$artist\" track:\"$title\"")
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val a = o.optJSONObject("artist")?.optString("name").orEmpty()
            if (similar(o.optString("title"), title) && similarArtist(a, artist)) {
                return o.optJSONObject("album")?.optString("cover_xl")?.takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    private fun deezerArtist(name: String): String? {
        val lookup = splitArtists(name).firstOrNull() ?: name
        val arr = deezer("artist", lookup)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val pic = o.optString("picture_xl")
            // Deezer serves a generic silhouette for artists without a photo: its path has an empty image id.
            if (similar(o.optString("name"), lookup) && pic.isNotBlank() && "/artist//" !in pic) return pic
        }
        return null
    }

    // ------------------------------------------------------------ helpers

    private fun get(url: String): String = String(getBytes(url))

    private fun getBytes(url: String): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 10000
        c.setRequestProperty("User-Agent", "Muse/1.1 (Android music player)")
        try {
            if (c.responseCode != 200) throw HttpException(c.responseCode)
            return c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }

    private fun download(url: String, target: File): Boolean {
        val bytes = getBytes(url)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return false
        val tmp = File(dir, target.name + ".tmp")
        tmp.writeBytes(bytes)
        return tmp.renameTo(target)
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun sha1(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray())
        .joinToString("") { "%02x".format(it) }

    private val bracketed = Regex("\\(.*?\\)|\\[.*?]")
    private val featuring = Regex("\\b(feat|ft|featuring)\\b.*")
    private val nonWord = Regex("[^\\p{L}\\p{N}]+")

    private fun norm(s: String) = s.lowercase()
        .replace(bracketed, " ")
        .replace(featuring, " ")
        .replace(nonWord, " ")
        .trim()

    private fun similar(a: String, b: String): Boolean {
        val x = norm(a)
        val y = norm(b)
        if (x.isEmpty() || y.isEmpty()) return false
        if (x == y) return true
        if ((y.length >= 4 && x.contains(y)) || (x.length >= 4 && y.contains(x))) return true
        val tx = x.split(' ').toSet()
        val ty = y.split(' ').toSet()
        return tx.intersect(ty).size.toFloat() / minOf(tx.size, ty.size) >= 0.6f
    }

    private fun splitArtists(s: String) = s.split(Regex("\\s*(,|&|/|;| x | and |feat\\.?|ft\\.?)\\s*", RegexOption.IGNORE_CASE))
        .map { it.trim() }.filter { it.isNotEmpty() }

    private fun similarArtist(a: String, b: String): Boolean =
        similar(a, b) || splitArtists(a).any { pa -> splitArtists(b).any { similar(pa, it) } }
}
