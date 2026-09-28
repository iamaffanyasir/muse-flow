package com.muse.player.online

import android.net.Uri
import com.muse.player.data.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * A playlist entry. Online songs use "yt:<videoId>" (or "web:<hash>") ids and carry the page [url] that
 * yt-dlp streams/downloads from; local songs use their content-uri as id and have no [url].
 */
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0L,
    val thumb: String? = null,
    val url: String? = null,
    val localUri: String? = null,
) {
    val isOnline get() = url != null

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("title", title).put("artist", artist).put("album", album)
        .put("duration", durationMs).put("thumb", thumb).put("url", url).put("local", localUri)

    companion object {
        fun fromJson(o: JSONObject) = Song(
            id = o.getString("id"),
            title = o.optString("title"),
            artist = o.optString("artist"),
            album = o.optString("album"),
            durationMs = o.optLong("duration"),
            thumb = o.optString("thumb").takeIf { it.isNotBlank() && it != "null" },
            url = o.optString("url").takeIf { it.isNotBlank() && it != "null" },
            localUri = o.optString("local").takeIf { it.isNotBlank() && it != "null" },
        )

        fun fromTrack(t: Track): Song = OnlineCatalog.song(t.id) ?: Song(
            id = t.id,
            title = t.title,
            artist = t.artist,
            album = t.album,
            durationMs = t.durationMs,
            thumb = t.artUri?.toString()?.takeIf { it.startsWith("http") },
            localUri = t.uri.toString(),
        )
    }
}

data class Playlist(val id: String, val name: String, val songs: List<Song>, val created: Long)

/** Registry of online songs so the player/UI can map a media id back to a playable [Track]. */
object OnlineCatalog {
    const val SCHEME = "muse-stream"

    private val songs = ConcurrentHashMap<String, Song>()
    private val byUrl = ConcurrentHashMap<String, String>()
    private val _tracks = MutableStateFlow<Map<String, Track>>(emptyMap())
    val tracks = _tracks.asStateFlow()

    fun register(list: List<Song>): List<Track> {
        val out = list.map { s ->
            songs[s.id] = s
            s.url?.let { byUrl[it] = s.id }
            s.toTrack()
        }
        _tracks.update { it + out.associateBy { t -> t.id } }
        return out
    }

    fun track(song: Song): Track = register(listOf(song)).first()
    fun song(id: String): Song? = songs[id]
    fun idForUrl(url: String): String? = byUrl[url]

    /** Re-points a song at its downloaded file. */
    fun refresh(id: String) {
        songs[id]?.let { register(listOf(it)) }
    }
}

fun streamUri(pageUrl: String): Uri = Uri.parse("${OnlineCatalog.SCHEME}:" + Uri.encode(pageUrl))

fun Song.toTrack(): Track {
    val local = Playlists.localUriFor(id) ?: localUri
    val uri = when {
        local != null -> Uri.parse(local)
        url != null -> streamUri(url)
        else -> Uri.parse(id)
    }
    return Track(
        id = id,
        uri = uri,
        title = title,
        artist = artist.ifBlank { "Unknown Artist" },
        album = album.ifBlank { if (isOnline) "Online" else "Unknown Album" },
        albumKey = "online:${album.lowercase()}|${artist.lowercase()}",
        trackNo = 0,
        durationMs = durationMs,
        folder = "",
        dateAdded = 0L,
        fromMediaStore = local?.startsWith("content://media") == true,
        artUri = thumb?.let(Uri::parse),
    )
}

fun parseDuration(text: String): Long {
    val parts = text.trim().split(':').mapNotNull { it.toLongOrNull() }
    if (parts.isEmpty() || parts.size > 3) return 0L
    return parts.fold(0L) { acc, p -> acc * 60 + p } * 1000
}

/** Channel names like "The Weeknd - Topic" or "TheWeekndVEVO" → "The Weeknd". */
fun cleanArtist(s: String): String = s.removeSuffix(" - Topic").replace(Regex("VEVO$"), "").trim()
