package com.dtp.spin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Mixer middle strip: per-deck gain + HIGH/MID/LOW + channel volume, and the
 * equal-power crossfader (plan Phase 3 layout, -1..+1).
 */
@Composable
fun MixerView(
    deckA: DeckFullState,
    deckB: DeckFullState,
    crossfade: Float,
    onGainDb: (deck: Int, db: Float) -> Unit,
    onEq: (deck: Int, low: Float, mid: Float, high: Float) -> Unit,
    onVolume: (deck: Int, volume: Float) -> Unit,
    onCrossfade: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("MIXER", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ChannelStrip(
                full = deckA,
                accent = DeckAColor,
                onGainDb = { onGainDb(0, it) },
                onEq = { l, m, h -> onEq(0, l, m, h) },
                onVolume = { onVolume(0, it) },
            )
            ChannelStrip(
                full = deckB,
                accent = DeckBColor,
                onGainDb = { onGainDb(1, it) },
                onEq = { l, m, h -> onEq(1, l, m, h) },
                onVolume = { onVolume(1, it) },
            )
        }
        Spacer(Modifier.height(4.dp))
        Crossfader(
            value = crossfade,
            onChange = onCrossfade,
            modifier = Modifier,
        )
    }
}

@Composable
private fun ChannelStrip(
    full: DeckFullState,
    accent: androidx.compose.ui.graphics.Color,
    onGainDb: (Float) -> Unit,
    onEq: (low: Float, mid: Float, high: Float) -> Unit,
    onVolume: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = full.controls
    Row(modifier = modifier.verticalScroll(rememberScrollState()), verticalAlignment = Alignment.Bottom) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            EQKnob(
                valueDb = c.gainDb,
                label = "GAIN",
                onChange = onGainDb,
                accent = accent,
            )
            EQKnob(
                valueDb = c.eqHighDb,
                label = "HIGH",
                onChange = { onEq(c.eqLowDb, c.eqMidDb, it) },
                accent = accent,
            )
            EQKnob(
                valueDb = c.eqMidDb,
                label = "MID",
                onChange = { onEq(c.eqLowDb, it, c.eqHighDb) },
                accent = accent,
            )
            EQKnob(
                valueDb = c.eqLowDb,
                label = "LOW",
                onChange = { onEq(it, c.eqMidDb, c.eqHighDb) },
                accent = accent,
            )
        }
        ChannelFader(value = c.volume, label = "VOL", onChange = onVolume, accent = accent)
    }
}
