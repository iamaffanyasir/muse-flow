package com.muse.player.ui

import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.muse.player.Graph
import com.muse.player.MainViewModel
import com.muse.player.data.AppSettings
import com.muse.player.data.Keys
import com.muse.player.data.NowPlayingStyle
import com.muse.player.data.Track
import com.muse.player.playback.PlayerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// ------------------------------------------------------------------ mini player

@Composable
fun MiniPlayer(track: Track, vm: MainViewModel, adaptive: Boolean, onExpand: () -> Unit) {
    val st by vm.player.state.collectAsState()
    val pos by vm.player.position.collectAsState()
    val cs = MaterialTheme.colorScheme
    val art = rememberArtColor(track, cs.primary)
    val bg = if (adaptive) lerp(cs.surfaceContainerHigh, art, 0.16f) else cs.surfaceContainerHigh
    var dragX by remember { mutableFloatStateOf(0f) }
    val shiftX by animateFloatAsState(dragX * 0.35f, spring(dampingRatio = 0.7f), label = "miniX")
    val artScale by animateFloatAsState(if (st.playing) 1f else 0.84f, spring(0.5f, 300f), label = "miniArt")
    val progress = if (st.duration > 0) (pos.toFloat() / st.duration).coerceIn(0f, 1f) else 0f
    val animProgress by animateFloatAsState(progress, tween(250, easing = LinearEasing), label = "miniProg")

    Box(
        Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth().height(62.dp)
            .graphicsLayer { translationX = shiftX }
            .shadow(18.dp, RoundedCornerShape(22.dp), spotColor = Color.Black.copy(alpha = 0.3f))
            .clip(RoundedCornerShape(22.dp))
            .background(bg)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (dragX > 160f) vm.player.previous() else if (dragX < -160f) vm.player.next()
                        dragX = 0f
                    },
                    onDragCancel = { dragX = 0f },
                ) { _, d -> dragX += d }
            }
            .pointerInput(Unit) {
                var fired = false
                detectVerticalDragGestures(onDragStart = { fired = false }) { _, d ->
                    if (!fired && d < -6f) { fired = true; onExpand() }
                }
            }
            .clickable(onClick = onExpand),
    ) {
        Row(Modifier.fillMaxSize().padding(start = 8.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(
                track, Modifier.size(46.dp).graphicsLayer { scaleX = artScale; scaleY = artScale },
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(Modifier.width(12.dp))
            AnimatedContent(
                track, Modifier.weight(1f), contentKey = { it.id },
                transitionSpec = {
                    (slideInVertically { it / 2 } + fadeIn(tween(250))) togetherWith (slideOutVertically { -it / 2 } + fadeOut(tween(150)))
                }, label = "miniTitle",
            ) { t ->
                Column {
                    Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                    Text(t.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
            Box(Modifier.size(44.dp).clip(CircleShape).pressScale(0.8f) { vm.player.togglePlay() }, contentAlignment = Alignment.Center) {
                if (st.buffering) CircularProgressIndicator(Modifier.size(36.dp), color = cs.primary, strokeWidth = 2.dp)
                PlayPauseIcon(st.playing, cs.onSurface, 24.dp)
            }
            Box(Modifier.size(44.dp).clip(CircleShape).pressScale(0.8f) { vm.player.next() }, contentAlignment = Alignment.Center) {
                Icon(MuseIcons.Next, "Next", tint = cs.onSurface, modifier = Modifier.size(24.dp))
            }
        }
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 22.dp, vertical = 3.dp)
                .height(2.dp).clip(CircleShape).background(cs.onSurface.copy(alpha = 0.08f))
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(animProgress).background(if (adaptive) art else cs.primary))
        }
    }
}

@Composable
fun PlayPauseIcon(playing: Boolean, tint: Color, size: Dp) {
    AnimatedContent(
        playing,
        transitionSpec = {
            (scaleIn(initialScale = 0.4f, animationSpec = spring(0.45f, 500f)) + fadeIn(tween(120))) togetherWith
                (scaleOut(targetScale = 0.4f) + fadeOut(tween(100)))
        }, label = "pp",
    ) { p ->
        Icon(if (p) MuseIcons.Pause else MuseIcons.Play, if (p) "Pause" else "Play", tint = tint, modifier = Modifier.size(size))
    }
}

// ------------------------------------------------------------------ style specs

/** What each Now Playing style shows and how. */
private data class Spec(
    val lightBg: Boolean = false,   // follows the app theme instead of a dark artwork backdrop
    val centered: Boolean = false,
    val filledPlay: Boolean = false,
    val wavy: Boolean = false,
    val info: Boolean = true,       // standard title/artist row
    val seek: Boolean = true,       // linear seek bar
    val volume: Boolean = true,
    val swipe: Boolean = true,      // swipe the artwork to skip
)

private fun NowPlayingStyle.spec() = when (this) {
    NowPlayingStyle.CLASSIC, NowPlayingStyle.IMMERSIVE -> Spec()
    NowPlayingStyle.MINIMAL -> Spec(lightBg = true, centered = true, filledPlay = true, wavy = true, volume = false)
    NowPlayingStyle.CARD -> Spec(centered = true, volume = false)
    NowPlayingStyle.GLASS -> Spec(filledPlay = true)
    NowPlayingStyle.POSTER -> Spec(wavy = true, info = false, volume = false)
    NowPlayingStyle.ORBIT -> Spec(centered = true, filledPlay = true, seek = false, volume = false, swipe = false)
    NowPlayingStyle.MONO -> Spec(centered = true, filledPlay = true, volume = false)
}

private val grayscale = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

// ------------------------------------------------------------------ now playing

@Composable
fun NowPlayingScreen(
    track: Track,
    byId: Map<String, Track>,
    vm: MainViewModel,
    s: AppSettings,
    onCollapse: () -> Unit,
    onOpenEq: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val st by vm.player.state.collectAsState()
    val artPalette = rememberArtPalette(track, cs.primary, 1024)
    val palette = if (s.adaptiveColor) artPalette
    else listOf(cs.primary, cs.primary.shade(0.35f), lerp(cs.primary, Color.White, 0.25f))
    var offset by remember { mutableFloatStateOf(0f) }
    val dragState = rememberDraggableState { d -> offset = (offset + d).coerceAtLeast(0f) }

    // Warm the cache so the next cover slides in already loaded.
    val nextId = st.upNext.firstOrNull()?.second
    LaunchedEffect(nextId) { nextId?.let(byId::get)?.let { ArtworkLoader.load(context, it, 1024) } }

    Box(
        Modifier.fillMaxSize()
            .graphicsLayer {
                translationY = offset
                val p = (offset / size.height).coerceIn(0f, 1f)
                scaleX = 1f - p * 0.1f
                scaleY = 1f - p * 0.1f
                shape = RoundedCornerShape(32.dp)
                clip = offset > 0f
            }
            .background(Color.Black)
            .draggable(
                dragState, Orientation.Vertical,
                onDragStopped = { v ->
                    if (offset > 260f || v > 2200f) onCollapse()
                    else animate(offset, 0f, animationSpec = spring(0.75f, 400f)) { value, _ -> offset = value }
                },
            ),
    ) {
        Crossfade(s.nowPlayingStyle, animationSpec = tween(400), label = "npStyle") { style ->
            Box(Modifier.fillMaxSize()) {
                Background(style, track, palette, st.playing, s.amoled)
                CompositionLocalProvider(LocalContentColor provides if (style.spec().lightBg) cs.onBackground else Color.White) {
                    NowPlayingContent(style, track, st, byId, vm, s, onCollapse, onOpenEq)
                }
            }
        }
    }
}

/** Slowly drifting blobs of the artwork's colours — the "living" background. */
@Composable
private fun FluidBackground(colors: List<Color>, playing: Boolean, darken: Float, amoled: Boolean) {
    var time by remember { mutableFloatStateOf(0f) }
    val speed by animateFloatAsState(if (playing) 1f else 0.2f, tween(1500), label = "fluidSpeed")
    LaunchedEffect(Unit) {
        var last = -1L
        while (isActive) {
            withFrameMillis { now ->
                if (last >= 0) time += (now - last) / 1000f * speed
                last = now
            }
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        val t = time * 0.22f
        val w = size.width
        val h = size.height
        val r = maxOf(w, h) * 0.62f
        drawRect(colors[0].shade(darken + 0.3f))
        fun blob(c: Color, cx: Float, cy: Float, radius: Float) {
            val center = Offset(cx, cy)
            drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.9f), c.copy(alpha = 0f)), center, radius), radius, center)
        }
        blob(colors[1].shade(darken), w * (0.2f + 0.3f * sin(t * 1.1f)), h * (0.22f + 0.12f * cos(t * 0.9f)), r)
        blob(colors[0].shade(darken), w * (0.85f + 0.2f * cos(t * 0.7f)), h * (0.42f + 0.16f * sin(t * 1.3f)), r * 0.9f)
        blob(colors[2].shade(darken), w * (0.35f + 0.3f * sin(t * 0.6f + 2f)), h * (0.78f + 0.1f * cos(t + 1f)), r)
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = if (amoled) 0.8f else 0.5f))))
    }
}

