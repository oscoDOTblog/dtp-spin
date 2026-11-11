package com.dtp.spin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dtp.spin.data.TrackEntity

/**
 * Searchable library with Load-to-A/B actions (Phase 2 contract).
 */
@Composable
fun LibraryView(
    tracks: List<TrackEntity>,
    query: String,
    onQuery: (String) -> Unit,
    onLoadToDeck: (deck: Int, track: TrackEntity) -> Unit,
    onDelete: (TrackEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        TextField(
            value = query,
            onValueChange = onQuery,
            placeholder = { Text("Search title, artist, album") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        )
        Spacer(Modifier.height(4.dp))
        if (tracks.isEmpty()) {
            Text(
                if (query.isBlank()) "Library is empty — use Import to add MP3/WAV files."
                else "No matches for “$query”.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(tracks, key = { it.id }) { track ->
                    TrackRow(
                        track = track,
                        onLoadToDeck = { onLoadToDeck(it, track) },
                        onDelete = { onDelete(track) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackRow(
    track: TrackEntity,
    onLoadToDeck: (deck: Int) -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "${track.artist} • ${DJViewModel.formatMs(track.durationMs)}" +
                    if (track.peaks == null) " • analyzing…" else "",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LoadButton(deckName = "A", color = DeckAColor, onClick = { onLoadToDeck(0) })
        Spacer(Modifier.width(2.dp))
        LoadButton(deckName = "B", color = DeckBColor, onClick = { onLoadToDeck(1) })
        IconButton(onClick = onDelete) {
            Text("✕", color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
        }
    }
}

@Composable
private fun LoadButton(deckName: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text("→$deckName", color = color, fontSize = 14.sp)
    }
}
