package com.muse.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muse.player.Graph
import com.muse.player.data.*

private val GridPad = 14.dp

@Composable
private fun EmptyState(title: String, body: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.LibraryMusic, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = onAction, shape = RoundedCornerShape(12.dp)) { Text(action) }
        }
    }
}

@Composable
fun LayoutToggle(s: AppSettings) {
    HeaderButton(
        if (s.trackLayout == TrackLayout.LIST) Icons.Rounded.GridView else Icons.AutoMirrored.Rounded.List, "Layout",
    ) {
        Graph.settings.put(Keys.layout, (if (s.trackLayout == TrackLayout.LIST) TrackLayout.GRID else TrackLayout.LIST).name)
    }
}

@Composable
fun SongsScreen(
    tracks: List<Track>,
    s: AppSettings,
    padding: PaddingValues,
    loaded: Boolean,
    granted: Boolean,
    onRequestPermission: () -> Unit,
    chips: @Composable () -> Unit = {},
) {
    val a = LocalActions.current
    val sorted = remember(tracks, s.sort) { tracks.applySort(s.sort) }
    val header: @Composable () -> Unit = {
        Column {
            LargeTitle("Songs", if (tracks.isEmpty()) null else "${tracks.size} songs") {
                var open by remember { mutableStateOf(false) }
                Box {
                    HeaderButton(Icons.AutoMirrored.Rounded.Sort, "Sort") { open = true }
                    DropdownMenu(open, { open = false }, shape = RoundedCornerShape(16.dp)) {
                        SongSort.entries.forEach { o ->
                            DropdownMenuItem(
                                text = { Text(o.label) },
                                trailingIcon = { if (o == s.sort) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary) },
                                onClick = { Graph.settings.put(Keys.sort, o.name); open = false },
                            )
                        }
                    }
                }
                LayoutToggle(s)
            }
            chips()
            if (tracks.isNotEmpty()) PlayShuffleRow(sorted)
            else if (loaded) {
                if (!granted && s.folders.isEmpty()) EmptyState(
                    "Let Muse find your music",
                    "Allow access to audio files, or add a folder in Settings.",
                    "Allow access", onRequestPermission,
                ) else EmptyState("No music yet", "Put audio files on your phone or add a folder in Settings → Library.")
            }
        }
    }
    if (s.trackLayout == TrackLayout.LIST) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item(key = "header") { header() }
            itemsIndexed(sorted, key = { _, t -> t.id }) { i, t -> TrackRow(t, onClick = { a.play(sorted, i) }) }
        }
    } else {
        LazyVerticalGrid(
            GridCells.Fixed(s.gridColumns), Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = GridPad, end = GridPad,
                top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding(),
            ),
        ) {
            item(key = "header", span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.bleed(GridPad)) { header() } }
            itemsIndexed(sorted, key = { _, t -> t.id }) { i, t -> TrackTile(t) { a.play(sorted, i) } }
        }
    }
}

