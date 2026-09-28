package com.muse.player.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.muse.player.data.Track

// ---------- shared plumbing ----------

class Actions(
    val play: (List<Track>, Int) -> Unit,
    val shuffle: (List<Track>) -> Unit,
    val playNext: (Track) -> Unit,
    val enqueue: (Track) -> Unit,
    val openAlbum: (String) -> Unit,
    val openArtist: (String) -> Unit,
    val addToPlaylist: (List<Track>) -> Unit = {},
    val download: (List<Track>) -> Unit = {},
    val share: (Track) -> Unit = {},
)

val LocalActions = staticCompositionLocalOf<Actions> { error("Actions not provided") }

data class NowPlayingInfo(val id: String? = null, val playing: Boolean = false)

val LocalNowPlaying = compositionLocalOf { NowPlayingInfo() }

fun formatTime(ms: Long): String {
    val t = ms.coerceAtLeast(0L) / 1000
    val h = t / 3600
    val m = (t % 3600) / 60
    val s = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun Color.shade(f: Float) = lerp(this, Color.Black, f)

fun Modifier.pressScale(scaleDown: Float = 0.94f, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) scaleDown else 1f,
        spring(dampingRatio = 0.55f, stiffness = 600f), label = "press",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, onClick = onClick)
}

/** Lets a full-span header inside a padded grid ignore the grid's horizontal padding. */
fun Modifier.bleed(horizontal: Dp) = layout { measurable, constraints ->
    val px = horizontal.roundToPx()
    if (!constraints.hasBoundedWidth) {
        val p = measurable.measure(constraints)
        return@layout layout(p.width, p.height) { p.place(0, 0) }
    }
    val p = measurable.measure(
        constraints.copy(minWidth = constraints.minWidth + 2 * px, maxWidth = constraints.maxWidth + 2 * px)
    )
    layout(constraints.maxWidth, p.height) { p.place(-px, 0) }
}

// ---------- headers & buttons ----------

@Composable
fun LargeTitle(title: String, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        actions()
    }
}

@Composable
fun HeaderButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.padding(horizontal = 4.dp).size(36.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .pressScale(0.86f, onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
    }
}

@Composable
fun BackRow(label: String = "Back", onBack: () -> Unit) {
    Row(
        Modifier.padding(start = 6.dp, top = 6.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onBack)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.ArrowBackIosNew, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun PillButton(icon: ImageVector, text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.pressScale(onClick = onClick).height(46.dp).clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
    }
}

@Composable
fun PlayShuffleRow(tracks: List<Track>) {
    val a = LocalActions.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PillButton(MuseIcons.Play, "Play", Modifier.weight(1f)) { a.play(tracks, 0) }
        PillButton(Icons.Rounded.Shuffle, "Shuffle", Modifier.weight(1f)) { a.shuffle(tracks) }
    }
}

// ---------- animated bits ----------

@Composable
fun PlayingBars(playing: Boolean, color: Color, modifier: Modifier = Modifier.size(16.dp)) {
    val t = rememberInfiniteTransition(label = "bars")
    val a by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(0.9f, 0.3f, infiniteRepeatable(tween(560), RepeatMode.Reverse), label = "b")
    val c by t.animateFloat(0.4f, 0.95f, infiniteRepeatable(tween(350), RepeatMode.Reverse), label = "c")
    Canvas(modifier) {
        val w = size.width / 5f
        listOf(a, b, c).forEachIndexed { i, v ->
            val h = size.height * (if (playing) v else 0.3f)
            drawRoundRect(color, Offset(i * 2 * w, size.height - h), Size(w, h), CornerRadius(w / 2, w / 2))
        }
    }
}

// ---------- settings widgets ----------

@Composable
fun Segmented(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val dark = cs.background.luminance() < 0.5f
    BoxWithConstraints(
        modifier.fillMaxWidth().height(38.dp).clip(RoundedCornerShape(11.dp))
            .background(cs.surfaceContainerHighest).padding(3.dp)
    ) {
        val w = maxWidth / options.size
        val x by animateDpAsState(w * selected, spring(dampingRatio = 0.8f, stiffness = 500f), label = "seg")
        Box(
            Modifier.offset(x = x).width(w).fillMaxHeight()
                .shadow(2.dp, RoundedCornerShape(9.dp))
                .clip(RoundedCornerShape(9.dp))
                .background(if (dark) Color(0xFF4A4A50) else Color.White)
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, o ->
                Box(
                    Modifier.weight(1f).fillMaxHeight()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        o, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                        color = if (i == selected) cs.onSurface else cs.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsGroup(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (title != null) {
            Text(
                title.uppercase(), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, bottom = 6.dp),
            )
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer),
            content = content,
        )
    }
}

@Composable
fun SettingRow(
    title: String,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(iconTint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = if (iconTint.luminance() > 0.6f) Color.Black else Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing()
    }
}

@Composable
fun MuseSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked, onChange,
        colors = SwitchDefaults.colors(
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            uncheckedBorderColor = Color.Transparent,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            uncheckedThumbColor = Color.White,
        ),
    )
}

