package com.muse.player.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.muse.player.Graph
import com.muse.player.data.*
import com.muse.player.online.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val urlRegex = Regex("https?://\\S+")
fun firstUrl(text: String?): String? = text?.let { urlRegex.find(it)?.value?.trimEnd('.', ',', ')', '"', '\'') }

// ---------------------------------------------------------------- shared bits

@Composable
fun LibraryChips(selected: Int, onSelect: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.padding(start = 20.dp, end = 20.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf("Songs", "Albums", "Artists").forEachIndexed { i, label ->
            val sel = i == selected
            val bg by animateColorAsState(if (sel) cs.primary else cs.surfaceContainerHigh, label = "chip")
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(bg).pressScale(0.92f) { onSelect(i) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) { Text(label, style = MaterialTheme.typography.labelLarge, color = if (sel) cs.onPrimary else cs.onSurface) }
        }
    }
}

/** Shows and drives the download state of one online song. */
@Composable
fun DownloadButton(song: Song, size: Dp = 40.dp) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val tasks by Downloader.tasks.collectAsState()
    val downloads by Playlists.downloads.collectAsState()
    val library by Graph.library.tracks.collectAsState()
    if (!song.isOnline) return
    val task = tasks.find { it.key == song.id }
    val inLibrary = remember(song, library) { Downloader.libraryMatch(song) != null }
    val done = downloads.containsKey(song.id) || inLibrary
    Box(
        Modifier.size(size).clip(CircleShape).clickable {
            when {
                done -> {
                    if (!downloads.containsKey(song.id)) Downloader.enqueue(listOf(song)) // links it to the library copy
                    Toast.makeText(context, "Already in your library", Toast.LENGTH_SHORT).show()
                }
                task?.active == true -> Downloader.cancel(song.id)
                task?.state == DlState.FAILED || task?.state == DlState.CANCELED -> Downloader.retry(song.id)
                else -> toastQueued(context, Downloader.enqueue(listOf(song)))
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            when {
                done -> 3
                task?.state == DlState.RUNNING -> 2
                task?.state == DlState.QUEUED -> 1
                task?.state == DlState.FAILED -> 4
                else -> 0
            },
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
            label = "dl",
        ) { st ->
            when (st) {
                3 -> Icon(Icons.Rounded.CheckCircle, "Downloaded", tint = cs.primary, modifier = Modifier.size(22.dp))
                2 -> Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { task?.progress ?: 0f }, modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp, color = cs.primary, trackColor = cs.primary.copy(alpha = 0.18f),
                    )
                    Box(Modifier.size(7.dp).clip(RoundedCornerShape(1.5.dp)).background(cs.primary))
                }
                1 -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = cs.onSurfaceVariant)
                4 -> Icon(Icons.Rounded.ErrorOutline, "Retry", tint = cs.error, modifier = Modifier.size(22.dp))
                else -> Icon(Icons.Rounded.ArrowCircleDown, "Download", tint = cs.onSurfaceVariant, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
fun PlaylistCover(tracks: List<Track>, modifier: Modifier, corner: Dp = 14.dp) {
    val cs = MaterialTheme.colorScheme
    val arts = remember(tracks) { tracks.distinctBy { it.albumKey }.take(4) }
    Box(
        modifier.clip(RoundedCornerShape(corner))
            .background(Brush.linearGradient(listOf(cs.primary.copy(alpha = 0.7f), cs.primary.shade(0.45f)))),
        contentAlignment = Alignment.Center,
    ) {
        when {
            arts.isEmpty() -> Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = Color.White, modifier = Modifier.fillMaxSize(0.38f))
            arts.size < 4 -> Artwork(arts[0], Modifier.fillMaxSize(), size = 512, shape = RectangleShape)
            else -> Column(Modifier.fillMaxSize()) {
                for (r in 0..1) Row(Modifier.weight(1f)) {
                    for (c in 0..1) Artwork(arts[r * 2 + c], Modifier.weight(1f).fillMaxHeight(), size = 256, shape = RectangleShape)
                }
            }
        }
    }
}

@Composable
fun NameDialog(title: String, initial: String, confirm: String = "Save", onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                name, { name = it }, singleLine = true, placeholder = { Text("Playlist name") },
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onDone(name) }),
            )
        },
        confirmButton = { TextButton(onClick = { onDone(name) }, enabled = name.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(songs: List<Song>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val lists by Playlists.all.collectAsState()
    var creating by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = cs.surfaceContainer) {
        Text(
            if (songs.size == 1) "Add “${songs.first().title}” to…" else "Add ${songs.size} songs to…",
            style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        LazyColumn(Modifier.heightIn(max = 460.dp)) {
            item {
                Row(
                    Modifier.fillMaxWidth().clickable { creating = true }.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(cs.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Add, null, tint = cs.primary) }
                    Spacer(Modifier.width(14.dp))
                    Text("New playlist", style = MaterialTheme.typography.bodyLarge, color = cs.primary)
                }
            }
            items(lists.sortedByDescending { it.created }, key = { it.id }) { p ->
                val tracks = remember(p.songs) { p.songs.map { OnlineCatalog.track(it) } }
                Row(
                    Modifier.fillMaxWidth().clickable {
                        val n = Playlists.add(p.id, songs)
                        Toast.makeText(context, if (n == 0) "Already in ${p.name}" else "Added to ${p.name}", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlaylistCover(tracks, Modifier.size(52.dp), 12.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(p.name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                        Text("${p.songs.size} songs", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(16.dp))
    }
    if (creating) {
        NameDialog("New playlist", "", "Create", onDismiss = { creating = false }) { name ->
            val p = Playlists.create(name, songs)
            Toast.makeText(context, "Created ${p.name}", Toast.LENGTH_SHORT).show()
            creating = false
            onDismiss()
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
    onSubmit: () -> Unit = {},
    keyboardType: KeyboardType = KeyboardType.Text,
    trailing: @Composable () -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().height(46.dp)
            .clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainerHigh).padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            BasicTextField(
                value, onChange, singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onBackground),
                cursorBrush = SolidColor(cs.primary),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); onSubmit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            IconButton(onClick = { onChange("") }) { Icon(Icons.Rounded.Cancel, "Clear", tint = cs.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
        }
        trailing()
    }
}

@Composable
private fun InfoCard(icon: ImageVector, title: String, body: String, busy: Boolean = false, action: String? = null, onAction: () -> Unit = {}) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainer).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(34.dp), color = cs.primary, strokeWidth = 3.dp)
        else Icon(icon, null, tint = cs.primary, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(14.dp))
            Button(onClick = onAction, shape = RoundedCornerShape(12.dp)) { Text(action) }
        }
    }
}

fun toastQueued(context: android.content.Context, r: Downloader.EnqueueResult) {
    val s = Graph.current
    val fmt = if (s.dlFormat == DlFormat.MP3) "MP3 ${s.mp3Kbps}k" else s.dlFormat.label
    fun songs(n: Int) = "$n ${if (n == 1) "song" else "songs"}"
    val parts = buildList {
        if (r.queued > 0) add("Downloading ${songs(r.queued)} as $fmt")
        if (r.inLibrary > 0) add(if (r.queued == 0 && r.inLibrary == 1) "Already in your library" else "${songs(r.inLibrary)} already in your library")
        if (r.alreadyQueued > 0) add("${songs(r.alreadyQueued)} already downloading")
    }
    Toast.makeText(context, parts.joinToString(" · ").ifEmpty { "Already downloaded" }, Toast.LENGTH_SHORT).show()
}

// ---------------------------------------------------------------- search

@Composable
fun DiscoverScreen(
    library: List<Track>,
    albums: List<Album>,
    artists: List<Artist>,
    padding: PaddingValues,
    onOpenLink: (String) -> Unit,
) {
    val a = LocalActions.current
    val cs = MaterialTheme.colorScheme
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var q by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Song>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var submitTick by remember { mutableIntStateOf(0) }
    val query = q.trim()
    val link = firstUrl(query)
    val downloads by Playlists.downloads.collectAsState()

    LaunchedEffect(query, mode, submitTick) {
        if (mode != 0 || query.length < 2 || link != null) {
            if (query.isEmpty()) results = emptyList()
            return@LaunchedEffect
        }
        delay(450)
        loading = true
        error = null
        results = try {
            YtMusic.search(query)
        } catch (e: Exception) {
            error = "Couldn't search right now. Check your connection."
            emptyList()
        }
        loading = false
    }
    val tracks = remember(results, downloads) { OnlineCatalog.register(results) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        item {
            LargeTitle("Search") { HeaderButton(Icons.Rounded.Link, "Paste a link") { onOpenLink("") } }
        }
        item {
            Segmented(listOf("Online", "Your library"), mode, Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { mode = it }
        }
        item {
            SearchField(
                q, if (mode == 0) "Songs, artists or paste a link" else "Songs, albums, artists",
                onChange = { q = it }, onSubmit = { submitTick++ },
            )
        }
        if (mode == 0) {
            when {
                link != null -> item {
                    InfoCard(Icons.Rounded.Link, "Open this link", link, action = "Fetch") { onOpenLink(link) }
                }
                query.isEmpty() -> item {
                    InfoCard(
                        Icons.Rounded.TravelExplore, "Find any song",
                        "Search YouTube Music, stream instantly, add to playlists and download in your format.",
                    )
                }
                loading && results.isEmpty() -> item { InfoCard(Icons.Rounded.Search, "Searching…", "", busy = true) }
                error != null -> item { InfoCard(Icons.Rounded.WifiOff, "Offline?", error!!, action = "Retry") { submitTick++ } }
                results.isEmpty() && !loading -> item { InfoCard(Icons.Rounded.SearchOff, "No results", "Try a different spelling.") }
                else -> {
                    if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp), color = cs.primary) }
                    itemsIndexed(tracks, key = { _, t -> "o" + t.id }) { i, t ->
                        TrackRow(t, onClick = { a.play(tracks, i) }) { DownloadButton(results[i]) }
                    }
                }
            }
        } else {
            val ql = query.lowercase()
            if (ql.isEmpty()) {
                item { InfoCard(Icons.Rounded.LibraryMusic, "Search your library", "Everything on this phone, including your downloads.") }
            } else {
                val songs = library.filter { ql in it.title.lowercase() || ql in it.artist.lowercase() || ql in it.album.lowercase() }.take(60)
                val foundArtists = artists.filter { ql in it.name.lowercase() }.take(8)
                val foundAlbums = albums.filter { ql in it.title.lowercase() }.take(12)
                if (foundArtists.isNotEmpty()) {
                    item { SectionHeader("Artists") }
                    items(foundArtists, key = { "ar" + it.name }) { ar -> ArtistRow(ar) { a.openArtist(ar.name) } }
                }
                if (foundAlbums.isNotEmpty()) {
                    item { SectionHeader("Albums") }
                    items(foundAlbums, key = { "al" + it.key }) { al -> AlbumRow(al) { a.openAlbum(al.key) } }
                }
                if (songs.isNotEmpty()) {
                    item { SectionHeader("Songs") }
                    itemsIndexed(songs, key = { _, t -> "s" + t.id }) { i, t -> TrackRow(t, onClick = { a.play(songs, i) }) }
                }
                if (songs.isEmpty() && foundAlbums.isEmpty() && foundArtists.isEmpty()) {
                    item { InfoCard(Icons.Rounded.SearchOff, "Nothing here", "Switch to Online to find it on YouTube Music.") }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp))
}

// ---------------------------------------------------------------- paste a link (Seal-style)

private sealed interface LinkUi {
    data object Idle : LinkUi
    data object Loading : LinkUi
    data class Failed(val message: String) : LinkUi
    data class Done(val info: Ytdl.LinkInfo) : LinkUi
}

@Composable
fun LinkScreen(initial: String, padding: PaddingValues, onBack: () -> Unit, onOpenPlaylist: (String) -> Unit) {
    val a = LocalActions.current
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val ytStatus by Ytdl.status.collectAsState()
    var url by rememberSaveable { mutableStateOf(initial) }
    var ui by remember { mutableStateOf<LinkUi>(LinkUi.Idle) }
    val downloads by Playlists.downloads.collectAsState()

    fun fetch() {
        val target = firstUrl(url) ?: run {
            ui = LinkUi.Failed("That doesn't look like a link.")
            return
        }
        url = target
        ui = LinkUi.Loading
        scope.launch {
            ui = try {
                LinkUi.Done(Ytdl.fetchInfo(target))
            } catch (e: Exception) {
                LinkUi.Failed(e.message?.lines()?.lastOrNull { "ERROR" in it }?.removePrefix("ERROR:")?.trim()
                    ?: "Couldn't read this link.")
            }
        }
    }
    LaunchedEffect(Unit) { if (initial.isNotBlank()) fetch() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        item { BackRow(onBack = onBack) }
        item { LargeTitle("Paste a link", "YouTube, YouTube Music, SoundCloud, Bandcamp & more") }
        item {
            SearchField(
                url, "https://…", onChange = { url = it }, onSubmit = { fetch() }, keyboardType = KeyboardType.Uri,
            ) {
                IconButton(onClick = { clipboard.getText()?.text?.let { url = firstUrl(it) ?: it; fetch() } }) {
                    Icon(Icons.Rounded.ContentPaste, "Paste", tint = cs.primary, modifier = Modifier.size(20.dp))
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                PillButton(Icons.Rounded.TravelExplore, "Fetch", Modifier.fillMaxWidth()) { fetch() }
            }
        }
        when (val st = ui) {
            LinkUi.Idle -> item {
                InfoCard(
                    Icons.Rounded.Link, "Paste any music link",
                    "Songs, albums and playlists. Tip: share a link from YouTube straight to Muse Flow.",
                )
            }
            LinkUi.Loading -> item {
                InfoCard(
                    Icons.Rounded.Link, "Reading link…",
                    if (!Ytdl.isReady) "$ytStatus (first launch takes a moment)" else "This can take a few seconds.",
                    busy = true,
                )
            }
            is LinkUi.Failed -> item { InfoCard(Icons.Rounded.ErrorOutline, "Couldn't open this link", st.message, action = "Try again") { fetch() } }
            is LinkUi.Done -> when (val info = st.info) {
                is Ytdl.LinkInfo.Single -> item {
                    val track = remember(info.song, downloads) { OnlineCatalog.track(info.song) }
                    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Artwork(track, Modifier.fillMaxWidth(0.7f).aspectRatio(1f).shadow(20.dp, RoundedCornerShape(18.dp)), size = 768, shape = RoundedCornerShape(18.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(track.title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                        Text(track.artist, style = MaterialTheme.typography.titleMedium, color = cs.primary)
                        if (track.durationMs > 0) Text(formatTime(track.durationMs), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PillButton(MuseIcons.Play, "Play", Modifier.weight(1f)) { a.play(listOf(track), 0) }
                            PillButton(Icons.Rounded.Download, "Download", Modifier.weight(1f)) {
                                toastQueued(context, Downloader.enqueue(listOf(info.song)))
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        PillButton(Icons.Rounded.LibraryAdd, "Add to playlist", Modifier.fillMaxWidth()) { a.addToPlaylist(listOf(track)) }
                    }
                }
                is Ytdl.LinkInfo.Collection -> {
                    val tracks = OnlineCatalog.register(info.songs)
                    item {
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                            Text(info.title, style = MaterialTheme.typography.headlineSmall)
                            Text("${info.songs.size} songs", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                        Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PillButton(MuseIcons.Play, "Play all", Modifier.weight(1f)) { a.play(tracks, 0) }
                            PillButton(Icons.Rounded.Download, "Download all", Modifier.weight(1f)) {
                                toastQueued(context, Downloader.enqueue(info.songs))
                            }
                        }
                        Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                            PillButton(Icons.Rounded.PlaylistAdd, "Save as playlist", Modifier.fillMaxWidth()) {
                                val p = Playlists.create(info.title, info.songs)
                                Toast.makeText(context, "Saved “${p.name}”", Toast.LENGTH_SHORT).show()
                                onOpenPlaylist(p.id)
                            }
                        }
                    }
                    itemsIndexed(tracks, key = { _, t -> "l" + t.id }) { i, t ->
                        TrackRow(t, onClick = { a.play(tracks, i) }) { DownloadButton(info.songs[i]) }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- playlists

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistsScreen(padding: PaddingValues, onOpen: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val lists by Playlists.all.collectAsState()
    val downloads by Playlists.downloads.collectAsState()
    val haptics = LocalHapticFeedback.current
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Playlist?>(null) }
    var deleting by remember { mutableStateOf<Playlist?>(null) }
    val sorted = lists.sortedByDescending { it.created }

    LazyVerticalGrid(
        GridCells.Fixed(2), Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.bleed(14.dp)) {
                LargeTitle("Playlists", if (lists.isEmpty()) null else "${lists.size} playlists") {
                    HeaderButton(Icons.Rounded.Add, "New playlist") { creating = true }
                }
            }
        }
        if (sorted.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                InfoCard(
                    Icons.AutoMirrored.Rounded.QueueMusic, "No playlists yet",
                    "Create one, then add songs from Search or your library.", action = "New playlist",
                ) { creating = true }
            }
        }
        items(sorted.size, key = { sorted[it].id }) { i ->
            val p = sorted[i]
            val tracks = remember(p.songs, downloads) { p.songs.map { OnlineCatalog.track(it) } }
            var menu by remember { mutableStateOf(false) }
            val online = p.songs.count { it.isOnline }
            val saved = p.songs.count { it.isOnline && downloads.containsKey(it.id) }
            Column(
                Modifier.padding(6.dp).clip(RoundedCornerShape(16.dp))
                    .combinedClickable(
                        onClick = { onOpen(p.id) },
                        onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); menu = true },
                    ),
            ) {
                PlaylistCover(tracks, Modifier.fillMaxWidth().aspectRatio(1f), 16.dp)
                Spacer(Modifier.height(6.dp))
                Text(p.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${p.songs.size} songs" + if (online > 0) " · $saved/$online saved" else "",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                )
                DropdownMenu(menu, { menu = false }, shape = RoundedCornerShape(16.dp)) {
                    DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; renaming = p })
                    DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) }, onClick = { menu = false; deleting = p })
                }
            }
        }
    }

    if (creating) NameDialog("New playlist", "", "Create", onDismiss = { creating = false }) { name ->
        creating = false
        onOpen(Playlists.create(name).id)
    }
    renaming?.let { p -> NameDialog("Rename playlist", p.name, onDismiss = { renaming = null }) { Playlists.rename(p.id, it); renaming = null } }
    deleting?.let { p -> ConfirmDelete(p, onDismiss = { deleting = null }) { Playlists.delete(p.id); deleting = null } }
}

@Composable
private fun ConfirmDelete(p: Playlist, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text("Delete “${p.name}”?") },
        text = { Text("Downloaded songs stay on your phone.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun PlaylistDetail(id: String, libraryById: Map<String, Track>, padding: PaddingValues, onBack: () -> Unit) {
    val a = LocalActions.current
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val lists by Playlists.all.collectAsState()
    val downloads by Playlists.downloads.collectAsState()
    val tasks by Downloader.tasks.collectAsState()
    val p = lists.find { it.id == id }
    if (p == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val tracks = remember(p.songs, downloads, libraryById) { p.songs.map { s -> libraryById[s.id] ?: OnlineCatalog.track(s) } }
    val online = p.songs.filter { it.isOnline }
    val saved = online.count { downloads.containsKey(it.id) }
    val ids = online.map { it.id }.toHashSet()
    val active = tasks.filter { it.active && it.key in ids }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { BackRow(onBack = onBack) }
                Box {
                    HeaderButton(Icons.Rounded.MoreHoriz, "More") { menu = true }
                    DropdownMenu(menu, { menu = false }, shape = RoundedCornerShape(16.dp)) {
                        DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; renaming = true })
                        DropdownMenuItem(text = { Text("Delete playlist") }, leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) }, onClick = { menu = false; deleting = true })
                    }
                }
                Spacer(Modifier.width(12.dp))
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                PlaylistCover(tracks, Modifier.fillMaxWidth(0.6f).aspectRatio(1f).shadow(20.dp, RoundedCornerShape(18.dp)), 18.dp)
                Spacer(Modifier.height(16.dp))
                Text(p.name, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
                Text(
                    "${p.songs.size} songs · ${formatTime(p.songs.sumOf { it.durationMs })}" + if (online.isNotEmpty()) " · $saved of ${online.size} downloaded" else "",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                )
            }
        }
        if (tracks.isNotEmpty()) {
            item { PlayShuffleRow(tracks) }
            if (online.isNotEmpty()) item {
                val allSaved = saved == online.size
                Column(Modifier.padding(horizontal = 20.dp)) {
                    PillButton(
                        when {
                            active.isNotEmpty() -> Icons.Rounded.Close
                            allSaved -> Icons.Rounded.CheckCircle
                            else -> Icons.Rounded.Download
                        },
                        when {
                            active.isNotEmpty() -> "Downloading · $saved/${online.size} done · Stop"
                            allSaved -> "All songs downloaded"
                            else -> "Download playlist (${online.size - saved})"
                        },
                        Modifier.fillMaxWidth(),
                    ) {
                        when {
                            active.isNotEmpty() -> active.forEach { Downloader.cancel(it.key) }
                            allSaved -> Toast.makeText(context, "Saved in Music/${Downloader.FOLDER}", Toast.LENGTH_SHORT).show()
                            else -> toastQueued(context, Downloader.enqueue(online))
                        }
                    }
                    if (active.isNotEmpty()) {
                        val progress = (saved + active.sumOf { it.progress.toDouble() }.toFloat()) / online.size
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(4.dp).clip(CircleShape),
                            color = cs.primary, trackColor = cs.primary.copy(alpha = 0.15f),
                        )
                    }
                }
            }
        } else {
            item { InfoCard(Icons.Rounded.Search, "This playlist is empty", "Find songs in Search and choose “Add to playlist”.") }
        }
        itemsIndexed(tracks, key = { _, t -> "p" + t.id }) { i, t ->
            val song = p.songs[i]
            TrackRow(t, onClick = { a.play(tracks, i) }) {
                DownloadButton(song)
                var rowMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { rowMenu = true }) { Icon(Icons.Rounded.MoreVert, "More", tint = cs.onSurfaceVariant) }
                    DropdownMenu(rowMenu, { rowMenu = false }, shape = RoundedCornerShape(16.dp)) {
                        DropdownMenuItem(
                            text = { Text("Remove from playlist") },
                            leadingIcon = { Icon(Icons.Rounded.RemoveCircleOutline, null) },
                            onClick = { rowMenu = false; Playlists.remove(p.id, song.id) },
                        )
                    }
                }
            }
        }
    }

    if (renaming) NameDialog("Rename playlist", p.name, onDismiss = { renaming = false }) { Playlists.rename(p.id, it); renaming = false }
    if (deleting) ConfirmDelete(p, onDismiss = { deleting = false }) { deleting = false; Playlists.delete(p.id) }
}

