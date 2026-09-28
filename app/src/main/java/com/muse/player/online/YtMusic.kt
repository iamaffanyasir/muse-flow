package com.muse.player.online

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Song search against YouTube Music's web API. One HTTP call, returns clean titles, artists,
 * albums, durations and square covers — much faster than spinning up yt-dlp for a search.
 */
object YtMusic {
    private const val SONGS_FILTER = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"
    private val durationRe = Regex("^\\d+(:\\d{2}){1,2}$")
    private val thumbSize = Regex("=w\\d+-h\\d+.*$")

    suspend fun search(query: String): List<Song> = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("context", JSONObject().put("client", JSONObject()
                .put("clientName", "WEB_REMIX").put("clientVersion", "1.20240918.01.00").put("hl", "en")))
            .put("query", query)
            .put("params", SONGS_FILTER)
        val c = URL("https://music.youtube.com/youtubei/v1/search?prettyPrint=false").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = 10000
            c.readTimeout = 15000
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/128 Mobile Safari/537.36")
            c.setRequestProperty("Origin", "https://music.youtube.com")
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            if (c.responseCode != 200) throw java.io.IOException("Search failed (HTTP ${c.responseCode})")
            parse(JSONObject(c.inputStream.bufferedReader().use { it.readText() }))
        } finally {
            c.disconnect()
        }
    }

    private fun parse(root: JSONObject): List<Song> {
        val sections = root.optJSONObject("contents")
            ?.optJSONObject("tabbedSearchResultsRenderer")?.optJSONArray("tabs")?.optJSONObject(0)
            ?.optJSONObject("tabRenderer")?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")?.optJSONArray("contents") ?: return emptyList()
        val out = ArrayList<Song>()
        for (i in 0 until sections.length()) {
            val items = sections.optJSONObject(i)?.optJSONObject("musicShelfRenderer")?.optJSONArray("contents") ?: continue
            for (j in 0 until items.length()) {
                val r = items.optJSONObject(j)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                parseItem(r)?.let(out::add)
            }
        }
        return out.distinctBy { it.id }
    }

    private fun runs(r: JSONObject, col: Int): JSONArray? = r.optJSONArray("flexColumns")?.optJSONObject(col)
        ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")?.optJSONObject("text")?.optJSONArray("runs")

    private fun parseItem(r: JSONObject): Song? {
        val videoId = r.optJSONObject("playlistItemData")?.optString("videoId")?.takeIf { it.isNotBlank() }
            ?: runs(r, 0)?.optJSONObject(0)?.optJSONObject("navigationEndpoint")
                ?.optJSONObject("watchEndpoint")?.optString("videoId")?.takeIf { it.isNotBlank() }
            ?: return null
        val title = runs(r, 0)?.optJSONObject(0)?.optString("text").orEmpty()

        // Second column: "Artist & Artist • Album • 3:22" — split on the bullet separators.
        val segments = mutableListOf(StringBuilder())
        var album = ""
        runs(r, 1)?.let { arr ->
            for (k in 0 until arr.length()) {
                val run = arr.optJSONObject(k) ?: continue
                val text = run.optString("text")
                if (text.trim() == "•") { segments += StringBuilder(); continue }
                val browseId = run.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId").orEmpty()
                if (browseId.startsWith("MPRE")) album = text
                segments.last().append(text)
            }
        }
        val parts = segments.map { it.toString().trim() }.filter { it.isNotEmpty() }
        val duration = parts.lastOrNull { durationRe.matches(it) }?.let(::parseDuration) ?: 0L
        val artist = parts.firstOrNull { it != album && !durationRe.matches(it) }.orEmpty()

        val thumbs = r.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        val thumb = thumbs?.optJSONObject(thumbs.length() - 1)?.optString("url")
            ?.replace(thumbSize, "=w544-h544-l90-rj")
            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        return Song(
            id = "yt:$videoId",
            title = title,
            artist = artist,
            album = album,
            durationMs = duration,
            thumb = thumb,
            url = "https://music.youtube.com/watch?v=$videoId",
        )
    }
}
