package com.muse.player.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import com.muse.player.data.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.log10
import kotlin.math.roundToInt

/** Owns the platform audio effects attached to the player's audio session. */
object AudioFx {
    data class Info(val freqsHz: List<Int>, val minLevel: Int, val maxLevel: Int)

    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var virt: Virtualizer? = null
    private val _info = MutableStateFlow<Info?>(null)
    val info = _info.asStateFlow()

    fun attach(sessionId: Int) {
        release()
        eq = runCatching { Equalizer(0, sessionId) }.getOrNull()
        bass = runCatching { BassBoost(0, sessionId) }.getOrNull()
        virt = runCatching { Virtualizer(0, sessionId) }.getOrNull()
        eq?.let { e ->
            runCatching {
                val range = e.bandLevelRange
                _info.value = Info(
                    freqsHz = (0 until e.numberOfBands).map { e.getCenterFreq(it.toShort()) / 1000 },
                    minLevel = range[0].toInt(),
                    maxLevel = range[1].toInt(),
                )
            }
        }
    }

    fun apply(s: AppSettings) {
        eq?.let { e ->
            runCatching {
                e.enabled = s.eqEnabled
                s.eqBands.forEachIndexed { i, level -> if (i < e.numberOfBands) e.setBandLevel(i.toShort(), level.toShort()) }
            }
        }
        bass?.let { b ->
            runCatching {
                b.enabled = s.eqEnabled && s.bassBoost > 0
                if (b.strengthSupported) b.setStrength(s.bassBoost.toShort())
            }
        }
        virt?.let { v ->
            runCatching {
                v.enabled = s.eqEnabled && s.virtualizer > 0
                if (v.strengthSupported) v.setStrength(s.virtualizer.toShort())
            }
        }
    }

    fun previewBand(index: Int, level: Int) {
        runCatching { eq?.setBandLevel(index.toShort(), level.toShort()) }
    }

    fun previewBass(strength: Int) {
        runCatching { bass?.apply { enabled = strength > 0; setStrength(strength.toShort()) } }
    }

    fun previewVirtualizer(strength: Int) {
        runCatching { virt?.apply { enabled = strength > 0; setStrength(strength.toShort()) } }
    }

    fun release() {
        runCatching { eq?.release() }
        runCatching { bass?.release() }
        runCatching { virt?.release() }
        eq = null; bass = null; virt = null
    }
}

/** Presets defined on 5 anchor frequencies and interpolated onto whatever bands the device exposes. */
object EqPresets {
    private val anchors = floatArrayOf(60f, 230f, 910f, 3600f, 14000f)
    val all: LinkedHashMap<String, FloatArray> = linkedMapOf(
        "Flat" to floatArrayOf(0f, 0f, 0f, 0f, 0f),
        "Bass Boost" to floatArrayOf(6f, 4f, 1f, 0f, 0f),
        "Treble Boost" to floatArrayOf(0f, 0f, 1f, 4f, 6f),
        "Vocal" to floatArrayOf(-2f, -1f, 3f, 4f, 1f),
        "Rock" to floatArrayOf(5f, 3f, -1f, 3f, 5f),
        "Pop" to floatArrayOf(-1f, 2f, 4f, 2f, -1f),
        "Jazz" to floatArrayOf(4f, 2f, -1f, 2f, 4f),
        "Classical" to floatArrayOf(4f, 3f, -1f, 3f, 4f),
        "Electronic" to floatArrayOf(5f, 3f, 0f, 2f, 4f),
        "Hip-Hop" to floatArrayOf(5f, 4f, 0f, 1f, 3f),
        "Acoustic" to floatArrayOf(4f, 2f, 1f, 3f, 3f),
        "Loudness" to floatArrayOf(6f, 2f, -1f, 2f, 5f),
        "Late Night" to floatArrayOf(3f, 1f, 0f, -1f, -3f),
    )

    fun levels(name: String, info: AudioFx.Info): List<Int> {
        val gains = all[name] ?: return List(info.freqsHz.size) { 0 }
        val xs = anchors.map { log10(it) }
        return info.freqsHz.map { f ->
            val x = log10(f.coerceAtLeast(20).toFloat())
            val db = when {
                x <= xs.first() -> gains.first()
                x >= xs.last() -> gains.last()
                else -> {
                    val i = xs.indexOfLast { it <= x }
                    val t = (x - xs[i]) / (xs[i + 1] - xs[i])
                    gains[i] + (gains[i + 1] - gains[i]) * t
                }
            }
            (db * 100).roundToInt().coerceIn(info.minLevel, info.maxLevel)
        }
    }
}
