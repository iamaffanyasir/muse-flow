package com.muse.player.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Soft, rounded transport glyphs. Each shape is filled and stroked with a round join,
 * which rounds every corner the way SF Symbols do.
 */
object MuseIcons {
    val Play: ImageVector by lazy {
        rounded("Play", 2.6f) { moveTo(8.5f, 5.5f); lineTo(8.5f, 18.5f); lineTo(19f, 12f); close() }
    }
    val Pause: ImageVector by lazy {
        rounded("Pause", 2.6f) {
            moveTo(7f, 5.5f); lineTo(9.6f, 5.5f); lineTo(9.6f, 18.5f); lineTo(7f, 18.5f); close()
            moveTo(14.4f, 5.5f); lineTo(17f, 5.5f); lineTo(17f, 18.5f); lineTo(14.4f, 18.5f); close()
        }
    }
    val Next: ImageVector by lazy {
        rounded("Next", 2f) {
            moveTo(2.8f, 7.2f); lineTo(2.8f, 16.8f); lineTo(10.6f, 12f); close()
            moveTo(12.4f, 7.2f); lineTo(12.4f, 16.8f); lineTo(20.2f, 12f); close()
        }
    }
    val Previous: ImageVector by lazy {
        rounded("Previous", 2f) {
            moveTo(21.2f, 7.2f); lineTo(21.2f, 16.8f); lineTo(13.4f, 12f); close()
            moveTo(11.6f, 7.2f); lineTo(11.6f, 16.8f); lineTo(3.8f, 12f); close()
        }
    }

    private fun rounded(name: String, stroke: Float, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .path(
                fill = SolidColor(Color.Black),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = stroke,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            )
            .build()
}
