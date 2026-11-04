package org.mixxx.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mixxx.mobile.engine.TempoMath
import org.mixxx.mobile.model.DeckState

@Composable
fun DeckCard(
    deck: DeckState,
    onTogglePlay: () -> Unit,
    onCue: () -> Unit,
    onTempo: (Double) -> Unit,
    onToggleSync: () -> Unit,
    onLoopIn: () -> Unit,
    onLoopOut: () -> Unit,
    onLoopClear: () -> Unit,
    onHotcueSet: (Int) -> Unit,
    onHotcueTrigger: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier.fillMaxWidth().padding(4.dp)) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                deck.track?.title ?: "No track loaded",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                "BPM ${deck.track?.bpm?.let { TempoMath.effectiveBpm(it, deck.tempo) } ?: "--"} " +
                    "| tempo ${"%.1f".format((deck.tempo - 1) * 100)}%",
                style = MaterialTheme.typography.bodySmall
            )
            WaveformView(
                trackId = deck.track?.id,
                positionMs = deck.positionMs,
                durationMs = deck.track?.durationMs ?: 0L
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onTogglePlay) {
                    Text(if (deck.isPlaying) "Pause" else "Play")
                }
                Button(onClick = onCue) { Text("Cue") }
                Button(onClick = onToggleSync) {
                    Text(if (deck.syncEnabled) "Sync*" else "Sync")
                }
            }
            Text("Tempo", style = MaterialTheme.typography.labelSmall)
            Slider(
                value = deck.tempo.toFloat(),
                onValueChange = { onTempo(it.toDouble()) },
                valueRange = TempoMath.MIN_TEMPO.toFloat()..TempoMath.MAX_TEMPO.toFloat()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onLoopIn) { Text("Loop In") }
                Button(onClick = onLoopOut) { Text("Loop Out") }
                Button(onClick = onLoopClear) { Text("Clear") }
            }
            Text(
                "Loop: ${deck.loop.startMs ?: "--"} -> ${deck.loop.endMs ?: "--"}" +
                    if (deck.loop.enabled) " (on)" else "",
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (slot in 0..2) {
                    Button(onClick = { onHotcueTrigger(slot) }) {
                        Text("H${slot + 1}")
                    }
                }
                for (slot in 0..2) {
                    Button(onClick = { onHotcueSet(slot) }) {
                        Text("S${slot + 1}")
                    }
                }
            }
        }
    }
}
