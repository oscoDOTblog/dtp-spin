package com.dtp.spin.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.platform.LocalViewConfiguration
import kotlin.math.abs

/**
 * Scrolling waveform overview. Tap = seek when paused; drag = scrub preview
 * with commit on release (plan gesture contract).
 */
@Composable
fun WaveformView(
    peaks: FloatArray?,
    positionMs: Long,
    durationMs: Long,
    cueMs: Long?,
    scrubPreviewMs: Long?,
    playing: Boolean,
    accent: Color,
    onSeek: (Long) -> Unit,
    onScrubPreview: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val slop = LocalViewConfiguration.current.touchSlop
    val scrubPositionMs = scrubPreviewMs ?: positionMs
    val key = remember(peaks, durationMs) { Any() }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .pointerInput(key, playing) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var x = down.position.x
                    var scrubbing = false
                    var event = awaitPointerEvent()
                    while (event.changes.any { it.pressed }) {
                        val change = event.changes[0]
                        val dx = abs(change.position.x - down.position.x)
                        val dy = abs(change.position.y - down.position.y)
                        // Scrub only on a predominantly horizontal drag, so a
                        // vertical scroll over the waveform doesn't re-cue.
                        if (dx > slop && dx > dy) scrubbing = true
                        if (scrubbing) {
                            x = change.position.x
                            onScrubPreview(xToMs(x, size.width.toFloat(), durationMs))
                            change.consume()
                        }
                        event = awaitPointerEvent()
                    }
                    if (scrubbing) {
                        onSeek(xToMs(x, size.width.toFloat(), durationMs))
                        onScrubPreview(null)
                    } else if (!playing && durationMs > 0) {
                        onSeek(xToMs(down.position.x, size.width.toFloat(), durationMs))
                    }
                }
            },
    ) {
        val w = size.width
        val h = size.height
        drawRect(Color(0xFF0A0D10))
        if (peaks == null || peaks.isEmpty() || durationMs <= 0) {
            drawPlaceholder(h)
            drawPlayhead(scrubPositionMs, durationMs, w, h, accent)
            return@Canvas
        }
        val buckets = peaks.size / 2
        val playedFrac = (scrubPositionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        val colW = w / buckets
        for (b in 0 until buckets) {
            val mn = peaks[b * 2]
            val mx = peaks[b * 2 + 1]
            val centerFrac = (b + 0.5f) / buckets
            val color = if (centerFrac <= playedFrac) WavePlayed else accent.copy(alpha = 0.85f)
            val yTop = h * 0.5f - mx * h * 0.46f
            val yBot = h * 0.5f - mn * h * 0.46f
            drawLine(
                color = color,
                start = Offset(b * colW, yTop.coerceIn(0f, h)),
                end = Offset(b * colW, yBot.coerceIn(0f, h)),
                strokeWidth = (colW * 0.8f).coerceAtLeast(1f),
            )
        }
        drawCueMarker(cueMs, durationMs, w, h)
        drawPlayhead(scrubPositionMs, durationMs, w, h, Color.White)
        if (scrubPreviewMs != null) {
            drawLine(
                color = accent,
                start = Offset((scrubPreviewMs.toFloat() / durationMs * w), 0f),
                end = Offset((scrubPreviewMs.toFloat() / durationMs * w), h),
                strokeWidth = 3f,
            )
        }
    }
}

private fun DrawScope.drawPlaceholder(h: Float) {
    drawLine(
        color = Color(0xFF2A323B),
        start = Offset(0f, h * 0.5f),
        end = Offset(size.width, h * 0.5f),
        strokeWidth = 1f,
    )
}

private fun DrawScope.drawPlayhead(positionMs: Long, durationMs: Long, w: Float, h: Float, color: Color) {
    if (durationMs <= 0) return
    val x = (positionMs.toFloat() / durationMs * w).coerceIn(0f, w)
    drawLine(color = color, start = Offset(x, 0f), end = Offset(x, h), strokeWidth = 2f)
}

private fun DrawScope.drawCueMarker(cueMs: Long?, durationMs: Long, w: Float, h: Float) {
    if (cueMs == null || durationMs <= 0) return
    val x = (cueMs.toFloat() / durationMs * w).coerceIn(0f, w)
    drawLine(color = CueColor, start = Offset(x, 0f), end = Offset(x, h), strokeWidth = 2f)
}

private fun AwaitPointerEventScope.xToMs(x: Float, width: Float, durationMs: Long): Long {
    if (width <= 0 || durationMs <= 0) return 0L
    return ((x / width).coerceIn(0f, 1f) * durationMs).toLong()
}
