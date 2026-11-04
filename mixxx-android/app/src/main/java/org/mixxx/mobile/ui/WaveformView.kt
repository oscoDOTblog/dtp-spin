package org.mixxx.mobile.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sin

/**
 * Lightweight fake-peak waveform: deterministic pseudo peaks from track id
 * so the MVP shows motion without decoding audio. The played region is
 * highlighted; a real peak decoder lands with the native mixer.
 */
@Composable
fun WaveformView(
    trackId: Long?,
    positionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier
) {
    val peaks = remember(trackId) {
        FloatArray(128) { i ->
            (0.25f + 0.55f * abs(sin(i * 0.7f + (trackId ?: 0L) * 0.13f))).coerceIn(0f, 1f)
        }
    }
    val progress =
        if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Canvas(modifier.fillMaxWidth().height(56.dp)) {
        val barW = size.width / peaks.size
        peaks.forEachIndexed { i, peak ->
            val played = i.toFloat() / peaks.size <= progress
            drawLine(
                color = if (played) Color(0xFF4FC3F7) else Color(0xFF424242),
                start = Offset(i * barW + barW / 2, size.height * (1 - peak) / 2),
                end = Offset(i * barW + barW / 2, size.height * (1 + peak) / 2),
                strokeWidth = (barW * 0.6f).coerceAtLeast(1f)
            )
        }
    }
}
