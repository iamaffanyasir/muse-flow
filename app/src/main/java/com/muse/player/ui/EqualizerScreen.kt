package com.muse.player.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.muse.player.Graph
import com.muse.player.data.AppSettings
import com.muse.player.data.Keys
import com.muse.player.playback.AudioFx
import com.muse.player.playback.EqPresets
import kotlin.math.roundToInt

private fun freqLabel(hz: Int) = if (hz >= 1000) "${(hz / 100) / 10f}k".replace(".0k", "k") else "$hz"

@Composable
fun EqualizerScreen(s: AppSettings, padding: PaddingValues, onBack: () -> Unit) {
    val info by AudioFx.info.collectAsState()
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())
    ) {
        BackRow(onBack = onBack)
        LargeTitle("Equalizer") {
            MuseSwitch(s.eqEnabled) { Graph.settings.put(Keys.eqEnabled, it) }
            Spacer(Modifier.width(12.dp))
        }
        val fx = info
        if (fx == null) {
            Text(
                "The equalizer isn't available on this device's audio output.",
                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
        } else {
            EqBody(s, fx)
        }
    }
}

@Composable
private fun EqBody(s: AppSettings, fx: AudioFx.Info) {
    val cs = MaterialTheme.colorScheme
    Column {
        val enabledAlpha = if (s.eqEnabled) 1f else 0.4f
        val levels = remember { mutableStateListOf<Int>() }
        LaunchedEffect(s.eqBands, fx) {
            levels.clear()
            levels.addAll(List(fx.freqsHz.size) { i -> s.eqBands.getOrNull(i) ?: 0 })
        }

        fun save(preset: String) {
            val snapshot = levels.toList()
            Graph.settings.update {
                it[Keys.eqBands] = snapshot.joinToString(",")
                it[Keys.eqPreset] = preset
                it[Keys.eqEnabled] = true
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            items((EqPresets.all.keys + "Custom").toList()) { name ->
                val selected = s.eqPreset == name
                val bg by animateColorAsState(if (selected) cs.primary else cs.surfaceContainerHigh, label = "chip")
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(bg)
                        .pressScale(0.92f) {
                            if (name != "Custom") {
                                val l = EqPresets.levels(name, fx)
                                levels.clear(); levels.addAll(l)
                                l.forEachIndexed { i, v -> AudioFx.previewBand(i, v) }
                                save(name)
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                ) {
                    Text(name, style = MaterialTheme.typography.labelLarge, color = if (selected) cs.onPrimary else cs.onSurface)
                }
            }
        }

        // bands
        Box(
            Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainer)
                .padding(vertical = 18.dp).alpha(enabledAlpha)
        ) {
            val range = (fx.maxLevel - fx.minLevel).toFloat().coerceAtLeast(1f)
            val accent = cs.primary
            // response curve drawn behind the sliders
            Canvas(Modifier.matchParentSize().padding(top = 26.dp, bottom = 30.dp)) {
                if (levels.isEmpty()) return@Canvas
                val step = size.width / levels.size
                val pts = levels.mapIndexed { i, l -> Offset(step * (i + 0.5f), size.height * (1f - (l - fx.minLevel) / range)) }
                val path = Path().apply {
                    moveTo(0f, pts.first().y)
                    lineTo(pts.first().x, pts.first().y)
                    for (i in 1 until pts.size) {
                        val p0 = pts[i - 1]; val p1 = pts[i]
                        val mx = (p0.x + p1.x) / 2
                        cubicTo(mx, p0.y, mx, p1.y, p1.x, p1.y)
                    }
                    lineTo(size.width, pts.last().y)
                }
                val fill = Path().apply { addPath(path); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
                drawPath(fill, Brush.verticalGradient(listOf(accent.copy(alpha = 0.25f), Color.Transparent)))
                drawPath(path, accent.copy(alpha = 0.7f), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            }
            Row(Modifier.fillMaxWidth()) {
                fx.freqsHz.forEachIndexed { i, hz ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        val lv = levels.getOrNull(i) ?: 0
                        Text(
                            "%+.0f".format(lv / 100f), style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant, modifier = Modifier.height(20.dp),
                        )
                        BandSlider(
                            value = lv, min = fx.minLevel, max = fx.maxLevel,
                            modifier = Modifier.height(230.dp).fillMaxWidth(),
                            onChange = { v ->
                                if (i < levels.size) levels[i] = v
                                AudioFx.previewBand(i, v)
                            },
                            onDone = { save("Custom") },
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(freqLabel(hz), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    }
                }
            }
        }

        SettingsGroup("Effects") {
            EffectSlider("Bass boost", Icons.Rounded.Speaker, s.bassBoost, s.eqEnabled, AudioFx::previewBass) {
                Graph.settings.put(Keys.bassBoost, it)
            }
            RowDivider(14.dp)
            EffectSlider("Surround", Icons.Rounded.SurroundSound, s.virtualizer, s.eqEnabled, AudioFx::previewVirtualizer) {
                Graph.settings.put(Keys.virtualizer, it)
            }
        }

        TextButton(
            onClick = {
                val zeros = List(fx.freqsHz.size) { 0 }
                levels.clear(); levels.addAll(zeros)
                zeros.forEachIndexed { i, v -> AudioFx.previewBand(i, v) }
                Graph.settings.update {
                    it[Keys.eqBands] = zeros.joinToString(",")
                    it[Keys.eqPreset] = "Flat"
                    it[Keys.bassBoost] = 0
                    it[Keys.virtualizer] = 0
                }
            },
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Icon(Icons.Rounded.RestartAlt, null)
            Spacer(Modifier.width(6.dp))
            Text("Reset")
        }
    }
}

@Composable
private fun EffectSlider(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: Int,
    enabled: Boolean,
    preview: (Int) -> Unit,
    save: (Int) -> Unit,
) {
    var local by remember(value) { mutableFloatStateOf(value / 1000f) }
    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text("${(local * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = local,
            onValueChange = { local = it; if (enabled) preview((it * 1000).roundToInt()) },
            onValueChangeFinished = { save((local * 1000).roundToInt()) },
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        )
    }
}

@Composable
private fun BandSlider(
    value: Int,
    min: Int,
    max: Int,
    modifier: Modifier,
    onChange: (Int) -> Unit,
    onDone: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val change by rememberUpdatedState(onChange)
    val done by rememberUpdatedState(onDone)
    val range = (max - min).toFloat().coerceAtLeast(1f)
    fun levelAt(y: Float, h: Float) = (min + (1f - (y / h).coerceIn(0f, 1f)) * range).roundToInt()
    Canvas(
        modifier
            .pointerInput(min, max) {
                detectVerticalDragGestures(onDragEnd = { done() }) { c, _ ->
                    c.consume()
                    change(levelAt(c.position.y, size.height.toFloat()))
                }
            }
            .pointerInput(min, max) {
                detectTapGestures { o -> change(levelAt(o.y, size.height.toFloat())); done() }
            }
    ) {
        val cx = size.width / 2
        val trackW = 5.dp.toPx()
        val zeroY = size.height * (1f - (0 - min) / range)
        val y = size.height * (1f - (value - min) / range)
        drawLine(cs.onSurfaceVariant.copy(alpha = 0.22f), Offset(cx, 0f), Offset(cx, size.height), trackW, StrokeCap.Round)
        drawLine(cs.primary, Offset(cx, zeroY), Offset(cx, y), trackW, StrokeCap.Round)
        drawCircle(Color.White, 11.dp.toPx(), Offset(cx, y))
        drawCircle(cs.primary, 11.dp.toPx(), Offset(cx, y), style = Stroke(3.dp.toPx()))
    }
}
