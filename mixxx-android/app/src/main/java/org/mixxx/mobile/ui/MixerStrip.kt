package org.mixxx.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mixxx.mobile.model.DeckId
import org.mixxx.mobile.model.DeckState
import org.mixxx.mobile.model.MixerState

@Composable
fun MixerStrip(
    mixer: MixerState,
    left: DeckState,
    right: DeckState,
    onCrossfader: (Float) -> Unit,
    onMaster: (Float) -> Unit,
    onGain: (DeckId, Float) -> Unit,
    onEq: (DeckId, Float, Float, Float) -> Unit,
    onEffectWet: (DeckId, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier.fillMaxWidth().padding(4.dp)) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Mixer", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                EqColumn("L EQ", left, { onGain(DeckId.LEFT, it) }) {
                        low, mid, high -> onEq(DeckId.LEFT, low, mid, high)
                }
                EqColumn("R EQ", right, { onGain(DeckId.RIGHT, it) }) {
                        low, mid, high -> onEq(DeckId.RIGHT, low, mid, high)
                }
            }
            Text("Crossfader", style = MaterialTheme.typography.labelSmall)
            Slider(value = mixer.crossfader, onValueChange = onCrossfader)
            Text("Master", style = MaterialTheme.typography.labelSmall)
            Slider(value = mixer.masterVolume, onValueChange = onMaster)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                EffectKnob("L FX", left.effectWet) { onEffectWet(DeckId.LEFT, it) }
                EffectKnob("R FX", right.effectWet) { onEffectWet(DeckId.RIGHT, it) }
            }
        }
    }
}

@Composable
private fun EqColumn(
    label: String,
    deck: DeckState,
    onGain: (Float) -> Unit,
    onEq: (Float, Float, Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Slider(value = deck.gain, onValueChange = onGain, valueRange = 0f..2f)
        Slider(
            value = deck.eq.first, onValueChange = { onEq(it, deck.eq.second, deck.eq.third) },
            valueRange = 0f..2f
        )
        Slider(
            value = deck.eq.second, onValueChange = { onEq(deck.eq.first, it, deck.eq.third) },
            valueRange = 0f..2f
        )
        Slider(
            value = deck.eq.third, onValueChange = { onEq(deck.eq.first, deck.eq.second, it) },
            valueRange = 0f..2f
        )
    }
}

@Composable
private fun EffectKnob(label: String, wet: Float, onWet: (Float) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Slider(value = wet, onValueChange = onWet)
    }
}
