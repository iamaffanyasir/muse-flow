package com.muse.player.data

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class Track(
    val id: String,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val albumKey: String,
    val trackNo: Int,
    val durationMs: Long,
    val folder: String,
    val dateAdded: Long,
    val fromMediaStore: Boolean,
    val artUri: Uri?,
) {
    val dedupeKey get() = "${title.lowercase()}|${artist.lowercase()}|${durationMs / 2000}"
}

data class Album(val key: String, val title: String, val artist: String, val tracks: List<Track>)
data class Artist(val name: String, val tracks: List<Track>) {
    val albumCount get() = tracks.distinctBy { it.albumKey }.size
}

fun List<Track>.albums(): List<Album> = groupBy { it.albumKey }.map { (key, ts) ->
    val sorted = ts.sortedWith(compareBy({ it.trackNo }, { it.title.lowercase() }))
    val artist = ts.groupingBy { it.artist }.eachCount().maxByOrNull { it.value }?.key ?: ""
    Album(key, ts.first().album, artist, sorted)
}.sortedBy { it.title.lowercase() }

fun List<Track>.artists(): List<Artist> = groupBy { it.artist }
    .map { Artist(it.key, it.value) }
    .sortedBy { it.name.lowercase() }

fun List<Track>.applySort(sort: SongSort): List<Track> = when (sort) {
    SongSort.TITLE -> sortedBy { it.title.lowercase() }
    SongSort.ARTIST -> sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))
    SongSort.ALBUM -> sortedWith(compareBy({ it.album.lowercase() }, { it.trackNo }))
    SongSort.RECENT -> sortedByDescending { it.dateAdded }
}

private val AUDIO_EXTS = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "wma", "amr", "mka", "alac", "aiff")
private val ALBUM_ART = Uri.parse("content://media/external/audio/albumart")

class MusicRepository(private val context: Context) {
    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks = _tracks.asStateFlow()
    private val _scanning = MutableStateFlow(false)
    val scanning = _scanning.asStateFlow()
    private val _loaded = MutableStateFlow(false)
    val loaded = _loaded.asStateFlow()
    private val mutex = Mutex()

    suspend fun scan(autoScan: Boolean, folders: Set<String>, minDurationSec: Int, hasPermission: Boolean) =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                _scanning.value = true
                try {
                    val result = LinkedHashMap<String, Track>()
                    if (autoScan && hasPermission) {
                        runCatching { queryMediaStore(minDurationSec) }.getOrNull()?.forEach { result[it.dedupeKey] = it }
                    }
                    for (f in folders) {
                        runCatching { scanTree(Uri.parse(f), minDurationSec) }.getOrNull()
                            ?.forEach { result.putIfAbsent(it.dedupeKey, it) }
                    }
                    _tracks.value = result.values.sortedBy { it.title.lowercase() }
                } finally {
                    _scanning.value = false
                    _loaded.value = true
                }
            }
        }

    private fun queryMediaStore(minDurationSec: Int): List<Track> {
        val collection = if (Build.VERSION.SDK_INT >= 29)
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.DISPLAY_NAME,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
        val out = ArrayList<Track>()
        context.contentResolver.query(collection, projection, selection, arrayOf((minDurationSec * 1000L).toString()), null)
            ?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    val albumId = c.getLong(4)
                    val path = c.getString(6) ?: ""
                    val name = c.getString(9) ?: ""
                    // Files saved as "Artist - Title.ext" still name themselves when tags are missing/"NA".
                    val base = name.substringBeforeLast('.')
                    val fromName = base.takeIf { " - " in it }?.let { it.substringBefore(" - ").trim() to it.substringAfter(" - ").trim() }
                    out += Track(
                        id = uri.toString(),
                        uri = uri,
                        title = c.getString(1).clean() ?: fromName?.second ?: base,
                        artist = c.getString(2).clean() ?: fromName?.first ?: "Unknown Artist",
                        album = c.getString(3).clean() ?: "Unknown Album",
                        albumKey = "ms:$albumId",
                        trackNo = c.getInt(8) % 1000,
                        durationMs = c.getLong(5),
                        folder = File(path).parentFile?.name ?: "",
                        dateAdded = c.getLong(7),
                        fromMediaStore = true,
                        artUri = ContentUris.withAppendedId(ALBUM_ART, albumId),
                    )
                }
            }
        return out
    }

    /** Walks a folder picked through the system picker (SAF) and reads tags of every audio file. */
    private fun scanTree(tree: Uri, minDurationSec: Int): List<Track> {
        val resolver = context.contentResolver
        val out = ArrayList<Track>()
        val stack = ArrayDeque<String>().apply { add(DocumentsContract.getTreeDocumentId(tree)) }
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        while (stack.isNotEmpty()) {
            val docId = stack.removeAt(stack.lastIndex)
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
            resolver.query(children, projection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: ""
                    val mime = c.getString(2) ?: ""
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        stack.add(id)
                    } else if (mime.startsWith("audio/") || name.substringAfterLast('.').lowercase() in AUDIO_EXTS) {
                        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                        val folder = docId.substringAfterLast(':').substringAfterLast('/')
                        readTags(uri, name, folder, c.getLong(3) / 1000)
                            ?.takeIf { it.durationMs >= minDurationSec * 1000L }
                            ?.let(out::add)
                    }
                }
            }
        }
        return out
    }

    private fun readTags(uri: Uri, name: String, folder: String, modified: Long): Track? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            val base = name.substringBeforeLast('.')
            val fromName = base.takeIf { " - " in it }?.let { it.substringBefore(" - ").trim() to it.substringAfter(" - ").trim() }
            val album = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).clean() ?: folder.ifBlank { "Unknown Album" }
            val artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).clean()
                ?: r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST).clean() ?: fromName?.first ?: "Unknown Artist"
            Track(
                id = uri.toString(),
                uri = uri,
                title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).clean() ?: fromName?.second ?: base,
                artist = artist,
                album = album,
                albumKey = "saf:${album.lowercase()}|${artist.lowercase()}",
                trackNo = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                    ?.substringBefore('/')?.trim()?.toIntOrNull() ?: 0,
                durationMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                folder = folder,
                dateAdded = modified,
                fromMediaStore = false,
                artUri = null,
            )
        } catch (_: Exception) {
            null
        } finally {
            runCatching { r.release() }
        }
    }
}

private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() && it != "<unknown>" && it != "NA" }
