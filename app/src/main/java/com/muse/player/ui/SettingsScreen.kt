package com.muse.player.ui

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.muse.player.Graph
import com.muse.player.data.OnlineArt
import com.muse.player.online.Downloader
import com.muse.player.online.Ytdl
import android.widget.Toast
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.muse.player.data.*
import com.muse.player.ui.theme.AccentPresets
import com.muse.player.ui.theme.ThemePreset
import com.muse.player.ui.theme.ThemePresets
import com.muse.player.ui.theme.museColorScheme

private fun folderLabel(uri: String): String = runCatching {
    val id = DocumentsContract.getTreeDocumentId(Uri.parse(uri))
    val volume = id.substringBefore(':')
    val path = id.substringAfter(':', "").ifBlank { "/" }
    (if (volume == "primary") "Internal" else "SD card") + " · " + path
}.getOrDefault(uri)

@Composable
fun SettingsScreen(
    s: AppSettings,
    tracks: List<Track>,
    artists: List<Artist>,
    scanning: Boolean,
    padding: PaddingValues,
    onRescan: () -> Unit,
    onOpenEq: () -> Unit,
) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var showPicker by remember { mutableStateOf(false) }
    var showDisclaimer by remember { mutableStateOf(false) }
    var showDeveloper by remember { mutableStateOf(false) }
    val bulk by ArtworkLoader.bulkProgress.collectAsState()
    var cacheSize by remember { mutableLongStateOf(0L) }
    LaunchedEffect(bulk) { cacheSize = withContext(Dispatchers.IO) { OnlineArt.cacheSizeBytes() } }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            Graph.settings.update { it[Keys.folders] = (it[Keys.folders] ?: emptySet()) + uri.toString() }
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        item { LargeTitle("Settings") }

        // ---------------- appearance
        item {
            SettingsGroup("Theme presets") {
                LazyRow(
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(ThemePresets) { p ->
                        PresetCard(p, selected = s.accent == p.accent && s.themeMode == p.mode && s.amoled == p.amoled) {
                            Graph.settings.update {
                                it[Keys.themeMode] = p.mode.name
                                it[Keys.amoled] = p.amoled
                                it[Keys.accent] = p.accent
                            }
                        }
                    }
                }
            }
        }
        item {
            SettingsGroup("Appearance") {
                Column(Modifier.padding(14.dp)) {
                    Text("Mode", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Segmented(listOf("System", "Light", "Dark"), s.themeMode.ordinal) {
                        Graph.settings.put(Keys.themeMode, ThemeMode.entries[it].name)
                    }
                }
                RowDivider(14.dp)
                SettingRow("AMOLED black", Icons.Rounded.DarkMode, Color(0xFF1C1C1E), "Pure black backgrounds in dark mode") {
                    MuseSwitch(s.amoled) { Graph.settings.put(Keys.amoled, it) }
                }
                RowDivider()
                SettingRow("Artwork colors", Icons.Rounded.AutoAwesome, Color(0xFFFF9F0A), "Tint player from album art") {
                    MuseSwitch(s.adaptiveColor) { Graph.settings.put(Keys.adaptive, it) }
                }
                RowDivider(14.dp)
                Column(Modifier.padding(14.dp)) {
                    Text("Accent color", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    val swatches = AccentPresets
                    val isCustom = s.accent !in swatches
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        (swatches.map<Int, Int?> { it } + listOf(null)).chunked(7).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { c ->
                                    if (c == null) {
                                        Swatch(
                                            color = if (isCustom) Color(s.accent) else cs.surfaceContainerHighest,
                                            selected = isCustom, rainbow = !isCustom,
                                        ) { showPicker = true }
                                    } else {
                                        Swatch(Color(c), selected = s.accent == c) { Graph.settings.put(Keys.accent, c) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---------------- now playing
        item {
            SettingsGroup("Now playing style") {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    NowPlayingStyle.entries.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { st ->
                                StylePreview(st, s.nowPlayingStyle == st, Modifier.weight(1f)) {
                                    Graph.settings.put(Keys.npStyle, st.name)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---------------- library
        item {
            SettingsGroup("Library") {
                Column(Modifier.padding(14.dp)) {
                    Text("Tracks layout", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Segmented(listOf("List", "Tiles"), s.trackLayout.ordinal) {
                        Graph.settings.put(Keys.layout, TrackLayout.entries[it].name)
                    }
                    if (s.trackLayout == TrackLayout.GRID) {
                        Spacer(Modifier.height(12.dp))
                        Text("Tiles per row", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Segmented(listOf("2", "3", "4"), (s.gridColumns - 2).coerceIn(0, 2)) {
                            Graph.settings.put(Keys.gridColumns, it + 2)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Skip tracks shorter than", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    val durations = listOf(0, 15, 30, 60)
                    Segmented(listOf("Off", "15s", "30s", "60s"), durations.indexOf(s.minDurationSec).coerceAtLeast(0)) {
                        Graph.settings.put(Keys.minDuration, durations[it])
                    }
                }
                RowDivider(14.dp)
                SettingRow("Scan device storage", Icons.Rounded.PhoneAndroid, Color(0xFF30D158), "Find music automatically") {
                    MuseSwitch(s.autoScan) { Graph.settings.put(Keys.autoScan, it) }
                }
                RowDivider()
                SettingRow(
                    "Rescan library", Icons.Rounded.Refresh, Color(0xFF0A84FF),
                    if (scanning) "Scanning…" else "${tracks.size} songs", onClick = onRescan,
                ) {
                    if (scanning) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = cs.primary)
                }
            }
        }
        item {
            SettingsGroup("Music folders") {
                if (s.folders.isEmpty()) {
                    Text(
                        "Add folders that aren't picked up automatically — e.g. on an SD card or in hidden directories.",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                }
                s.folders.sorted().forEach { f ->
                    SettingRow(folderLabel(f), Icons.Rounded.Folder, Color(0xFF64D2FF)) {
                        IconButton(onClick = {
                            runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(f), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                            Graph.settings.update { it[Keys.folders] = (it[Keys.folders] ?: emptySet()) - f }
                        }) { Icon(Icons.Rounded.RemoveCircleOutline, "Remove", tint = Color(0xFFFF453A)) }
                    }
                    RowDivider()
                }
                SettingRow("Add folder", Icons.Rounded.CreateNewFolder, cs.primary, onClick = { folderPicker.launch(null) })
            }
        }

        // ---------------- downloads
        item {
            val ytVersion by Ytdl.version.collectAsState()
            var updating by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            SettingsGroup("Downloads") {
                Column(Modifier.padding(14.dp)) {
                    Text("Audio format", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Segmented(DlFormat.entries.map { it.label }, s.dlFormat.ordinal) {
                        Graph.settings.put(Keys.dlFormat, DlFormat.entries[it].name)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        when (s.dlFormat) {
                            DlFormat.MP3 -> "Plays everywhere. Converted from the best available audio."
                            DlFormat.M4A -> "AAC — usually saved without re-encoding. Great quality, small files."
                            DlFormat.OPUS -> "YouTube's original audio — best quality per MB, no re-encoding."
                        },
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    )
                    if (s.dlFormat == DlFormat.MP3) {
                        Spacer(Modifier.height(12.dp))
                        Text("MP3 bitrate", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        val rates = listOf(128, 192, 256, 320)
                        Segmented(rates.map { "$it" }, rates.indexOf(s.mp3Kbps).coerceAtLeast(0)) {
                            Graph.settings.put(Keys.mp3Kbps, rates[it])
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Simultaneous downloads", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Segmented(listOf("1", "2", "3"), (s.parallel - 1).coerceIn(0, 2)) {
                        Graph.settings.put(Keys.parallel, it + 1)
                    }
                }
                RowDivider(14.dp)
                SettingRow("Embed cover & tags", Icons.Rounded.Sell, Color(0xFFFF9F0A), "Title, artist, album and artwork in the file") {
                    MuseSwitch(s.embedMeta) { Graph.settings.put(Keys.embedMeta, it) }
                }
                RowDivider()
                SettingRow("Square covers", Icons.Rounded.CropSquare, Color(0xFF30D158), "Crop video thumbnails to album-art shape") {
                    MuseSwitch(s.cropArt) { Graph.settings.put(Keys.cropArt, it) }
                }
                RowDivider()
                SettingRow("Save location", Icons.Rounded.Folder, Color(0xFF64D2FF), "Music/${Downloader.FOLDER}")
                RowDivider()
                SettingRow(
                    "Open song links in Muse Flow", Icons.Rounded.Link, Color(0xFF0A84FF),
                    "Shared Muse Flow & YouTube links play here",
                    onClick = {
                        val pkg = Uri.parse("package:" + context.packageName)
                        val intent = if (android.os.Build.VERSION.SDK_INT >= 31)
                            Intent(android.provider.Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, pkg)
                        else Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
                        runCatching { context.startActivity(intent) }
                    },
                ) { Icon(Icons.Rounded.ChevronRight, null, tint = cs.onSurfaceVariant) }
                RowDivider()
                SettingRow(
                    "Update yt-dlp", Icons.Rounded.SystemUpdate, Color(0xFF8E8E93),
                    if (updating) "Checking…" else "Version ${ytVersion ?: "…"} · updates itself every few days",
                    onClick = if (updating) null else ({
                        updating = true
                        scope.launch {
                            val msg = runCatching { Ytdl.update() }.getOrElse { "Update failed: ${it.message?.take(80)}" }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            updating = false
                        }
                    }),
                ) {
                    if (updating) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = cs.primary)
                }
            }
        }

        // ---------------- artwork
        item {
            SettingsGroup("Artwork") {
                SettingRow("Download missing artwork", Icons.Rounded.CloudDownload, Color(0xFF5E5CE6), "Album covers from iTunes & Deezer") {
                    MuseSwitch(s.fetchArt) { Graph.settings.put(Keys.fetchArt, it) }
                }
                RowDivider()
                SettingRow("Artist photos", Icons.Rounded.AccountCircle, Color(0xFFBF5AF2), "Show real photos for artists") {
                    MuseSwitch(s.artistImages) { Graph.settings.put(Keys.artistImages, it) }
                }
                RowDivider()
                SettingRow("Wi-Fi only", Icons.Rounded.Wifi, Color(0xFF0A84FF), "Don't use mobile data for artwork") {
                    MuseSwitch(s.wifiOnly) { Graph.settings.put(Keys.wifiOnly, it) }
                }
                RowDivider()
                val b = bulk
                SettingRow(
                    "Download all now", Icons.Rounded.Download, Color(0xFF30D158),
                    if (b != null) "Fetching ${b.first} of ${b.second}…" else "Grab covers for the whole library",
                    onClick = if (b == null && s.fetchArt) ({ ArtworkLoader.downloadAll(context, tracks, artists) }) else null,
                ) {
                    if (b != null) CircularProgressIndicator(
                        progress = { if (b.second == 0) 0f else b.first.toFloat() / b.second },
                        modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp, color = cs.primary,
                    )
                }
                RowDivider()
                SettingRow(
                    "Clear downloaded artwork", Icons.Rounded.DeleteOutline, Color(0xFF8E8E93),
                    "%.1f MB on device".format(cacheSize / 1_048_576f),
                    onClick = { ArtworkLoader.clearDownloaded(); cacheSize = 0L },
                )
            }
        }

        // ---------------- audio
        item {
            SettingsGroup("Audio") {
                SettingRow(
                    "Equalizer", Icons.Rounded.GraphicEq, Color(0xFFFF375F),
                    if (s.eqEnabled) "On · ${s.eqPreset}" else "Off", onClick = onOpenEq,
                ) { Icon(Icons.Rounded.ChevronRight, null, tint = cs.onSurfaceVariant) }
            }
        }
        // ---------------- about
        item {
            SettingsGroup("About") {
                SettingRow(
                    "Disclaimer", Icons.Rounded.Gavel, Color(0xFFFF9F0A),
                    "Why Muse Flow exists & fair use", onClick = { showDisclaimer = true },
                ) { Icon(Icons.Rounded.ChevronRight, null, tint = cs.onSurfaceVariant) }
                RowDivider()
                SettingRow(
                    "From the Developer", Icons.Rounded.Code, Color(0xFF5E5CE6),
                    "Mohammad Affan Yasir · Visualter", onClick = { showDeveloper = true },
                ) { Icon(Icons.Rounded.ChevronRight, null, tint = cs.onSurfaceVariant) }
            }
        }
        item { BuiltWithLove() }
    }

    if (showDisclaimer) DisclaimerDialog { showDisclaimer = false }
    if (showDeveloper) DeveloperDialog { showDeveloper = false }

    if (showPicker) {
        ColorPickerDialog(Color(s.accent), onDismiss = { showPicker = false }) {
            Graph.settings.put(Keys.accent, it.toArgb())
            showPicker = false
        }
    }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, rainbow: Boolean = false, onClick: () -> Unit) {
    val ring by animateFloatAsState(if (selected) 1f else 0f, label = "ring")
    Box(
        Modifier.size(34.dp).pressScale(0.85f, onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            if (ring > 0f) drawCircle(color, r, style = Stroke(2.dp.toPx() * ring))
            if (rainbow) drawCircle(
                Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)),
                r - 5.dp.toPx(),
            ) else drawCircle(color, r - 5.dp.toPx() * (0.6f + 0.4f * ring))
        }
        if (rainbow) Icon(Icons.Rounded.Add, "Custom color", tint = Color.White, modifier = Modifier.size(16.dp))
        else if (selected) Icon(
            Icons.Rounded.Check, null, modifier = Modifier.size(16.dp),
            tint = if (color.luminance() > 0.6f) Color.Black else Color.White,
        )
    }
}

@Composable
private fun PresetCard(p: ThemePreset, selected: Boolean, onClick: () -> Unit) {
    val dark = when (p.mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> MaterialTheme.colorScheme.background.luminance() < 0.5f
    }
    val scheme = remember(p, dark) { museColorScheme(dark, p.amoled, Color(p.accent)) }
    val border by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, label = "presetBorder")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier.pressScale(0.93f, onClick).size(78.dp, 110.dp)
                .border(2.5.dp, border, RoundedCornerShape(16.dp)).padding(4.dp)
                .clip(RoundedCornerShape(12.dp)).background(scheme.background).padding(8.dp),
        ) {
            Box(Modifier.fillMaxWidth(0.7f).height(7.dp).clip(CircleShape).background(scheme.onBackground.copy(alpha = 0.85f)))
            Spacer(Modifier.height(8.dp))
            repeat(3) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(scheme.primary.copy(alpha = 0.8f - it * 0.2f)))
                    Spacer(Modifier.width(4.dp))
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(scheme.onSurfaceVariant.copy(alpha = 0.5f)))
                }
                Spacer(Modifier.height(5.dp))
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(5.dp)).background(scheme.surfaceContainerHigh)) {
                Box(Modifier.align(Alignment.CenterEnd).padding(end = 4.dp).size(6.dp).clip(CircleShape).background(scheme.primary))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(p.name, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun StylePreview(style: NowPlayingStyle, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val accent = cs.primary
    val border by animateColorAsState(if (selected) accent else Color.Transparent, label = "styleBorder")
    val bg: Brush = when (style) {
        NowPlayingStyle.CLASSIC -> Brush.verticalGradient(listOf(accent.shade(0.25f), accent.shade(0.8f)))
        NowPlayingStyle.IMMERSIVE -> Brush.verticalGradient(listOf(accent.shade(0.1f), accent.shade(0.6f), Color.Black))
        NowPlayingStyle.MINIMAL -> Brush.verticalGradient(listOf(cs.background, cs.background))
        NowPlayingStyle.CARD -> Brush.linearGradient(listOf(accent.shade(0.35f), accent.shade(0.75f), Color(0xFF101014)))
        NowPlayingStyle.GLASS -> Brush.radialGradient(listOf(accent.copy(alpha = 0.9f), accent.shade(0.5f), Color(0xFF0C0C10)))
        NowPlayingStyle.POSTER -> Brush.verticalGradient(listOf(accent.shade(0.55f), Color(0xFF0B0B0E)))
        NowPlayingStyle.ORBIT -> Brush.verticalGradient(listOf(accent.shade(0.45f), accent.shade(0.85f)))
        NowPlayingStyle.MONO -> Brush.verticalGradient(listOf(Color(0xFF111111), Color.Black))
    }
    val fg = if (style == NowPlayingStyle.MINIMAL) cs.onBackground else Color.White
    val art = when (style) {
        NowPlayingStyle.MINIMAL -> accent.copy(alpha = 0.35f)
        NowPlayingStyle.MONO -> Color(0xFF8E8E93)
        else -> Color.White.copy(alpha = 0.35f)
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.pressScale(0.93f, onClick).fillMaxWidth().aspectRatio(0.56f)
                .border(2.5.dp, border, RoundedCornerShape(14.dp)).padding(3.dp)
                .clip(RoundedCornerShape(11.dp)).background(bg),
        ) {
            when (style) {
                NowPlayingStyle.CARD -> Box(Modifier.fillMaxSize().padding(4.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.12f)))
                NowPlayingStyle.GLASS -> Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.36f).padding(4.dp)
                        .clip(RoundedCornerShape(7.dp)).background(Color.White.copy(alpha = 0.18f))
                )
                else -> {}
            }
            Column(
                Modifier.fillMaxSize().padding(
                    when (style) {
                        NowPlayingStyle.IMMERSIVE -> 0.dp
                        NowPlayingStyle.CARD -> 9.dp
                        else -> 7.dp
                    }
                ),
                horizontalAlignment = if (style == NowPlayingStyle.POSTER) Alignment.Start else Alignment.CenterHorizontally,
            ) {
                when (style) {
                    NowPlayingStyle.IMMERSIVE -> Box(
                        Modifier.fillMaxWidth().aspectRatio(1f)
                            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.Transparent)))
                    )
                    NowPlayingStyle.POSTER -> {
                        Spacer(Modifier.height(16.dp))
                        Box(Modifier.size(16.dp).clip(RoundedCornerShape(3.dp)).background(art))
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.fillMaxWidth(0.95f).height(6.dp).clip(CircleShape).background(fg.copy(alpha = 0.9f)))
                        Spacer(Modifier.height(3.dp))
                        Box(Modifier.fillMaxWidth(0.6f).height(6.dp).clip(CircleShape).background(fg.copy(alpha = 0.9f)))
                    }
                    NowPlayingStyle.ORBIT -> {
                        Spacer(Modifier.height(10.dp))
                        Box(Modifier.fillMaxWidth(0.9f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                            Canvas(Modifier.fillMaxSize()) {
                                drawCircle(Color.White.copy(alpha = 0.2f), size.minDimension / 2 - 2f, style = Stroke(2.dp.toPx()))
                                drawArc(
                                    Color.White, -90f, 220f, false, Offset(2f, 2f),
                                    androidx.compose.ui.geometry.Size(size.width - 4f, size.height - 4f), style = Stroke(2.dp.toPx()),
                                )
                            }
                            Box(Modifier.fillMaxSize(0.72f).clip(CircleShape).background(art))
                        }
                    }
                    else -> {
                        Spacer(Modifier.height(if (style == NowPlayingStyle.MINIMAL) 14.dp else 10.dp))
                        Box(
                            Modifier.fillMaxWidth(
                                when (style) {
                                    NowPlayingStyle.MINIMAL -> 0.7f
                                    NowPlayingStyle.GLASS -> 0.8f
                                    else -> 1f
                                }
                            ).aspectRatio(1f).clip(RoundedCornerShape(if (style == NowPlayingStyle.MONO) 2.dp else 5.dp)).background(art)
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (style != NowPlayingStyle.POSTER) {
                    Box(Modifier.fillMaxWidth(0.7f).height(4.dp).clip(CircleShape).background(fg.copy(alpha = 0.8f)))
                    Spacer(Modifier.height(4.dp))
                }
                if (style != NowPlayingStyle.ORBIT) {
                    Box(
                        Modifier.fillMaxWidth().padding(horizontal = if (style == NowPlayingStyle.IMMERSIVE) 7.dp else 0.dp)
                            .height(2.dp).clip(CircleShape).background(fg.copy(alpha = 0.35f))
                    )
                }
                Spacer(Modifier.height(6.dp))
                val filled = style in setOf(NowPlayingStyle.MINIMAL, NowPlayingStyle.GLASS, NowPlayingStyle.ORBIT, NowPlayingStyle.MONO)
                Box(
                    Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp)
                        .size(if (filled) 13.dp else 10.dp).clip(CircleShape)
                        .background(if (style == NowPlayingStyle.MINIMAL) accent else fg)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(style.label, style = MaterialTheme.typography.labelMedium, color = if (selected) accent else cs.onSurface)
    }
}

@Composable
private fun ColorPickerDialog(initial: Color, onDismiss: () -> Unit, onPick: (Color) -> Unit) {
    val hsv = remember {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toArgb(), it) }
    }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1].coerceAtLeast(0.2f)) }
    var value by remember { mutableFloatStateOf(hsv[2].coerceAtLeast(0.3f)) }
    val color = Color.hsv(hue, sat, value)
    val cs = MaterialTheme.colorScheme

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainer).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Custom accent", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier.size(84.dp).shadow(16.dp, CircleShape, spotColor = color).clip(CircleShape).background(color),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "#%06X".format(color.toArgb() and 0xFFFFFF), style = MaterialTheme.typography.labelMedium,
                    color = if (color.luminance() > 0.6f) Color.Black else Color.White,
                )
            }
            Spacer(Modifier.height(20.dp))
            GradientSlider(
                hue / 360f,
                listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f).map { Color.hsv(it % 360f, 1f, 1f) },
            ) { hue = it * 360f }
            Spacer(Modifier.height(14.dp))
            GradientSlider(sat, listOf(Color.hsv(hue, 0f, value), Color.hsv(hue, 1f, value))) { sat = it }
            Spacer(Modifier.height(14.dp))
            GradientSlider(value, listOf(Color.Black, Color.hsv(hue, sat, 1f))) { value = it.coerceAtLeast(0.15f) }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onPick(color) },
                    colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = if (color.luminance() > 0.6f) Color.Black else Color.White),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("Apply") }
            }
        }
    }
}

@Composable
private fun GradientSlider(value: Float, colors: List<Color>, onChange: (Float) -> Unit) {
    val cb by rememberUpdatedState(onChange)
    Canvas(
        Modifier.fillMaxWidth().height(30.dp)
            .pointerInput(Unit) {
                detectDragGestures { c, _ -> c.consume(); cb((c.position.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectTapGestures { o -> cb((o.x / size.width).coerceIn(0f, 1f)) }
            }
    ) {
        val h = 14.dp.toPx()
        val top = (size.height - h) / 2
        drawRoundRect(
            Brush.horizontalGradient(colors), Offset(0f, top),
            androidx.compose.ui.geometry.Size(size.width, h),
            androidx.compose.ui.geometry.CornerRadius(h / 2, h / 2),
        )
        val x = value.coerceIn(0f, 1f) * size.width
        drawCircle(Color.White, 12.dp.toPx(), Offset(x.coerceIn(12.dp.toPx(), size.width - 12.dp.toPx()), size.height / 2))
        drawCircle(Color.Black.copy(alpha = 0.15f), 12.dp.toPx(), Offset(x.coerceIn(12.dp.toPx(), size.width - 12.dp.toPx()), size.height / 2), style = Stroke(1.dp.toPx()))
    }
}
