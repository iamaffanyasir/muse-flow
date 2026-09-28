package com.muse.player.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.muse.player.online.OnlineCatalog
import com.muse.player.online.Playlists
import com.muse.player.online.Ytdl
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.muse.player.Graph
import com.muse.player.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        // Online songs carry a "muse-stream:<page url>" uri; yt-dlp turns it into a real audio URL
        // (or the downloaded file) right when the player opens it, on the loader thread.
        val dataSources = ResolvingDataSource.Factory(DefaultDataSource.Factory(this)) { spec ->
            if (spec.uri.scheme != OnlineCatalog.SCHEME) return@Factory spec
            val page = Uri.decode(spec.uri.encodedSchemeSpecificPart)
            val local = OnlineCatalog.idForUrl(page)?.let(Playlists::localUriFor)
            spec.withUri(Uri.parse(local ?: Ytdl.resolveStream(page)))
        }
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSources))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(object : MediaSession.Callback {
                // Items coming from a controller lose their local URI, so restore it from the request metadata.
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>,
                ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(
                    mediaItems.map { item ->
                        item.buildUpon()
                            .setUri(item.requestMetadata.mediaUri ?: Uri.parse(item.mediaId))
                            .build()
                    }.toMutableList()
                )
            })
            .build()

        player.addListener(object : Player.Listener {
            private var lastRetry = ""

            // Resolve the upcoming song's stream in the background so skipping starts instantly.
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = prefetchNext()
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) = prefetchNext()

            private fun prefetchNext() {
                val next = player.nextMediaItemIndex.takeIf { it != androidx.media3.common.C.INDEX_UNSET } ?: return
                val uri = player.getMediaItemAt(next).localConfiguration?.uri ?: return
                if (uri.scheme != OnlineCatalog.SCHEME) return
                val page = Uri.decode(uri.encodedSchemeSpecificPart)
                if (OnlineCatalog.idForUrl(page)?.let(Playlists::localUriFor) != null) return
                scope.launch(kotlinx.coroutines.Dispatchers.IO) { runCatching { Ytdl.resolveStream(page) } }
            }

            override fun onPlayerError(error: PlaybackException) {
                // Stream URLs expire; forget it and retry once, otherwise move on.
                val uri = player.currentMediaItem?.localConfiguration?.uri ?: return
                if (uri.scheme != OnlineCatalog.SCHEME) return
                Ytdl.forgetStream(Uri.decode(uri.encodedSchemeSpecificPart))
                val id = player.currentMediaItem?.mediaId.orEmpty()
                if (lastRetry != id) {
                    lastRetry = id
                    player.prepare()
                    player.play()
                } else if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                }
            }
        })
        AudioFx.attach(player.audioSessionId)
        scope.launch { Graph.settings.flow.collect { AudioFx.apply(it) } }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        AudioFx.release()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