@Composable
private fun Background(style: NowPlayingStyle, track: Track, palette: List<Color>, playing: Boolean, amoled: Boolean) {
    when (style) {
        NowPlayingStyle.CLASSIC -> FluidBackground(palette, playing, 0.3f, amoled)
        NowPlayingStyle.CARD -> FluidBackground(palette, playing, 0.5f, amoled)
        NowPlayingStyle.POSTER -> FluidBackground(palette, playing, 0.62f, amoled)
        NowPlayingStyle.ORBIT -> FluidBackground(palette, playing, 0.45f, amoled)
        NowPlayingStyle.IMMERSIVE -> Box(Modifier.fillMaxSize()) {
            FluidBackground(palette, playing, 0.45f, amoled)
            Artwork(
                track,
                Modifier.fillMaxWidth().aspectRatio(0.92f)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(Brush.verticalGradient(0.5f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                    },
                size = 1024, shape = RectangleShape,
            )
            Box(Modifier.fillMaxWidth().height(140.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.4f), Color.Transparent))))
        }
        NowPlayingStyle.GLASS -> Box(Modifier.fillMaxSize().background(palette[0].shade(0.6f))) {
            // The cover itself, blown up and blurred behind everything.
            Artwork(track, Modifier.fillMaxSize().graphicsLayer { scaleX = 1.3f; scaleY = 1.3f }.blur(60.dp), size = 256, shape = RectangleShape)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = if (amoled) 0.85f else 0.6f)))
                )
            )
        }
        NowPlayingStyle.MONO -> Box(
            Modifier.fillMaxSize().background(Color(0xFF050505))
                .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.07f), Color.Transparent), radius = 1100f, center = Offset(540f, 300f)))
        )
        NowPlayingStyle.MINIMAL -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NowPlayingContent(
    style: NowPlayingStyle,
    track: Track,
    st: PlayerState,
    byId: Map<String, Track>,
    vm: MainViewModel,
    s: AppSettings,
    onCollapse: () -> Unit,
    onOpenEq: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val content = LocalContentColor.current
    val spec = style.spec()
    val highlight = if (spec.lightBg) cs.primary else content
    var showQueue by rememberSaveable { mutableStateOf(false) }

    // staggered entrance
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(750, easing = FastOutSlowInEasing)) }
    fun Modifier.stagger(i: Int) = graphicsLayer {
        val p = ((appear.value - i * 0.07f) / 0.65f).coerceIn(0f, 1f)
        alpha = p
        translationY = (1f - p) * 70f
    }

    val body: @Composable ColumnScope.() -> Unit = {
        Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 14.dp).stagger(1), contentAlignment = Alignment.Center) {
            AnimatedContent(
                showQueue,
                transitionSpec = { (fadeIn(tween(250)) + scaleIn(initialScale = 0.94f)) togetherWith (fadeOut(tween(150)) + scaleOut(targetScale = 0.94f)) },
                label = "queue",
            ) { queue ->
                when {
                    queue -> QueueView(st, byId, vm, content)
                    spec.swipe -> SwipeableHero(track, vm) { t -> Hero(style, t, st, vm) }
                    else -> Hero(style, track, st, vm)
                }
            }
        }

        // Glass keeps its controls on a frosted panel.
        val panel = if (style == NowPlayingStyle.GLASS) Modifier
            .clip(RoundedCornerShape(28.dp)).background(Color.White.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(28.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp)
        else Modifier
        Column(Modifier.fillMaxWidth().then(panel)) {
            if (spec.info) {
                Row(Modifier.fillMaxWidth().stagger(2), verticalAlignment = Alignment.CenterVertically) {
                    if (spec.centered) Spacer(Modifier.width(48.dp))
                    AnimatedContent(
                        track, Modifier.weight(1f), contentKey = { it.id },
                        transitionSpec = {
                            val dir = vm.player.direction
                            (fadeIn(tween(300)) + slideInHorizontally { it / 8 * dir }) togetherWith (fadeOut(tween(150)) + slideOutHorizontally { -it / 8 * dir })
                        },
                        label = "npTitle",
                    ) { t -> TitleBlock(style, t, spec, content, cs.primary) }
                    TrackMenu(track, tint = content.copy(alpha = 0.75f))
                }
                Spacer(Modifier.height(10.dp))
            }
            if (spec.seek) Column(Modifier.stagger(3)) { SeekSection(vm, st, highlight, content, spec.wavy) }
            Spacer(Modifier.height(2.dp))

            Row(
                Modifier.fillMaxWidth().stagger(4),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ControlButton(MuseIcons.Previous, 38.dp, content) { vm.player.previous() }
                PlayButton(st.playing, st.buffering, spec, content) { vm.player.togglePlay() }
                ControlButton(MuseIcons.Next, 38.dp, content) { vm.player.next() }
            }

            if (spec.volume) {
                Spacer(Modifier.height(2.dp))
                Box(Modifier.stagger(5)) { VolumeBar(content) }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp, bottom = if (style == NowPlayingStyle.GLASS) 0.dp else 10.dp).stagger(6),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ToggleIcon(Icons.Rounded.Shuffle, st.shuffle, highlight, content) { vm.player.toggleShuffle() }
                ToggleIcon(
                    if (st.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    st.repeat != Player.REPEAT_MODE_OFF, highlight, content,
                ) { vm.player.cycleRepeat() }
                ToggleIcon(Icons.Rounded.GraphicEq, s.eqEnabled, highlight, content, onOpenEq)
                ToggleIcon(Icons.AutoMirrored.Rounded.QueueMusic, showQueue, highlight, content) { showQueue = !showQueue }
            }
        }
        if (style == NowPlayingStyle.GLASS) Spacer(Modifier.height(12.dp))
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = if (style == NowPlayingStyle.CARD || style == NowPlayingStyle.GLASS) 16.dp else 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.padding(top = 8.dp).size(36.dp, 5.dp).clip(CircleShape).background(content.copy(alpha = 0.3f)))
        Row(Modifier.fillMaxWidth().stagger(0), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCollapse) {
                Icon(Icons.Rounded.KeyboardArrowDown, "Close", tint = content, modifier = Modifier.size(28.dp))
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                val streaming = track.uri.scheme == com.muse.player.online.OnlineCatalog.SCHEME
                Text(
                    when {
                        streaming && st.buffering -> "CONNECTING…"
                        streaming -> "STREAMING"
                        else -> "PLAYING FROM"
                    },
                    style = MaterialTheme.typography.labelSmall, color = content.copy(alpha = 0.55f),
                )
                Text(track.album, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, color = content)
            }
            IconButton(onClick = {
                val next = NowPlayingStyle.entries[(style.ordinal + 1) % NowPlayingStyle.entries.size]
                Graph.settings.put(Keys.npStyle, next.name)
            }) { Icon(Icons.Rounded.AutoAwesome, "Change style", tint = content, modifier = Modifier.size(22.dp)) }
        }

        if (style == NowPlayingStyle.CARD) {
            // Everything lives on one floating frosted card.
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(top = 6.dp, bottom = 12.dp)
                    .shadow(24.dp, RoundedCornerShape(34.dp), spotColor = Color.Black.copy(alpha = 0.4f))
                    .clip(RoundedCornerShape(34.dp)).background(Color.White.copy(alpha = 0.09f))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(34.dp))
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = body,
            )
        } else {
            body()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TitleBlock(style: NowPlayingStyle, t: Track, spec: Spec, content: Color, accent: Color) {
    val typo = MaterialTheme.typography
    val titleStyle: TextStyle = when (style) {
        NowPlayingStyle.MINIMAL -> typo.headlineMedium
        NowPlayingStyle.CARD, NowPlayingStyle.ORBIT -> typo.headlineSmall
        NowPlayingStyle.MONO -> typo.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        else -> typo.titleLarge
    }
    val artistStyle: TextStyle = if (style == NowPlayingStyle.MONO) typo.labelLarge.copy(letterSpacing = 3.sp)
    else typo.titleMedium.copy(fontWeight = FontWeight.Normal)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (spec.centered) Alignment.CenterHorizontally else Alignment.Start) {
        Text(
            if (style == NowPlayingStyle.MONO) t.title.uppercase() else t.title,
            maxLines = 1, color = content, style = titleStyle,
            textAlign = if (spec.centered) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.basicMarquee(),
        )
        Text(
            if (style == NowPlayingStyle.MONO) t.artist.uppercase() else t.artist,
            maxLines = 1, style = artistStyle,
            color = when {
                spec.lightBg -> accent
                style == NowPlayingStyle.MONO -> content.copy(alpha = 0.5f)
                else -> content.copy(alpha = 0.62f)
            },
            modifier = Modifier.basicMarquee(),
        )
    }
}

