package com.muse.player.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.muse.player.data.Artist
import com.muse.player.data.OnlineArt
import com.muse.player.data.Track
import com.muse.player.online.OnlineCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object ArtworkLoader {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 6).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val misses: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val noEmbedded: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Bumped whenever previously-missing art may now be available, so visible images retry. */
    private val _version = MutableStateFlow(0)
    val version = _version.asStateFlow()

    private val _bulk = MutableStateFlow<Pair<Int, Int>?>(null)
    val bulkProgress = _bulk.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val io = Dispatchers.IO.limitedParallelism(4)

    fun invalidate() {
        misses.clear()
        _version.value++
    }

    fun clearDownloaded() {
        OnlineArt.clear()
        cache.evictAll()
        invalidate()
    }

    fun cached(track: Track, size: Int): Bitmap? = cache.get("${track.id}#$size")
    fun cachedArtist(name: String, size: Int): Bitmap? = cache.get("artist:$name#$size")

    suspend fun load(context: Context, track: Track, size: Int): Bitmap? {
        val key = "${track.id}#$size"
        cache.get(key)?.let { return it }
        if (key in misses) return null

        // Online songs (and downloads of them) know their cover URL.
        val remote = track.artUri?.toString()?.takeIf { it.startsWith("http") }
        var definitive = true
        if (remote != null && track.uri.scheme == OnlineCatalog.SCHEME) {
            val out = OnlineArt.image(remote)
            val b = out.file?.let { withContext(io) { decodeFile(it, size) } }
            if (b != null) cache.put(key, b) else if (out.status != OnlineArt.Status.ERROR) misses.add(key)
            return b
        }

        var bmp: Bitmap? = withContext(io) {
            if (track.id in noEmbedded) null
            else decodeEmbedded(context, track, size).also { if (it == null) noEmbedded.add(track.id) }
                ?: OnlineArt.cachedAlbumArt(track)?.let { decodeFile(it, size) }
        }
        if (bmp == null) bmp = OnlineArt.cachedAlbumArt(track)?.let { withContext(io) { decodeFile(it, size) } }
        if (bmp == null && remote != null) {
            val out = OnlineArt.image(remote)
            bmp = out.file?.let { withContext(io) { decodeFile(it, size) } }
            if (out.status == OnlineArt.Status.ERROR) definitive = false
        }
        if (bmp == null) {
            if (OnlineArt.enabled) {
                val out = OnlineArt.albumArt(track)
                bmp = out.file?.let { withContext(io) { decodeFile(it, size) } }
                if (out.status == OnlineArt.Status.ERROR) definitive = false
            } else definitive = false
        }
        if (bmp != null) cache.put(key, bmp) else if (definitive) misses.add(key)
        return bmp
    }

    suspend fun loadArtist(name: String, size: Int): Bitmap? {
        val key = "artist:$name#$size"
        cache.get(key)?.let { return it }
        if (key in misses) return null
        var file = OnlineArt.cachedArtist(name)
        var definitive = true
        if (file == null && OnlineArt.enabled && OnlineArt.artistImages) {
            val out = OnlineArt.artistImage(name)
            file = out.file
            if (out.status == OnlineArt.Status.ERROR) definitive = false
        } else if (file == null) definitive = false
        val bmp = file?.let { withContext(io) { decodeFile(it, size) } }
        if (bmp != null) cache.put(key, bmp) else if (definitive) misses.add(key)
        return bmp
    }

    /** Downloads everything that's missing for the whole library in the background. */
    fun downloadAll(context: Context, tracks: List<Track>, artists: List<Artist>) {
        if (_bulk.value != null) return
        invalidate()
        OnlineArt.scope.launch {
            val albumTracks = tracks.groupBy { it.albumKey }.values.map { it.first() }
            val total = albumTracks.size + if (OnlineArt.artistImages) artists.size else 0
            var done = 0
            _bulk.value = 0 to total
            for (t in albumTracks) {
                runCatching { load(context, t, 256) }
                _bulk.value = ++done to total
            }
            if (OnlineArt.artistImages) for (a in artists) {
                runCatching { loadArtist(a.name, 256) }
                _bulk.value = ++done to total
            }
            _bulk.value = null
            _version.value++
        }
    }

    private fun decodeEmbedded(context: Context, track: Track, size: Int): Bitmap? {
        if (track.fromMediaStore && Build.VERSION.SDK_INT >= 29) {
            runCatching {
                return context.contentResolver.loadThumbnail(track.uri, Size(size, size), null).software()
            }
            return null
        }
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, track.uri)
            val bytes = r.embeddedPicture ?: return null
            decodeSampled(size) { opts -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { r.release() }
        }
    }

    private fun decodeFile(file: File, size: Int): Bitmap? = runCatching {
        decodeSampled(size) { opts -> BitmapFactory.decodeFile(file.path, opts) }
    }.getOrNull()

    private inline fun decodeSampled(size: Int, decode: (BitmapFactory.Options) -> Bitmap?): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        decode(bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= size && bounds.outHeight / (sample * 2) >= size) sample *= 2
        return decode(BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun Bitmap.software(): Bitmap =
        if (Build.VERSION.SDK_INT >= 26 && config == Bitmap.Config.HARDWARE) copy(Bitmap.Config.ARGB_8888, false) else this

    /** Three representative colours, strongest first — enough for gradients and the fluid background. */
    fun palette(bmp: Bitmap): List<Color> {
        val n = 24
        val s = Bitmap.createScaledBitmap(bmp, n, n, true)
        val hsv = FloatArray(3)
        val weight = DoubleArray(13)
        val r = DoubleArray(13); val g = DoubleArray(13); val b = DoubleArray(13)
        for (x in 0 until n) for (y in 0 until n) {
            val p = s.getPixel(x, y)
            android.graphics.Color.colorToHSV(p, hsv)
            val bin = if (hsv[1] < 0.18f || hsv[2] < 0.12f) 12 else (hsv[0] / 30f).toInt().coerceIn(0, 11)
            val w = if (bin == 12) 0.15 * hsv[2] + 0.01 else (hsv[1] * hsv[2]).toDouble() + 0.02
            weight[bin] += w
            r[bin] += android.graphics.Color.red(p) * w
            g[bin] += android.graphics.Color.green(p) * w
            b[bin] += android.graphics.Color.blue(p) * w
        }
        val bins = (0 until 13).filter { weight[it] > 0.0 }.sortedByDescending { weight[it] }
        val colors = bins.take(3).map { i ->
            val out = FloatArray(3)
            android.graphics.Color.colorToHSV(
                android.graphics.Color.rgb((r[i] / weight[i]).toInt(), (g[i] / weight[i]).toInt(), (b[i] / weight[i]).toInt()), out,
            )
            out[1] = (out[1] * 1.2f).coerceAtMost(0.9f)
            out[2] = out[2].coerceIn(0.35f, 0.92f)
            Color(android.graphics.Color.HSVToColor(out))
        }.toMutableList()
        if (colors.isEmpty()) colors += Color(0xFF8E8E93)
        while (colors.size < 3) {
            val out = FloatArray(3)
            android.graphics.Color.colorToHSV(android.graphics.Color.argb(255, (colors[0].red * 255).toInt(), (colors[0].green * 255).toInt(), (colors[0].blue * 255).toInt()), out)
            out[0] = (out[0] + if (colors.size == 1) 28f else -28f + 360f) % 360f
            out[2] = (out[2] * 0.8f).coerceAtLeast(0.3f)
            colors += Color(android.graphics.Color.HSVToColor(out))
        }
        return colors
    }
}

