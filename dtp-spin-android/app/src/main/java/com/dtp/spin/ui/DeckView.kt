package com.dtp.spin.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dtp.spin.util.AudioMath

/**
 * One deck: identity + meters, scrolling waveform, transport (play/cue with
 * hold-to-preview and long-press set), manual tempo slider.
 */
@Composable
fun DeckView(
    name: String,
    full: DeckFullState,
    accent: Color,
    onTogglePlay: () -> Unit,
    onCueDown: () -> Unit,
    onCueUp: () -> Unit,
    onSetCue: () -> Unit,
    onSeek: (Long) -> Unit,
    onScrubPreview: (Long?) -> Unit,
    onTempoSlider: (Float) -> Unit,
    onTempoNudge: (Float) -> Unit,
    onTempoReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val controls = full.controls
    val engine = full.engine
    val shownPosition = controls.scrubPreviewMs ?: engine.positionMs
    val tempoSlider = AudioMath.rateToTempo(controls.rate, DJViewModel.TEMPO_RANGE)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
    ) {
        // Identity + meters.
        Row(verticalAlignment = Alignment.CenterVertically) {
            DeckBadge(name, accent)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    controls.track?.title ?: "No track — pick one below",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    controls.track?.let { "${it.artist} • ${DJViewModel.formatMs(it.durationMs)}" }
                        ?: "deck $name",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LevelMeter(level = engine.level, accent = accent, modifier = Modifier.height(28.dp))
        }

        Spacer(Modifier.height(4.dp))
        WaveformView(
            peaks = controls.peaks,
            positionMs = engine.positionMs,
            durationMs = engine.durationMs,
            cueMs = controls.cueMs,
            scrubPreviewMs = controls.scrubPreviewMs,
            playing = engine.playing,
            accent = accent,
            onSeek = onSeek,
            onScrubPreview = onScrubPreview,
        )

        // Transport row.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onTogglePlay,
                enabled = controls.track != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (engine.playing) accent else MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(if (engine.playing) "❚❚" else "▶")
            }
            // CUE pad: press-and-hold previews from the cue point, release
            // returns; long-press stores the current playhead as the cue.
            // Implemented on a plain Box: a Material Button child would consume
            // the down event before this detector sees it.
            var cueHeld by remember { mutableStateOf(false) }
            val cueEnabled = controls.track != null
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        when {
                            !cueEnabled -> MaterialTheme.colorScheme.surface
                            cueHeld -> accent
                            else -> MaterialTheme.colorScheme.primaryContainer
                        },
                    )
                    .padding(horizontal = 18.dp, vertical = 10.dp)
                    .pointerInput(cueEnabled) {
                        if (!cueEnabled) return@pointerInput
                        detectTapGestures(
                            onPress = {
                                try {
                                    cueHeld = true
                                    onCueDown()
                                    tryAwaitRelease()
                                } finally {
                                    cueHeld = false
                                    onCueUp()
                                }
                            },
                            onLongPress = { onSetCue() },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "CUE",
                    color = if (cueEnabled) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
            Text(
                "${DJViewModel.formatMs(shownPosition)} / ${DJViewModel.formatMs(engine.durationMs)}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (engine.starved && engine.playing) {
                StatusChip("buffering")
            }
            if (engine.ended) {
                StatusChip("ended")
            }
            controls.cueMs?.let {
                StatusChip("cue ${DJViewModel.formatMs(it)}")
            }
        }

        // Tempo row: manual slider ±8 %, live nudge while dragging, commit on release.
        var sliderPos by remember(controls.rate) { mutableStateOf(tempoSlider) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("TMP", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = sliderPos,
                onValueChange = {
                    sliderPos = it
                    onTempoNudge(it)
                },
                onValueChangeFinished = { onTempoSlider(sliderPos) },
                valueRange = -1f..1f,
                steps = 0,
                modifier = Modifier.weight(1f),
            )
            Text(
                "%+.1f%%".format((controls.rate - 1f) * 100f),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            TextButton(onClick = onTempoReset) {
                Text("0", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DeckBadge(name: String, accent: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(accent.copy(alpha = 0.2f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(name, color = accent, fontSize = 16.sp)
    }
}

@Composable
private fun StatusChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Static preview: no engine, no Activity (plan Phase 0 fakes/previews). */
@androidx.compose.ui.tooling.preview.Preview(
    name = "Deck A",
    showBackground = true,
    widthDp = 700,
    heightDp = 420,
)
@Composable
private fun DeckViewPreview() {
    DJTheme {
        DeckView(
            name = "A",
            full = DeckFullState(
                controls = DeckControls(track = null),
                engine = com.dtp.spin.audio.DeckState(index = 0),
            ),
            accent = DeckAColor,
            onTogglePlay = {},
            onCueDown = {},
            onCueUp = {},
            onSetCue = {},
            onSeek = {},
            onScrubPreview = {},
            onTempoSlider = {},
            onTempoNudge = {},
            onTempoReset = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Post-gain peak meter, 0..1. */
@Composable
private fun LevelMeter(level: Float, accent: Color, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .width(8.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF0A0D10)),
    ) {
        val h = size.height * level.coerceIn(0f, 1f)
        drawRect(
            color = if (level > 0.95f) Color.Red else accent,
            topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - h),
            size = androidx.compose.ui.geometry.Size(size.width, h),
        )
    }
}