/** Artwork that follows your finger; fling it left/right to skip. Track changes slide in the matching direction. */
@Composable
private fun SwipeableHero(track: Track, vm: MainViewModel, content: @Composable (Track) -> Unit) {
    var dx by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    fun settle() {
        scope.launch { animate(dx, 0f, animationSpec = spring(0.7f, 320f)) { v, _ -> dx = v } }
    }
    Box(
        Modifier.fillMaxSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val threshold = size.width * 0.2f
                        if (dx < -threshold) vm.player.next() else if (dx > threshold) vm.player.previous()
                        settle()
                    },
                    onDragCancel = { settle() },
                ) { c, d -> c.consume(); dx += d }
            }
            .graphicsLayer {
                translationX = dx * 0.7f
                rotationZ = dx / 70f
                alpha = 1f - (abs(dx) / size.width).coerceAtMost(0.45f)
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            track, contentKey = { it.id },
            transitionSpec = {
                val dir = vm.player.direction
                (slideInHorizontally(spring(0.82f, 280f)) { it * dir } + fadeIn(tween(250)) + scaleIn(initialScale = 0.8f)) togetherWith
                    (slideOutHorizontally(tween(260)) { -it * dir } + fadeOut(tween(200)) + scaleOut(targetScale = 0.8f))
            },
            label = "heroTrack",
        ) { t -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content(t) } }
    }
}