@Composable
fun rememberArtwork(track: Track?, size: Int = 256): Bitmap? {
    val context = LocalContext.current
    val version by ArtworkLoader.version.collectAsState()
    val initial = remember(track?.id, size) { track?.let { ArtworkLoader.cached(it, size) } }
    val state = produceState(initial, track?.id, size, version) {
        value = track?.let { ArtworkLoader.load(context, it, size) }
    }
    return state.value
}

/** Three animated colours taken from the artwork. */
@Composable
fun rememberArtPalette(track: Track?, fallback: Color, size: Int = 256): List<Color> {
    val bmp = rememberArtwork(track, size)
    val colors = remember(bmp, fallback) {
        bmp?.let { ArtworkLoader.palette(it) } ?: listOf(fallback, fallback.shade(0.3f), fallback.shade(0.55f))
    }
    val a by animateColorAsState(colors[0], tween(900), label = "pal0")
    val b by animateColorAsState(colors[1], tween(900), label = "pal1")
    val c by animateColorAsState(colors[2], tween(900), label = "pal2")
    return listOf(a, b, c)
}

@Composable
fun rememberArtColor(track: Track?, fallback: Color, size: Int = 256): Color = rememberArtPalette(track, fallback, size)[0]

private fun placeholderColors(seed: String): List<Color> {
    val hue = ((seed.hashCode() and 0x7fffffff) % 360).toFloat()
    return listOf(Color.hsv(hue, 0.3f, 0.75f), Color.hsv((hue + 40f) % 360f, 0.42f, 0.52f))
}

@Composable
private fun Placeholder(seed: String, icon: ImageVector) {
    val colors = remember(seed) { placeholderColors(seed) }
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(colors)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.fillMaxSize(0.4f))
    }
}

@Composable
fun Artwork(
    track: Track?,
    modifier: Modifier = Modifier,
    size: Int = 256,
    shape: Shape = RoundedCornerShape(8.dp),
    colorFilter: ColorFilter? = null,
) {
    val bmp = rememberArtwork(track, size)
    Box(modifier.clip(shape), contentAlignment = Alignment.Center) {
        Crossfade(bmp, animationSpec = tween(260), label = "art") { b ->
            if (b != null) {
                val img = remember(b) { b.asImageBitmap() }
                Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, colorFilter = colorFilter)
            } else {
                Placeholder(track?.album ?: "muse", Icons.Rounded.MusicNote)
            }
        }
    }
}

/** Artist photo when one was found online, otherwise the artist's first album cover. */
@Composable
fun ArtistImage(artist: Artist, modifier: Modifier = Modifier, size: Int = 256, shape: Shape = CircleShape) {
    val version by ArtworkLoader.version.collectAsState()
    val photo by produceState(ArtworkLoader.cachedArtist(artist.name, size), artist.name, size, version) {
        value = ArtworkLoader.loadArtist(artist.name, size) ?: value
    }
    Box(modifier.clip(shape)) {
        Crossfade(photo, animationSpec = tween(260), label = "artist") { p ->
            if (p != null) {
                val img = remember(p) { p.asImageBitmap() }
                Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else if (artist.name == "Unknown Artist") {
                Placeholder(artist.name, Icons.Rounded.Person)
            } else {
                Artwork(artist.tracks.first(), Modifier.fillMaxSize(), size, shape)
            }
        }
    }
}
