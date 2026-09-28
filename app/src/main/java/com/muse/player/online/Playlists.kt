package com.muse.player.online

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** User playlists plus the songId → downloaded-file map, persisted as one small JSON file. */
object Playlists {
    private lateinit var file: File
    private lateinit var appContext: Context
    // Single-threaded so writes land in the order they were made.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    private val _all = MutableStateFlow<List<Playlist>>(emptyList())
    val all = _all.asStateFlow()
    private val _downloads = MutableStateFlow<Map<String, String>>(emptyMap())
    val downloads = _downloads.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        file = File(context.filesDir, "playlists.json")
        runCatching {
            if (!file.exists()) return@runCatching
            val root = JSONObject(file.readText())
            val lists = root.optJSONArray("playlists") ?: JSONArray()
            _all.value = (0 until lists.length()).map { i ->
                val o = lists.getJSONObject(i)
                val songs = o.optJSONArray("songs") ?: JSONArray()
                Playlist(
                    id = o.getString("id"),
                    name = o.optString("name"),
                    songs = (0 until songs.length()).map { Song.fromJson(songs.getJSONObject(it)) },
                    created = o.optLong("created"),
                )
            }
            val dl = root.optJSONObject("downloads")
            _downloads.value = dl?.keys()?.asSequence()?.associateWith { dl.getString(it) } ?: emptyMap()
        }
        _all.value.forEach { OnlineCatalog.register(it.songs.filter(Song::isOnline)) }
        scope.launch { pruneMissingDownloads() }
    }

    fun localUriFor(songId: String): String? = _downloads.value[songId]

    fun get(id: String): Playlist? = _all.value.find { it.id == id }

    fun create(name: String, songs: List<Song> = emptyList()): Playlist {
        val p = Playlist(UUID.randomUUID().toString(), name.trim().ifBlank { "New playlist" }, songs.distinctBy { it.id }, System.currentTimeMillis())
        _all.update { it + p }
        persist()
        return p
    }

    fun rename(id: String, name: String) = edit(id) { it.copy(name = name.trim().ifBlank { it.name }) }
    fun delete(id: String) { _all.update { list -> list.filterNot { it.id == id } }; persist() }

    /** Returns how many songs were actually new to the playlist. */
    fun add(id: String, songs: List<Song>): Int {
        var added = 0
        edit(id) { p ->
            val existing = p.songs.map { it.id }.toHashSet()
            val fresh = songs.filter { existing.add(it.id) }
            added = fresh.size
            p.copy(songs = p.songs + fresh)
        }
        OnlineCatalog.register(songs.filter(Song::isOnline))
        return added
    }

    fun remove(id: String, songId: String) = edit(id) { p -> p.copy(songs = p.songs.filterNot { it.id == songId }) }

    fun markDownloaded(songId: String, uri: String) {
        _downloads.update { it + (songId to uri) }
        OnlineCatalog.refresh(songId)
        persist()
    }

    private fun edit(id: String, block: (Playlist) -> Playlist) {
        _all.update { list -> list.map { if (it.id == id) block(it) else it } }
        persist()
    }

    /** Drop download records whose file the user deleted, so those songs stream again. */
    private fun pruneMissingDownloads() {
        val resolver = appContext.contentResolver
        val missing = _downloads.value.filter { (_, uri) ->
            runCatching {
                resolver.query(Uri.parse(uri), arrayOf("_id"), null, null, null)?.use { it.count == 0 } ?: true
            }.getOrDefault(false)
        }.keys
        if (missing.isNotEmpty()) {
            _downloads.update { it - missing }
            missing.forEach(OnlineCatalog::refresh)
            persist()
        }
    }

    private fun persist() {
        val lists = _all.value
        val downloads = _downloads.value
        scope.launch {
            run {
                val root = JSONObject()
                root.put("playlists", JSONArray().apply {
                    lists.forEach { p ->
                        put(JSONObject().put("id", p.id).put("name", p.name).put("created", p.created)
                            .put("songs", JSONArray().apply { p.songs.forEach { put(it.toJson()) } }))
                    }
                })
                root.put("downloads", JSONObject().apply { downloads.forEach { (k, v) -> put(k, v) } })
                val tmp = File(file.parentFile, "playlists.json.tmp")
                tmp.writeText(root.toString())
                tmp.renameTo(file)
            }
        }
    }
}