@Composable
private fun Hero(style: NowPlayingStyle, track: Track, st: PlayerState, vm: MainViewModel) {
    val playing = st.playing
    val scale by animateFloatAsState(if (playing) 1f else 0.8f, spring(dampingRatio = 0.55f, stiffness = 200f), label = "heroScale")
    val soft = scale * 0.1f + 0.9f
    val elevation by animateDpAsState(if (playing) 30.dp else 8.dp, label = "heroElev")
    when (style) {
        NowPlayingStyle.CLASSIC -> Artwork(
            track,
            Modifier.aspectRatio(1f)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .shadow(elevation, RoundedCornerShape(16.dp)),
            size = 1024, shape = RoundedCornerShape(16.dp),
        )
        NowPlayingStyle.IMMERSIVE -> Spacer(Modifier.fillMaxSize())
        NowPlayingStyle.MINIMAL -> Artwork(
            track,
            Modifier.fillMaxWidth(0.74f).aspectRatio(1f)
                .graphicsLayer { scaleX = soft; scaleY = soft }
                .shadow(elevation * 0.6f, RoundedCornerShape(28.dp)),
            size = 1024, shape = RoundedCornerShape(28.dp),
        )
        NowPlayingStyle.CARD -> Artwork(
            track,
            Modifier.aspectRatio(1f)
                .graphicsLayer { scaleX = soft; scaleY = soft }
                .shadow(elevation * 0.7f, RoundedCornerShape(24.dp)),
            size = 1024, shape = RoundedCornerShape(24.dp),
        )
        NowPlayingStyle.GLASS -> Artwork(
            track,
            Modifier.fillMaxWidth(0.84f).aspectRatio(1f)
                .graphicsLayer { scaleX = soft; scaleY = soft }
                .shadow(elevation, RoundedCornerShape(26.dp))
                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(26.dp)),
            size = 1024, shape = RoundedCornerShape(26.dp),
        )
        NowPlayingStyle.POSTER -> PosterHero(track)
        NowPlayingStyle.ORBIT -> OrbitHero(track, st, vm)
        NowPlayingStyle.MONO -> {
            val dim by animateFloatAsState(if (playing) 1f else 0.55f, tween(500), label = "monoDim")
            Artwork(
                track,
                Modifier.aspectRatio(1f)
                    .graphicsLayer { scaleX = soft; scaleY = soft; alpha = dim }
                    .shadow(elevation * 0.5f, RoundedCornerShape(6.dp)),
                size = 1024, shape = RoundedCornerShape(6.dp), colorFilter = grayscale,
            )
        }
    }
}

