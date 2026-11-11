package com.dtp.spin.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Imported audio file. The app never copies music: [uri] is the SAF document
 * URI with a persisted read permission; files stay in place.
 */
@Entity(
    tableName = "tracks",
    indices = [Index(value = ["uri"], unique = true)],
)
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val sampleRateHz: Int,
    val channels: Int,
    val dateAddedMs: Long = System.currentTimeMillis(),
    /** Waveform overview: interleaved min/max pairs, [-1, 1]. Null until scanned. */
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val peaks: ByteArray? = null,
) {
    // ByteArray breaks data-class equality; compare by id/uri instead.
    override fun equals(other: Any?): Boolean =
        other is TrackEntity && other.id == id && other.uri == uri

    override fun hashCode(): Int = 31 * id.hashCode() + uri.hashCode()
}

/** Saved cue point (long-press Cue in the deck). */
@Entity(
    tableName = "cues",
    indices = [Index(value = ["trackId"])],
)
data class CueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val trackId: Long,
    val positionMs: Long,
    val label: String = "Cue",
)
