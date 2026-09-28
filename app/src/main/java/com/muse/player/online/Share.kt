package com.muse.player.online

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Song links look like https://www.visualter.in/mf?v=<videoId>&t=<title>&a=<artist>.
 * With Muse Flow installed the link opens straight in the app and starts playing; otherwise the
 * page at www.visualter.in/mf offers "Open in Muse Flow" and a YouTube Music fallback.
 */
object Share {
    const val HOST = "www.visualter.in" // the bare domain 307-redirects here, which breaks app-link verification
    const val PATH = "/mf"

    fun linkFor(song: Song): String? {
        val id = song.id.takeIf { it.startsWith("yt:") }?.removePrefix("yt:") ?: return null
        return Uri.Builder().scheme("https").authority(HOST).path(PATH)
            .appendQueryParameter("v", id)
            .appendQueryParameter("t", song.title)
            .appendQueryParameter("a", song.artist)
            .build().toString()
    }

    /** Opens the system share sheet. Returns false when the song has no shareable link (local file). */
    fun send(context: Context, song: Song): Boolean {
        val link = linkFor(song)
        val text = when {
            link != null -> buildString {
                append("🎧 ${song.title}")
                if (song.artist.isNotBlank()) append(" — ${song.artist}")
                append("\n\nListen on Muse Flow:\n$link")
                append("\n\nOr on YouTube Music:\n${song.url ?: "https://music.youtube.com/watch?v=${song.id.removePrefix("yt:")}"}")
            }
            song.url != null -> "🎧 ${song.title}\n${song.url}"
            else -> return false
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, song.title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, "Share “${song.title}”").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    /** A Muse Flow song link (https or museflow://) → the song it points at. */
    fun parse(uri: Uri): Song? {
        val ours = (uri.scheme == "https" || uri.scheme == "http") &&
            (uri.host == HOST || uri.host == HOST.removePrefix("www.")) && (uri.path ?: "").startsWith(PATH)
        if (!ours && uri.scheme != "museflow") return null
        val id = uri.getQueryParameter("v")?.takeIf { Regex("[A-Za-z0-9_-]{6,20}").matches(it) } ?: return null
        return Song(
            id = "yt:$id",
            title = uri.getQueryParameter("t")?.takeIf { it.isNotBlank() } ?: "Shared song",
            artist = uri.getQueryParameter("a").orEmpty(),
            thumb = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
            url = "https://music.youtube.com/watch?v=$id",
        )
    }
}