@Composable
fun RowDivider(start: Dp = 56.dp) {
    HorizontalDivider(Modifier.padding(start = start), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

/** Thin Apple-style bar that thickens while dragged. */
@Composable
fun FlexSlider(
    value: Float,
    color: Color,
    modifier: Modifier = Modifier,
    onDrag: (Float?) -> Unit = {},
    onChange: (Float) -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var local by remember { mutableFloatStateOf(value) }
    val shown = if (dragging) local else value.coerceIn(0f, 1f)
    val height by animateDpAsState(if (dragging) 12.dp else 6.dp, label = "flexH")
    val onDragCb by rememberUpdatedState(onDrag)
    val onChangeCb by rememberUpdatedState(onChange)
    Box(
        modifier.fillMaxWidth().height(28.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o ->
                        dragging = true
                        local = (o.x / size.width).coerceIn(0f, 1f)
                        onDragCb(local)
                    },
                    onDragEnd = { onChangeCb(local); dragging = false; onDragCb(null) },
                    onDragCancel = { dragging = false; onDragCb(null) },
                ) { change, _ ->
                    change.consume()
                    local = (change.position.x / size.width).coerceIn(0f, 1f)
                    onDragCb(local)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { o -> onChangeCb((o.x / size.width).coerceIn(0f, 1f)) }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(height).clip(CircleShape).background(color.copy(alpha = 0.22f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(shown).background(color.copy(alpha = if (dragging) 1f else 0.85f)))
        }
    }
}

// ---------- track rows ----------

@Composable
fun TrackMenuDropdown(track: Track, open: Boolean, onDismiss: () -> Unit) {
    val a = LocalActions.current
    DropdownMenu(open, onDismissRequest = onDismiss, shape = RoundedCornerShape(16.dp)) {
        DropdownMenuItem(
            text = { Text("Play next") },
            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null) },
            onClick = { a.playNext(track); onDismiss() },
        )
        DropdownMenuItem(
            text = { Text("Add to queue") },
            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) },
            onClick = { a.enqueue(track); onDismiss() },
        )
        DropdownMenuItem(
            text = { Text("Add to playlist") },
            leadingIcon = { Icon(Icons.Rounded.LibraryAdd, null) },
            onClick = { a.addToPlaylist(listOf(track)); onDismiss() },
        )
        if (track.uri.scheme == com.muse.player.online.OnlineCatalog.SCHEME) {
            DropdownMenuItem(
                text = { Text("Download") },
                leadingIcon = { Icon(Icons.Rounded.Download, null) },
                onClick = { a.download(listOf(track)); onDismiss() },
            )
        }
        if (track.id.startsWith("yt:") || track.id.startsWith("web:")) {
            DropdownMenuItem(
                text = { Text("Share") },
                leadingIcon = { Icon(Icons.Rounded.IosShare, null) },
                onClick = { a.share(track); onDismiss() },
            )
        }
        // Online songs have no local album/artist page to jump to.
        if (!track.id.startsWith("yt:") && !track.id.startsWith("web:")) {
            DropdownMenuItem(
                text = { Text("Go to album") },
                leadingIcon = { Icon(Icons.Rounded.Album, null) },
                onClick = { a.openAlbum(track.albumKey); onDismiss() },
            )
            DropdownMenuItem(
                text = { Text("Go to artist") },
                leadingIcon = { Icon(Icons.Rounded.Person, null) },
                onClick = { a.openArtist(track.artist); onDismiss() },
            )
        }
    }
}

@Composable
fun TrackMenu(track: Track, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Rounded.MoreHoriz, "More", tint = tint) }
        TrackMenuDropdown(track, open) { open = false }
    }
}

/** Tap to play, long-press for the track menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.trackGestures(onClick: () -> Unit, onLongClick: () -> Unit): Modifier {
    val haptics = LocalHapticFeedback.current
    return combinedClickable(
        onClick = onClick,
        onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onLongClick() },
    )
}

@Composable
fun TrackRow(track: Track, onClick: () -> Unit, number: Int? = null, trailing: @Composable () -> Unit = {}) {
    val np = LocalNowPlaying.current
    val cs = MaterialTheme.colorScheme
    val current = np.id == track.id
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().trackGestures(onClick) { menu = true }
            .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (number == null) {
            Box(Modifier.size(48.dp)) {
                Artwork(track, Modifier.fillMaxSize(), shape = RoundedCornerShape(10.dp))
                if (current) {
                    Box(
                        Modifier.matchParentSize().clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) { PlayingBars(np.playing, Color.White) }
                }
            }
        } else {
            Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) {
                if (current) PlayingBars(np.playing, cs.primary)
                else Text("$number", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = if (current) cs.primary else cs.onBackground,
            )
            Text(
                if (number == null) track.artist else formatTime(track.durationMs),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
            )
            TrackMenuDropdown(track, menu) { menu = false }
        }
        trailing()
    }
}

@Composable
fun TrackTile(track: Track, onClick: () -> Unit) {
    val np = LocalNowPlaying.current
    val cs = MaterialTheme.colorScheme
    val current = np.id == track.id
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.padding(6.dp).clip(RoundedCornerShape(14.dp)).trackGestures(onClick) { menu = true }) {
        Box {
            TrackMenuDropdown(track, menu) { menu = false }
            Artwork(track, Modifier.fillMaxWidth().aspectRatio(1f), size = 384, shape = RoundedCornerShape(12.dp))
            if (current) {
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(8.dp).size(28.dp).clip(CircleShape).background(cs.primary),
                    contentAlignment = Alignment.Center,
                ) { PlayingBars(np.playing, cs.onPrimary, Modifier.size(13.dp)) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            track.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleSmall, color = if (current) cs.primary else cs.onBackground,
        )
        Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    }
}
