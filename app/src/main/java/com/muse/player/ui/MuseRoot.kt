package com.muse.player.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.muse.player.MainViewModel
import com.muse.player.data.*
import com.muse.player.online.Downloader
import com.muse.player.online.OnlineCatalog
import com.muse.player.online.Song
import com.muse.player.ui.theme.isDark

enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    LIBRARY("Library", Icons.Outlined.LibraryMusic, Icons.Rounded.LibraryMusic),
    SEARCH("Search", Icons.Outlined.Search, Icons.Rounded.Search),
    PLAYLISTS("Playlists", Icons.AutoMirrored.Outlined.QueueMusic, Icons.AutoMirrored.Rounded.QueueMusic),
    DOWNLOADS("Downloads", Icons.Outlined.DownloadForOffline, Icons.Rounded.DownloadForOffline),
    SETTINGS("Settings", Icons.Outlined.Settings, Icons.Rounded.Settings),
}

sealed interface Route {
    data class AlbumPage(val key: String) : Route
    data class ArtistPage(val name: String) : Route
    data class PlaylistPage(val id: String) : Route
    data class Link(val url: String) : Route
    data object Equalizer : Route
}

@Composable
fun MuseRoot(vm: MainViewModel, s: AppSettings) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val audioPerm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, audioPerm) == PackageManager.PERMISSION_GRANTED)
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        granted = res[audioPerm] == true ||
            ContextCompat.checkSelfPermission(context, audioPerm) == PackageManager.PERMISSION_GRANTED
    }
    val requestPerms = {
        val perms = if (Build.VERSION.SDK_INT >= 33) arrayOf(audioPerm, Manifest.permission.POST_NOTIFICATIONS) else arrayOf(audioPerm)
        permLauncher.launch(perms)
    }
    LaunchedEffect(Unit) { if (!granted) requestPerms() }

    var rescanTick by remember { mutableIntStateOf(0) }
    val completedDownloads by Downloader.completed.collectAsState()
    LaunchedEffect(granted, s.autoScan, s.folders, s.minDurationSec, rescanTick, completedDownloads) { vm.scan(s, granted) }

    val tracks by vm.library.tracks.collectAsState()
    val loaded by vm.library.loaded.collectAsState()
    val scanning by vm.library.scanning.collectAsState()
    val pstate by vm.player.state.collectAsState()
    val catalog by OnlineCatalog.tracks.collectAsState()
    val libraryById = remember(tracks) { tracks.associateBy { it.id } }
    val byId = remember(libraryById, catalog) { catalog + libraryById }
    val albums = remember(tracks) { tracks.albums() }
    val artists = remember(tracks) { tracks.artists() }
    val current = pstate.mediaId?.let { byId[it] }
    val dlTasks by Downloader.tasks.collectAsState()
    val activeDownloads = dlTasks.count { it.active }

    var tab by rememberSaveable { mutableStateOf(Tab.LIBRARY) }
    var libSection by rememberSaveable { mutableIntStateOf(0) }
    val stack = remember { mutableStateListOf<Route>() }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val showPlayer = expanded && current != null
    var sheetSongs by remember { mutableStateOf<List<Song>?>(null) }

    BackHandler(stack.isNotEmpty() && !showPlayer) { stack.removeAt(stack.lastIndex) }
    BackHandler(showPlayer) { expanded = false }

    // A friend's Muse Flow link: stream it straight away and open the player.
    val sharedSong by vm.sharedSong.collectAsState()
    LaunchedEffect(sharedSong) {
        val song = sharedSong ?: return@LaunchedEffect
        vm.sharedSong.value = null
        vm.player.play(listOf(OnlineCatalog.track(song)), 0)
        expanded = true
        Toast.makeText(context, "Playing shared song", Toast.LENGTH_SHORT).show()
        // Upgrade to YouTube Music's square cover and album name when we can find the same track.
        runCatching { com.muse.player.online.YtMusic.search("${song.title} ${song.artist}") }
            .getOrNull()?.firstOrNull { it.id == song.id }
            ?.let { OnlineCatalog.register(listOf(it)) }
    }

    // Links shared into the app from YouTube, browsers, etc.
    val shared by vm.sharedLink.collectAsState()
    LaunchedEffect(shared) {
        val url = shared ?: return@LaunchedEffect
        expanded = false
        tab = Tab.SEARCH
        stack.clear()
        stack.add(Route.Link(url))
        vm.sharedLink.value = null
    }

    val view = LocalView.current
    val dark = s.isDark()
    val lightBars = if (showPlayer) (s.nowPlayingStyle == NowPlayingStyle.MINIMAL && !dark) else !dark
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = lightBars
            isAppearanceLightNavigationBars = lightBars
        }
    }

    val actions = remember(vm) {
        Actions(
            play = { list, i -> vm.player.play(list, i) },
            shuffle = { list -> vm.player.play(list.shuffled(), 0) },
            playNext = { t ->
                vm.player.playNext(t)
                Toast.makeText(context, "Playing next", Toast.LENGTH_SHORT).show()
            },
            enqueue = { t ->
                vm.player.enqueue(t)
                Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
            },
            openAlbum = { key -> stack.add(Route.AlbumPage(key)); expanded = false },
            openArtist = { name -> stack.add(Route.ArtistPage(name)); expanded = false },
            addToPlaylist = { ts -> sheetSongs = ts.map(Song::fromTrack) },
            share = { t ->
                val song = OnlineCatalog.song(t.id)
                if (song == null || !com.muse.player.online.Share.send(context, song)) {
                    Toast.makeText(context, "Only online songs can be shared", Toast.LENGTH_SHORT).show()
                }
            },
            download = { ts ->
                toastQueued(context, Downloader.enqueue(ts.mapNotNull { OnlineCatalog.song(it.id) }))
            },
        )
    }

    CompositionLocalProvider(
        LocalActions provides actions,
        LocalNowPlaying provides NowPlayingInfo(pstate.mediaId, pstate.playing),
    ) {
        Box(Modifier.fillMaxSize().background(cs.background)) {
            val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val bottomSpace by animateDpAsState(
                78.dp + navBottom + (if (current != null) 70.dp else 0.dp) + 8.dp, label = "bottomSpace",
            )
            val padding = PaddingValues(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                bottom = bottomSpace,
            )
            val holder = rememberSaveableStateHolder()
            val target: Any = stack.lastOrNull() ?: tab
            val chips: @Composable () -> Unit = { LibraryChips(libSection) { libSection = it } }

            AnimatedContent(
                target,
                transitionSpec = {
                    if (targetState is Route) {
                        (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 6 }) togetherWith fadeOut(tween(140))
                    } else {
                        fadeIn(tween(200)) togetherWith fadeOut(tween(120))
                    }
                },
                label = "nav",
            ) { dest ->
                holder.SaveableStateProvider(dest.toString()) {
                    val back = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
                    when (dest) {
                        Tab.LIBRARY -> Crossfade(libSection, label = "libSection") { sec ->
                            when (sec) {
                                0 -> SongsScreen(tracks, s, padding, loaded, granted, requestPerms, chips)
                                1 -> AlbumsScreen(albums, s, padding, chips)
                                else -> ArtistsScreen(artists, padding, chips)
                            }
                        }
                        Tab.SEARCH -> DiscoverScreen(tracks, albums, artists, padding) { url -> stack.add(Route.Link(url)) }
                        Tab.PLAYLISTS -> PlaylistsScreen(padding) { id -> stack.add(Route.PlaylistPage(id)) }
                        Tab.DOWNLOADS -> DownloadsScreen(padding)
                        Tab.SETTINGS -> SettingsScreen(
                            s, tracks, artists, scanning, padding,
                            onRescan = { rescanTick++ },
                            onOpenEq = { stack.add(Route.Equalizer) },
                        )
                        is Route.AlbumPage -> albums.find { it.key == dest.key }
                            ?.let { AlbumDetail(it, padding) { back() } }
                        is Route.ArtistPage -> artists.find { it.name == dest.name }
                            ?.let { ArtistDetail(it, padding) { back() } }
                        is Route.PlaylistPage -> PlaylistDetail(dest.id, libraryById, padding) { back() }
                        is Route.Link -> LinkScreen(dest.url, padding, onBack = { back() }) { id ->
                            stack.removeAt(stack.lastIndex)
                            tab = Tab.PLAYLISTS
                            stack.clear()
                            stack.add(Route.PlaylistPage(id))
                        }
                        Route.Equalizer -> EqualizerScreen(s, padding) { back() }
                    }
                }
            }

            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(bottomSpace)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, cs.background.copy(alpha = 0.92f), cs.background)))
            )
            Column(Modifier.align(Alignment.BottomCenter)) {
                AnimatedVisibility(
                    current != null,
                    enter = slideInVertically(spring(0.8f, 380f)) { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    current?.let { MiniPlayer(it, vm, s.adaptiveColor) { expanded = true } }
                }
                BottomBar(tab, activeDownloads) { t ->
                    stack.clear()
                    tab = t
                }
            }

            AnimatedVisibility(
                showPlayer,
                enter = slideInVertically(spring(dampingRatio = 0.86f, stiffness = 380f)) { it } + fadeIn(tween(150)),
                exit = slideOutVertically(tween(300, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(300)),
            ) {
                var last by remember { mutableStateOf(current) }
                if (current != null) last = current
                last?.let { t ->
                    NowPlayingScreen(
                        t, byId, vm, s,
                        onCollapse = { expanded = false },
                        onOpenEq = { expanded = false; stack.add(Route.Equalizer) },
                    )
                }
            }
        }

        sheetSongs?.let { songs -> AddToPlaylistSheet(songs) { sheetSongs = null } }
    }
}

