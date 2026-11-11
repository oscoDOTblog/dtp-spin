package com.dtp.spin.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Landscape tablet layout (plan Phase 3): decks on top with the mixer between
 * them, collapsible library at the bottom.
 */
@Composable
fun DJScreen(viewModel: DJViewModel, modifier: Modifier = Modifier) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var libraryOpen by remember { mutableStateOf(true) }

    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.importUris(uris)
    }

    LaunchedEffect(ui.message) {
        ui.message?.let {
            snackbar.showSnackbar(it.text)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            // Top bar.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("DTP SPIN", fontSize = 18.sp, color = MaterialTheme.colorScheme.onBackground)
                if (!ui.streamRunning) {
                    Text(
                        "audio off",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (ui.restoring) {
                    LinearProgressIndicator(modifier = Modifier.width(120.dp))
                }
                TextButton(onClick = { libraryOpen = !libraryOpen }) {
                    Text(if (libraryOpen) "Hide library" else "Library")
                }
                Button(onClick = { importer.launch(arrayOf("audio/*")) }) {
                    Text("Import")
                }
            }

            // Decks + mixer.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DeckView(
                    name = "A",
                    full = ui.deckA,
                    accent = DeckAColor,
                    onTogglePlay = { viewModel.togglePlay(0) },
                    onCueDown = { viewModel.cueDown(0) },
                    onCueUp = { viewModel.cueUp(0) },
                    onSetCue = { viewModel.setCueAtCurrent(0) },
                    onSeek = { viewModel.seekTo(0, it) },
                    onScrubPreview = { viewModel.scrubPreview(0, it) },
                    onTempoSlider = { viewModel.setTempoSlider(0, it) },
                    onTempoNudge = { viewModel.nudgeTempo(0, it) },
                    onTempoReset = { viewModel.setTempoSlider(0, 0f) },
                    modifier = Modifier.weight(1f),
                )
                MixerView(
                    deckA = ui.deckA,
                    deckB = ui.deckB,
                    crossfade = ui.crossfade,
                    onGainDb = { deck, db -> viewModel.setGainDb(deck, db) },
                    onEq = { deck, l, m, h -> viewModel.setEq(deck, l, m, h) },
                    onVolume = { deck, v -> viewModel.setVolume(deck, v) },
                    onCrossfade = { viewModel.setCrossfade(it) },
                    modifier = Modifier.width(220.dp),
                )
                DeckView(
                    name = "B",
                    full = ui.deckB,
                    accent = DeckBColor,
                    onTogglePlay = { viewModel.togglePlay(1) },
                    onCueDown = { viewModel.cueDown(1) },
                    onCueUp = { viewModel.cueUp(1) },
                    onSetCue = { viewModel.setCueAtCurrent(1) },
                    onSeek = { viewModel.seekTo(1, it) },
                    onScrubPreview = { viewModel.scrubPreview(1, it) },
                    onTempoSlider = { viewModel.setTempoSlider(1, it) },
                    onTempoNudge = { viewModel.nudgeTempo(1, it) },
                    onTempoReset = { viewModel.setTempoSlider(1, 0f) },
                    modifier = Modifier.weight(1f),
                )
            }

            // Library bottom sheet (collapsible).
            if (libraryOpen) {
                Spacer(Modifier.height(4.dp))
                LibraryView(
                    tracks = ui.tracks,
                    query = ui.query,
                    onQuery = { viewModel.setQuery(it) },
                    onLoadToDeck = { deck, track -> viewModel.loadToDeck(deck, track) },
                    onDelete = { viewModel.deleteTrack(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true),
                )
            }
        }
    }
}