/** Magazine-style: small cover, then the title set huge. */
@Composable
private fun PosterHero(track: Track) {
    val content = LocalContentColor.current
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
        Row(verticalAlignment = Alignment.Bottom) {
            Artwork(
                track, Modifier.size(150.dp).shadow(22.dp, RoundedCornerShape(18.dp)),
                size = 512, shape = RoundedCornerShape(18.dp),
            )
            Spacer(Modifier.weight(1f))
            TrackMenu(track, tint = content.copy(alpha = 0.75f))
        }
        Spacer(Modifier.height(22.dp))
        Text(
            track.title, color = content, maxLines = 3, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold, lineHeight = 40.sp, letterSpacing = (-1).sp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            track.artist, color = content.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Normal),
        )
        Text(
            track.album, color = content.copy(alpha = 0.45f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Round cover inside a progress ring — drag around the ring to seek. */
@Composable
private fun OrbitHero(track: Track, st: PlayerState, vm: MainViewModel) {
    val content = LocalContentColor.current
    val pos by vm.player.position.collectAsState()
    val dur = st.duration
    var dragFrac by remember { mutableStateOf<Float?>(null) }
    val live = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
    val frac = dragFrac ?: live
    val glow by animateFloatAsState(if (st.playing) 1f else 0.4f, tween(600), label = "orbitGlow")
    val cover by animateFloatAsState(if (st.playing) 1f else 0.9f, spring(0.55f, 200f), label = "orbitCover")

    fun angleFrac(o: Offset, size: IntSize): Float {
        var a = atan2(o.y - size.height / 2f, o.x - size.width / 2f) + (PI / 2).toFloat()
        if (a < 0) a += (2 * PI).toFloat()
        return (a / (2 * PI)).toFloat()
    }

    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val ring = min(maxWidth.value, (maxHeight - 34.dp).value).dp
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(ring)
                    .pointerInput(dur) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val c = Offset(size.width / 2f, size.height / 2f)
                            val outer = size.width / 2f
                            // Only the ring seeks; the middle still lets you swipe the player down.
                            if (hypot(down.position.x - c.x, down.position.y - c.y) < outer * 0.74f) return@awaitEachGesture
                            down.consume()
                            dragFrac = angleFrac(down.position, size)
                            drag(down.id) { change ->
                                change.consume()
                                dragFrac = angleFrac(change.position, size)
                            }
                            dragFrac?.let { vm.player.seekTo((it * dur).toLong()) }
                            dragFrac = null
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 6.dp.toPx()
                    val inset = 14.dp.toPx()
                    val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                    val topLeft = Offset(inset, inset)
                    drawArc(content.copy(alpha = 0.16f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                    drawArc(
                        content.copy(alpha = 0.25f * glow), -90f, 360f * frac, false,
                        topLeft, arcSize, style = Stroke(stroke * 3.2f, cap = StrokeCap.Round),
                    )
                    drawArc(content, -90f, 360f * frac, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    val r = arcSize.width / 2
                    val a = (-90f + 360f * frac) * (PI / 180f).toFloat()
                    val knob = Offset(size.width / 2 + r * cos(a), size.height / 2 + r * sin(a))
                    drawCircle(content, (if (dragFrac != null) 11.dp else 8.dp).toPx(), knob)
                }
                Artwork(
                    track, Modifier.fillMaxSize(0.76f).graphicsLayer { scaleX = cover; scaleY = cover }.shadow(26.dp, CircleShape),
                    size = 768, shape = CircleShape,
                )
            }
            Spacer(Modifier.height(10.dp))
            val shown = (frac * dur).toLong()
            Text(
                "${formatTime(shown)}  /  ${formatTime(dur)}",
                style = MaterialTheme.typography.labelLarge, color = content.copy(alpha = 0.65f),
            )
        }
    }
}

@Composable
private fun PlayButton(playing: Boolean, buffering: Boolean, spec: Spec, content: Color, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val pulse = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val ring = if (spec.lightBg) cs.primary else content
    Box(
        Modifier.size(96.dp).drawBehind {
            val p = pulse.value
            if (p < 1f) drawCircle(ring.copy(alpha = 0.35f * (1f - p)), size.minDimension / 2 * (0.62f + 0.4f * p))
        },
        contentAlignment = Alignment.Center,
    ) {
        val click = {
            scope.launch { pulse.snapTo(0f); pulse.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
            onClick()
        }
        if (buffering) {
            CircularProgressIndicator(
                Modifier.size(90.dp), color = if (spec.lightBg) cs.primary else content,
                strokeWidth = 2.5.dp, trackColor = Color.Transparent,
            )
        }
        if (spec.filledPlay) {
            val bg = if (spec.lightBg) cs.primary else Color.White
            val fg = if (spec.lightBg) cs.onPrimary else Color(0xFF111111)
            Box(
                Modifier.size(76.dp).pressScale(0.88f, click)
                    .shadow(14.dp, CircleShape, spotColor = if (spec.lightBg) cs.primary else Color.Black).clip(CircleShape).background(bg),
                contentAlignment = Alignment.Center,
            ) { PlayPauseIcon(playing, fg, 34.dp) }
        } else {
            Box(Modifier.size(84.dp).clip(CircleShape).pressScale(0.82f, click), contentAlignment = Alignment.Center) {
                PlayPauseIcon(playing, content, 50.dp)
            }
        }
    }
}

@Composable
private fun SeekSection(vm: MainViewModel, st: PlayerState, color: Color, content: Color, wavy: Boolean) {
    val pos by vm.player.position.collectAsState()
    var drag by remember { mutableStateOf<Float?>(null) }
    val dur = st.duration
    val shown = drag?.let { (it * dur).toLong() } ?: pos
    val value = if (dur > 0) pos.toFloat() / dur else 0f
    if (wavy) {
        WavySlider(value, color, st.playing, onDrag = { drag = it }) { f -> vm.player.seekTo((f * dur).toLong()) }
    } else {
        FlexSlider(value, color, onDrag = { drag = it }) { f -> vm.player.seekTo((f * dur).toLong()) }
    }
    Row(Modifier.fillMaxWidth()) {
        Text(formatTime(shown), style = MaterialTheme.typography.labelSmall, color = content.copy(alpha = 0.55f))
        Spacer(Modifier.weight(1f))
        Text("-" + formatTime(dur - shown), style = MaterialTheme.typography.labelSmall, color = content.copy(alpha = 0.55f))
    }
}

/** Squiggly progress line that ripples while music plays and flattens when paused or dragged. */
@Composable
private fun WavySlider(value: Float, color: Color, playing: Boolean, onDrag: (Float?) -> Unit, onChange: (Float) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var local by remember { mutableFloatStateOf(value) }
    val shown = if (dragging) local else value.coerceIn(0f, 1f)
    val amp by animateFloatAsState(if (playing && !dragging) 1f else 0f, tween(500), label = "waveAmp")
    val phase by rememberInfiniteTransition(label = "wave").animateFloat(
        0f, (2 * PI).toFloat(), infiniteRepeatable(tween(1500, easing = LinearEasing)), label = "wavePhase",
    )
    val thumbH by animateDpAsState(if (dragging) 26.dp else 18.dp, label = "waveThumb")
    val onDragCb by rememberUpdatedState(onDrag)
    val onChangeCb by rememberUpdatedState(onChange)
    Canvas(
        Modifier.fillMaxWidth().height(32.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o ->
                        dragging = true
                        local = (o.x / size.width).coerceIn(0f, 1f)
                        onDragCb(local)
                    },
                    onDragEnd = { onChangeCb(local); dragging = false; onDragCb(null) },
                    onDragCancel = { dragging = false; onDragCb(null) },
                ) { c, _ ->
                    c.consume()
                    local = (c.position.x / size.width).coerceIn(0f, 1f)
                    onDragCb(local)
                }
            }
            .pointerInput(Unit) { detectTapGestures { o -> onChangeCb((o.x / size.width).coerceIn(0f, 1f)) } }
    ) {
        val cy = size.height / 2
        val x = shown * size.width
        val stroke = 4.dp.toPx()
        val a = 3.5.dp.toPx() * amp
        val wavelength = 30.dp.toPx()
        val path = Path().apply {
            moveTo(0f, cy + a * sin(-phase))
            var px = 0f
            while (px < x) {
                px = min(px + 2f, x)
                lineTo(px, cy + a * sin(px / wavelength * 2f * PI.toFloat() - phase))
            }
        }
        drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round))
        val gap = 7.dp.toPx()
        if (x + gap < size.width) drawLine(color.copy(alpha = 0.22f), Offset(x + gap, cy), Offset(size.width, cy), stroke, StrokeCap.Round)
        val tw = 5.dp.toPx()
        val th = thumbH.toPx()
        drawRoundRect(color, Offset(x - tw / 2, cy - th / 2), Size(tw, th), CornerRadius(tw / 2))
    }
}

