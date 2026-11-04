package org.mixxx.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.mixxx.mobile.model.DeckId

@Composable
fun DjScreen(vm: DjViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.padding(8.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DeckCard(
                    deck = state.left,
                    onTogglePlay = { vm.togglePlay(DeckId.LEFT) },
                    onCue = { vm.cue(DeckId.LEFT) },
                    onTempo = { vm.setTempo(DeckId.LEFT, it) },
                    onToggleSync = { vm.toggleSync(DeckId.LEFT) },
                    onLoopIn = { vm.setLoopIn(DeckId.LEFT, state.left.positionMs) },
                    onLoopOut = { vm.setLoopOut(DeckId.LEFT, state.left.positionMs) },
                    onLoopClear = { vm.clearLoop(DeckId.LEFT) },
                    onHotcueSet = { vm.setHotcue(DeckId.LEFT, it, state.left.positionMs) },
                    onHotcueTrigger = { vm.triggerHotcue(DeckId.LEFT, it) },
                    modifier = Modifier.weight(1f)
                )
                DeckCard(
                    deck = state.right,
                    onTogglePlay = { vm.togglePlay(DeckId.RIGHT) },
                    onCue = { vm.cue(DeckId.RIGHT) },
                    onTempo = { vm.setTempo(DeckId.RIGHT, it) },
                    onToggleSync = { vm.toggleSync(DeckId.RIGHT) },
                    onLoopIn = { vm.setLoopIn(DeckId.RIGHT, state.right.positionMs) },
                    onLoopOut = { vm.setLoopOut(DeckId.RIGHT, state.right.positionMs) },
                    onLoopClear = { vm.clearLoop(DeckId.RIGHT) },
                    onHotcueSet = { vm.setHotcue(DeckId.RIGHT, it, state.right.positionMs) },
                    onHotcueTrigger = { vm.triggerHotcue(DeckId.RIGHT, it) },
                    modifier = Modifier.weight(1f)
                )
            }
            MixerStrip(
                mixer = state.mixer,
                left = state.left,
                right = state.right,
                onCrossfader = vm::setCrossfader,
                onMaster = vm::setMaster,
                onGain = vm::setGain,
                onEq = vm::setEq,
                onEffectWet = vm::setEffectWet
            )
            LibrarySheet(tracks = state.library, onLoad = vm::loadTrack)
        }
    }
}
