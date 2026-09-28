package com.muse.player

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.muse.player.data.AppSettings
import com.muse.player.playback.PlayerConnection
import com.muse.player.ui.MuseRoot
import com.muse.player.ui.theme.MuseTheme
import android.content.Intent
import com.muse.player.ui.firstUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val player = PlayerConnection(app, viewModelScope).also { it.connect() }
    val library = Graph.library

    /** A link shared into the app; the UI consumes it and resets it to null. */
    val sharedLink = MutableStateFlow<String?>(null)

    /** A Muse Flow song link that was opened; the UI plays it and resets it to null. */
    val sharedSong = MutableStateFlow<com.muse.player.online.Song?>(null)

    fun scan(s: AppSettings, hasPermission: Boolean) {
        viewModelScope.launch { library.scan(s.autoScan, s.folders, s.minDurationSec, hasPermission) }
    }

    override fun onCleared() {
        player.release()
    }
}

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleShare(intent)
        setContent {
            val settings by Graph.settings.flow.collectAsState(initial = null)
            val s = settings
            if (s != null) {
                MuseTheme(s) { MuseRoot(vm, s) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                val url = firstUrl(intent.getStringExtra(Intent.EXTRA_TEXT)) ?: return
                val song = runCatching { com.muse.player.online.Share.parse(android.net.Uri.parse(url)) }.getOrNull()
                if (song != null) vm.sharedSong.value = song else vm.sharedLink.value = url
            }
            Intent.ACTION_VIEW -> {
                val data = intent.data ?: return
                val song = com.muse.player.online.Share.parse(data)
                if (song != null) vm.sharedSong.value = song else vm.sharedLink.value = data.toString()
            }
        }
    }
}