// ---------------------------------------------------------------- downloads

@Composable
fun DownloadsScreen(padding: PaddingValues) {
    val cs = MaterialTheme.colorScheme
    val s = Graph.current
    val tasks by Downloader.tasks.collectAsState()
    val ytStatus by Ytdl.status.collectAsState()
    val sorted = remember(tasks) {
        tasks.sortedWith(compareBy<DlTask>({ !it.active }, { it.state != DlState.RUNNING }).thenByDescending { it.added })
    }
    val fmt = if (s.dlFormat == DlFormat.MP3) "MP3 · ${s.mp3Kbps} kbps" else s.dlFormat.label

    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        item {
            LargeTitle("Downloads", "$fmt → Music/${Downloader.FOLDER}") {
                if (tasks.any { !it.active }) HeaderButton(Icons.Rounded.ClearAll, "Clear finished") { Downloader.clearFinished() }
            }
        }
        if (!Ytdl.isReady) item {
            InfoCard(Icons.Rounded.Downloading, ytStatus, "Unpacking yt-dlp and ffmpeg — only on first launch.", busy = ytStatus != "Downloader failed to start")
        }
        if (sorted.isEmpty()) item {
            InfoCard(
                Icons.Rounded.DownloadForOffline, "Nothing downloading",
                "Tap the download icon on any song, download a whole playlist, or paste a link in Search.",
            )
        }
        items(sorted, key = { it.key }) { t -> DownloadRow(t) }
    }
}

