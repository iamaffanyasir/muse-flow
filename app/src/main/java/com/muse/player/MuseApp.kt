package com.muse.player

import android.app.Application
import android.content.Context
import com.muse.player.data.AppSettings
import com.muse.player.data.MusicRepository
import com.muse.player.online.Downloader
import com.muse.player.online.Playlists
import com.muse.player.online.Ytdl
import com.muse.player.data.OnlineArt
import com.muse.player.data.SettingsRepo
import com.muse.player.ui.ArtworkLoader
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MuseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }
}

/** Tiny service locator shared by the UI and the playback service (same process). */
object Graph {
    lateinit var settings: SettingsRepo
        private set
    lateinit var library: MusicRepository
        private set

    /** Latest settings snapshot for non-UI code (downloads). */
    @Volatile var current = AppSettings()
        private set

    fun init(context: Context) {
        val app = context.applicationContext
        settings = SettingsRepo(app)
        library = MusicRepository(app)
        OnlineArt.init(app)
        Playlists.init(app)
        Downloader.init(app)
        Ytdl.init(app)
        OnlineArt.scope.launch { settings.flow.collect { current = it } }
        OnlineArt.scope.launch {
            settings.flow.map { Triple(it.fetchArt, it.artistImages, it.wifiOnly) }
                .distinctUntilChanged()
                .collect { (fetch, artists, wifi) ->
                    val changed = fetch != OnlineArt.enabled || artists != OnlineArt.artistImages || wifi != OnlineArt.wifiOnly
                    OnlineArt.enabled = fetch
                    OnlineArt.artistImages = artists
                    OnlineArt.wifiOnly = wifi
                    if (changed) ArtworkLoader.invalidate()
                }
        }
    }
}