@Composable
private fun BottomBar(tab: Tab, activeDownloads: Int, onTab: (Tab) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.navigationBarsPadding().padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 10.dp)
            .fillMaxWidth().height(62.dp)
            .shadow(18.dp, RoundedCornerShape(31.dp), spotColor = Color.Black.copy(alpha = 0.3f))
            .clip(RoundedCornerShape(31.dp))
            .background(cs.surfaceContainerHigh)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { t ->
            val selected = t == tab
            val weight by animateFloatAsState(if (selected) 2.1f else 1f, spring(0.8f, 420f), label = "tabWeight")
            val color by animateColorAsState(if (selected) cs.primary else cs.onSurfaceVariant, label = "tabColor")
            val bg by animateColorAsState(if (selected) cs.primary.copy(alpha = 0.14f) else Color.Transparent, label = "tabBg")
            Box(
                Modifier.weight(weight).fillMaxHeight().clip(RoundedCornerShape(25.dp)).background(bg)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onTab(t) },
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        Crossfade(selected, label = "tabIcon") { sel ->
                            Icon(if (sel) t.selectedIcon else t.icon, t.label, tint = color, modifier = Modifier.size(22.dp))
                        }
                        if (t == Tab.DOWNLOADS && activeDownloads > 0) {
                            Box(
                                Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-2).dp).size(9.dp)
                                    .clip(CircleShape).background(cs.primary)
                            )
                        }
                    }
                    AnimatedVisibility(
                        selected,
                        enter = fadeIn(tween(200, 80)) + expandHorizontally(spring(0.8f, 420f)),
                        exit = fadeOut(tween(90)) + shrinkHorizontally(spring(0.8f, 420f)),
                    ) {
                        Text(
                            t.label, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
        }
    }
}