@Composable
private fun DownloadRow(t: DlTask) {
    val cs = MaterialTheme.colorScheme
    val track = remember(t.song) { OnlineCatalog.track(t.song) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(track, Modifier.size(50.dp), shape = RoundedCornerShape(10.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(t.song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
            val sub = when (t.state) {
                DlState.QUEUED -> "Waiting…"
                DlState.RUNNING -> buildString {
                    append(t.message.ifBlank { "Downloading" })
                    if (t.progress in 0.01f..0.99f) append(" · ${(t.progress * 100).toInt()}%")
                    if (t.etaSec > 0 && t.progress < 1f) append(" · ${formatTime(t.etaSec * 1000)} left")
                }
                DlState.DONE -> "Saved · ${t.message}"
                DlState.FAILED -> t.message
                DlState.CANCELED -> "Canceled"
            }
            Text(
                sub, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                color = if (t.state == DlState.FAILED) cs.error else cs.onSurfaceVariant,
            )
            if (t.state == DlState.RUNNING) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { t.progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                    color = cs.primary, trackColor = cs.primary.copy(alpha = 0.15f),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        when (t.state) {
            DlState.QUEUED, DlState.RUNNING -> IconButton(onClick = { Downloader.cancel(t.key) }) {
                Icon(Icons.Rounded.Close, "Cancel", tint = cs.onSurfaceVariant)
            }
            DlState.FAILED, DlState.CANCELED -> IconButton(onClick = { Downloader.retry(t.key) }) {
                Icon(Icons.Rounded.Refresh, "Retry", tint = cs.primary)
            }
            DlState.DONE -> Icon(Icons.Rounded.CheckCircle, null, tint = cs.primary, modifier = Modifier.padding(12.dp).size(22.dp))
        }
    }
}
