package com.muse.player.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import android.net.Uri
import com.muse.player.data.OnlineArt
import com.muse.player.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerState(
    val mediaId: String? = null,
    val playing: Boolean = false,
    /** Waiting for audio — e.g. a stream is being resolved/buffered. */
    val buffering: Boolean = false,
    val duration: Long = 0L,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
    /** (queue index, mediaId) of the items that will play next, in play order. */
    val upNext: List<Pair<Int, String>> = emptyList(),
)

fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri(uri)
    .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(OnlineArt.cachedAlbumArt(this)?.let { Uri.fromFile(it) } ?: artUri)
            .build()
    )
    .build()

/** UI-side handle to the playback service. Must be used from the main thread. */
class PlayerConnection(private val context: Context, private val scope: CoroutineScope) {
    private var controller: MediaController? = null
    private var future: ListenableFuture<MediaController>? = null
    private val pending = ArrayList<(MediaController) -> Unit>()

    /** +1 when moving forward through the queue, -1 when going back; drives the artwork slide direction. */
    @Volatile var direction = 1
        private set

    private val _state = MutableStateFlow(PlayerState())
    val state = _state.asStateFlow()
    private val _position = MutableStateFlow(0L)
    val position = _position.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                direction = 1
            }
        }

        override fun onEvents(player: Player, events: Player.Events) {
            sync(
                events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    Player.EVENT_REPEAT_MODE_CHANGED,
                )
            )
        }
    }

    fun connect() {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token).buildAsync()
        future = f
        f.addListener({
            val c = runCatching { f.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            sync(true)
            pending.forEach { it(c) }
            pending.clear()
        }, ContextCompat.getMainExecutor(context))

        scope.launch {
            while (isActive) {
                controller?.let { _position.value = it.currentPosition }
                delay(if (_state.value.playing) 200 else 800)
            }
        }
    }

    fun release() {
        future?.let { MediaController.releaseFuture(it) }
        controller = null
    }

    private fun sync(queueChanged: Boolean) {
        val c = controller ?: return
        _state.value = PlayerState(
            mediaId = c.currentMediaItem?.mediaId,
            playing = c.playWhenReady && c.playbackState != Player.STATE_ENDED,
            buffering = c.playWhenReady && c.playbackState == Player.STATE_BUFFERING,
            duration = c.duration.coerceAtLeast(0L),
            shuffle = c.shuffleModeEnabled,
            repeat = c.repeatMode,
            upNext = if (queueChanged) computeUpNext(c) else _state.value.upNext,
        )
        _position.value = c.currentPosition
    }

    private fun computeUpNext(c: MediaController): List<Pair<Int, String>> {
        val timeline = c.currentTimeline
        if (timeline.isEmpty) return emptyList()
        val out = ArrayList<Pair<Int, String>>()
        var i = c.currentMediaItemIndex
        repeat(150) {
            i = timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, c.shuffleModeEnabled)
            if (i == C.INDEX_UNSET) return out
            out += i to c.getMediaItemAt(i).mediaId
        }
        return out
    }

    private fun run(block: (MediaController) -> Unit) {
        controller?.let(block) ?: pending.add(block)
    }

    fun play(tracks: List<Track>, startIndex: Int) = run { c ->
        if (tracks.isEmpty()) return@run
        c.shuffleModeEnabled = false
        c.setMediaItems(tracks.map { it.toMediaItem() }, startIndex.coerceIn(0, tracks.lastIndex), 0L)
        c.prepare()
        c.play()
    }

    fun togglePlay() = run { c ->
        if (c.playWhenReady && c.playbackState != Player.STATE_ENDED) c.pause()
        else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekTo(c.currentMediaItemIndex, 0L)
            c.play()
        }
    }

    fun next() = run { direction = 1; it.seekToNext() }
    fun previous() = run { direction = -1; it.seekToPrevious() }
    fun skipTo(index: Int) = run { direction = 1; it.seekTo(index, 0L); it.play() }

    fun seekTo(ms: Long) = run {
        it.seekTo(ms)
        _position.value = ms
    }

    fun toggleShuffle() = run { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeat() = run {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun playNext(track: Track) = run { c ->
        if (c.mediaItemCount == 0) play(listOf(track), 0)
        else c.addMediaItem(c.currentMediaItemIndex + 1, track.toMediaItem())
    }

    fun enqueue(track: Track) = run { c ->
        if (c.mediaItemCount == 0) play(listOf(track), 0) else c.addMediaItem(track.toMediaItem())
    }
}
