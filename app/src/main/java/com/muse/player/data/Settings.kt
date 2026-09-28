package com.muse.player.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.dataStore by preferencesDataStore("settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class TrackLayout { LIST, GRID }
enum class SongSort(val label: String) { TITLE("Title"), ARTIST("Artist"), ALBUM("Album"), RECENT("Recently added") }
enum class DlFormat(val ext: String, val label: String) { MP3("mp3", "MP3"), M4A("m4a", "M4A"), OPUS("opus", "Opus") }
enum class NowPlayingStyle(val label: String) {
    CLASSIC("Classic"), IMMERSIVE("Immersive"), MINIMAL("Minimal"), CARD("Card"),
    GLASS("Glass"), POSTER("Poster"), ORBIT("Orbit"), MONO("Mono")
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val amoled: Boolean = false,
    val accent: Int = 0xFFFA2D48.toInt(),
    val adaptiveColor: Boolean = true,
    val trackLayout: TrackLayout = TrackLayout.LIST,
    val gridColumns: Int = 2,
    val sort: SongSort = SongSort.TITLE,
    val nowPlayingStyle: NowPlayingStyle = NowPlayingStyle.CLASSIC,
    val autoScan: Boolean = true,
    val folders: Set<String> = emptySet(),
    val minDurationSec: Int = 30,
    val eqEnabled: Boolean = false,
    val eqPreset: String = "Flat",
    val eqBands: List<Int> = emptyList(),
    val bassBoost: Int = 0,
    val virtualizer: Int = 0,
    val fetchArt: Boolean = true,
    val artistImages: Boolean = true,
    val wifiOnly: Boolean = false,
    val dlFormat: DlFormat = DlFormat.MP3,
    val mp3Kbps: Int = 320,
    val embedMeta: Boolean = true,
    val cropArt: Boolean = true,
    val parallel: Int = 2,
)

object Keys {
    val themeMode = stringPreferencesKey("theme_mode")
    val amoled = booleanPreferencesKey("amoled")
    val accent = intPreferencesKey("accent")
    val adaptive = booleanPreferencesKey("adaptive")
    val layout = stringPreferencesKey("layout")
    val gridColumns = intPreferencesKey("grid_columns")
    val sort = stringPreferencesKey("sort")
    val npStyle = stringPreferencesKey("np_style")
    val autoScan = booleanPreferencesKey("auto_scan")
    val folders = stringSetPreferencesKey("folders")
    val minDuration = intPreferencesKey("min_duration")
    val eqEnabled = booleanPreferencesKey("eq_enabled")
    val eqPreset = stringPreferencesKey("eq_preset")
    val eqBands = stringPreferencesKey("eq_bands")
    val bassBoost = intPreferencesKey("bass_boost")
    val virtualizer = intPreferencesKey("virtualizer")
    val fetchArt = booleanPreferencesKey("fetch_art")
    val artistImages = booleanPreferencesKey("artist_images")
    val wifiOnly = booleanPreferencesKey("wifi_only")
    val dlFormat = stringPreferencesKey("dl_format")
    val mp3Kbps = intPreferencesKey("mp3_kbps")
    val embedMeta = booleanPreferencesKey("embed_meta")
    val cropArt = booleanPreferencesKey("crop_art")
    val parallel = intPreferencesKey("parallel")
}

private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
    this?.let { s -> enumValues<E>().firstOrNull { it.name == s } } ?: default

class SettingsRepo(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val flow: Flow<AppSettings> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { p ->
            val d = AppSettings()
            AppSettings(
                themeMode = p[Keys.themeMode].toEnum(d.themeMode),
                amoled = p[Keys.amoled] ?: d.amoled,
                accent = p[Keys.accent] ?: d.accent,
                adaptiveColor = p[Keys.adaptive] ?: d.adaptiveColor,
                trackLayout = p[Keys.layout].toEnum(d.trackLayout),
                gridColumns = p[Keys.gridColumns] ?: d.gridColumns,
                sort = p[Keys.sort].toEnum(d.sort),
                nowPlayingStyle = p[Keys.npStyle].toEnum(d.nowPlayingStyle),
                autoScan = p[Keys.autoScan] ?: d.autoScan,
                folders = p[Keys.folders] ?: d.folders,
                minDurationSec = p[Keys.minDuration] ?: d.minDurationSec,
                eqEnabled = p[Keys.eqEnabled] ?: d.eqEnabled,
                eqPreset = p[Keys.eqPreset] ?: d.eqPreset,
                eqBands = p[Keys.eqBands]?.split(',')?.mapNotNull { it.toIntOrNull() } ?: d.eqBands,
                bassBoost = p[Keys.bassBoost] ?: d.bassBoost,
                virtualizer = p[Keys.virtualizer] ?: d.virtualizer,
                fetchArt = p[Keys.fetchArt] ?: d.fetchArt,
                artistImages = p[Keys.artistImages] ?: d.artistImages,
                wifiOnly = p[Keys.wifiOnly] ?: d.wifiOnly,
                dlFormat = p[Keys.dlFormat].toEnum(d.dlFormat),
                mp3Kbps = p[Keys.mp3Kbps] ?: d.mp3Kbps,
                embedMeta = p[Keys.embedMeta] ?: d.embedMeta,
                cropArt = p[Keys.cropArt] ?: d.cropArt,
                parallel = p[Keys.parallel] ?: d.parallel,
            )
        }
        .distinctUntilChanged()

    fun <T> put(key: Preferences.Key<T>, value: T) {
        scope.launch { context.dataStore.edit { it[key] = value } }
    }

    fun update(block: (MutablePreferences) -> Unit) {
        scope.launch { context.dataStore.edit { block(it) } }
    }
}
