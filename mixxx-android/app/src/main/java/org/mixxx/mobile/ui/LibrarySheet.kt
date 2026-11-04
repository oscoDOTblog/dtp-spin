package org.mixxx.mobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mixxx.mobile.model.DeckId
import org.mixxx.mobile.model.TrackMeta

@Composable
fun LibrarySheet(
    tracks: List<TrackMeta>,
    onLoad: (DeckId, TrackMeta) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth().padding(4.dp)) {
        Text("Library", style = MaterialTheme.typography.titleSmall)
        LazyColumn {
            items(tracks, key = { it.id }) { track ->
                Card(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Column(Modifier.padding(8.dp)) {
                        Text("${track.artist} - ${track.title}")
                        Text(
                            "BPM ${track.bpm ?: "--"} | ${track.durationMs / 1000}s",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "Load L",
                            Modifier.clickable { onLoad(DeckId.LEFT, track) }
                                .padding(4.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Load R",
                            Modifier.clickable { onLoad(DeckId.RIGHT, track) }
                                .padding(4.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