@Composable
private fun AlbumTile(album: Album, onClick: () -> Unit) {
    Column(Modifier.padding(6.dp).pressScale(0.96f, onClick)) {
        Artwork(album.tracks.first(), Modifier.fillMaxWidth().aspectRatio(1f), size = 384, shape = RoundedCornerShape(12.dp))
        Spacer(Modifier.height(6.dp))
        Text(album.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
        Text(album.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun AlbumRow(album: Album, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(album.tracks.first(), Modifier.size(62.dp), shape = RoundedCornerShape(10.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(album.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
            Text("${album.artist} · ${album.tracks.size} songs", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun AlbumsScreen(albums: List<Album>, s: AppSettings, padding: PaddingValues, chips: @Composable () -> Unit = {}) {
    val a = LocalActions.current
    if (s.trackLayout == TrackLayout.LIST) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item { Column { LargeTitle("Albums", "${albums.size} albums") { LayoutToggle(s) }; chips() } }
            items(albums, key = { it.key }) { al -> AlbumRow(al) { a.openAlbum(al.key) } }
        }
    } else {
        LazyVerticalGrid(
            GridCells.Fixed(s.gridColumns), Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = GridPad, end = GridPad, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.bleed(GridPad)) { Column { LargeTitle("Albums", "${albums.size} albums") { LayoutToggle(s) }; chips() } }
            }
            items(albums, key = { it.key }) { al -> AlbumTile(al) { a.openAlbum(al.key) } }
        }
    }
}

@Composable
internal fun ArtistRow(artist: Artist, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtistImage(artist, Modifier.size(56.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(artist.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
            Text(
                "${artist.albumCount} albums · ${artist.tracks.size} songs",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ArtistsScreen(artists: List<Artist>, padding: PaddingValues, chips: @Composable () -> Unit = {}) {
    val a = LocalActions.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        item { Column { LargeTitle("Artists", "${artists.size} artists"); chips() } }
        items(artists, key = { it.name }) { ar -> ArtistRow(ar) { a.openArtist(ar.name) } }
    }
}

@Composable
fun AlbumDetail(album: Album, padding: PaddingValues, onBack: () -> Unit) {
    val a = LocalActions.current
    val totalMin = album.tracks.sumOf { it.durationMs } / 60000
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        item { BackRow(onBack = onBack) }
        item {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Artwork(
                    album.tracks.first(),
                    Modifier.fillMaxWidth(0.68f).aspectRatio(1f).shadow(20.dp, RoundedCornerShape(14.dp)),
                    size = 768, shape = RoundedCornerShape(14.dp),
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    album.title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Text(
                    album.artist, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { a.openArtist(album.artist) }.padding(horizontal = 8.dp, vertical = 2.dp),
                )
                Text(
                    "${album.tracks.size} songs · $totalMin min",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { PlayShuffleRow(album.tracks) }
        itemsIndexed(album.tracks, key = { _, t -> t.id }) { i, t -> TrackRow(t, onClick = { a.play(album.tracks, i) }, number = i + 1) }
    }
}

@Composable
fun ArtistDetail(artist: Artist, padding: PaddingValues, onBack: () -> Unit) {
    val a = LocalActions.current
    val albums = remember(artist) { artist.tracks.albums() }
    val tracks = remember(artist) { artist.tracks.sortedBy { it.title.lowercase() } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        item { BackRow(onBack = onBack) }
        item {
            Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                ArtistImage(artist, Modifier.size(96.dp), size = 512)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(artist.name, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${albums.size} albums · ${tracks.size} songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { PlayShuffleRow(tracks) }
        if (albums.isNotEmpty()) {
            item {
                Text("Albums", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 4.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 14.dp)) {
                    items(albums, key = { it.key }) { al -> Box(Modifier.width(150.dp)) { AlbumTile(al) { a.openAlbum(al.key) } } }
                }
            }
        }
        item { Text("Songs", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 4.dp)) }
        itemsIndexed(tracks, key = { _, t -> t.id }) { i, t -> TrackRow(t, onClick = { a.play(tracks, i) }) }
    }
}

@Composable
fun SearchScreen(tracks: List<Track>, albums: List<Album>, artists: List<Artist>, padding: PaddingValues) {
    val a = LocalActions.current
    val cs = MaterialTheme.colorScheme
    var q by rememberSaveable { mutableStateOf("") }
    val query = q.trim().lowercase()
    val songs = remember(query, tracks) {
        if (query.isEmpty()) emptyList()
        else tracks.filter { query in it.title.lowercase() || query in it.artist.lowercase() || query in it.album.lowercase() }.take(60)
    }
    val foundAlbums = remember(query, albums) { if (query.isEmpty()) emptyList() else albums.filter { query in it.title.lowercase() }.take(12) }
    val foundArtists = remember(query, artists) { if (query.isEmpty()) emptyList() else artists.filter { query in it.name.lowercase() }.take(8) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        item { LargeTitle("Search") }
        item {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().height(44.dp)
                    .clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainerHigh).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (q.isEmpty()) Text("Songs, albums, artists", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                    BasicTextField(
                        q, { q = it }, singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onBackground),
                        cursorBrush = SolidColor(cs.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (q.isNotEmpty()) {
                    Icon(
                        Icons.Rounded.Cancel, "Clear", tint = cs.onSurfaceVariant,
                        modifier = Modifier.size(20.dp).clip(CircleShape).clickable { q = "" },
                    )
                }
            }
        }
        if (query.isEmpty()) {
            item {
                Text(
                    "Recently added", style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 4.dp),
                )
            }
            val recent = tracks.sortedByDescending { it.dateAdded }.take(20)
            itemsIndexed(recent, key = { _, t -> "r" + t.id }) { i, t -> TrackRow(t, onClick = { a.play(recent, i) }) }
        } else {
            if (foundArtists.isNotEmpty()) {
                item { SectionTitle("Artists") }
                items(foundArtists, key = { "ar" + it.name }) { ar -> ArtistRow(ar) { a.openArtist(ar.name) } }
            }
            if (foundAlbums.isNotEmpty()) {
                item { SectionTitle("Albums") }
                items(foundAlbums, key = { "al" + it.key }) { al -> AlbumRow(al) { a.openAlbum(al.key) } }
            }
            if (songs.isNotEmpty()) {
                item { SectionTitle("Songs") }
                itemsIndexed(songs, key = { _, t -> "s" + t.id }) { i, t -> TrackRow(t, onClick = { a.play(songs, i) }) }
            }
            if (songs.isEmpty() && foundAlbums.isEmpty() && foundArtists.isEmpty()) {
                item { EmptyState("No results", "Nothing in your library matches \"${q.trim()}\".") }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp))
}