@Composable
private fun ControlButton(icon: ImageVector, size: Dp, tint: Color, onClick: () -> Unit) {
    Box(Modifier.size(size + 26.dp).clip(CircleShape).pressScale(0.78f, onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size))
    }
}

@Composable
private fun ToggleIcon(icon: ImageVector, active: Boolean, activeColor: Color, content: Color, onClick: () -> Unit) {
    val bg by animateColorAsState(if (active) content.copy(alpha = 0.13f) else Color.Transparent, label = "toggleBg")
    val tint by animateColorAsState(if (active) activeColor else content.copy(alpha = 0.55f), label = "toggleTint")
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(bg).pressScale(0.85f, onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp)) }
}

@Composable
private fun VolumeBar(color: Color) {
    val context = LocalContext.current
    val am = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val max = remember { am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var vol by remember { mutableIntStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(600)
            vol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        }
    }
    fun set(f: Float) {
        val v = (f * max).roundToInt()
        if (v != vol) {
            vol = v
            am.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.AutoMirrored.Rounded.VolumeDown, null, tint = color.copy(alpha = 0.55f), modifier = Modifier.size(18.dp))
        FlexSlider(
            vol.toFloat() / max, color, Modifier.weight(1f).padding(horizontal = 10.dp),
            onDrag = { f -> if (f != null) set(f) },
        ) { f -> set(f) }
        Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, tint = color.copy(alpha = 0.55f), modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun QueueView(st: PlayerState, byId: Map<String, Track>, vm: MainViewModel, content: Color) {
    Column(Modifier.fillMaxSize()) {
        Text("Up Next", style = MaterialTheme.typography.titleMedium, color = content, modifier = Modifier.padding(bottom = 8.dp))
        if (st.upNext.isEmpty()) {
            Text("Nothing queued after this song.", style = MaterialTheme.typography.bodyMedium, color = content.copy(alpha = 0.6f))
        }
        LazyColumn(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))) {
            items(st.upNext, key = { it.first }) { (index, id) ->
                val t = byId[id] ?: return@items
                Row(
                    Modifier.animateItem().fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { vm.player.skipTo(index) }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(t, Modifier.size(46.dp), shape = RoundedCornerShape(10.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = content, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                        Text(t.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = content.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
