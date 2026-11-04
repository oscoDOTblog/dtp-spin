package org.mixxx.mobile.model

/** Metadata for one library track. BPM is nullable until analyzed/tapped. */
data class TrackMeta(
    val id: Long,
    val title: String,
    val artist: String,
    val uri: String,
    val durationMs: Long,
    val bpm: Double? = null
)
