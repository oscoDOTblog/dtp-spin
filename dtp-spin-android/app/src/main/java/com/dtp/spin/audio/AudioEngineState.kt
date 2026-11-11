package com.dtp.spin.audio

/**
 * Measured per-deck engine state, published at ~25 Hz by [AudioEngine.state].
 * Positions are derived from native frame counters; control parameters
 * (volume/EQ/tempo) live in the ViewModel and are pushed down to the engine.
 */
data class DeckState(
    val index: Int,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playing: Boolean = false,
    val starved: Boolean = false,
    val ended: Boolean = false,
    /** 0..1-ish post-gain peak for the level meter. */
    val level: Float = 0f,
    val trackId: Long? = null,
    val generation: Int = 0,
)

data class AudioEngineState(
    val deckA: DeckState = DeckState(0),
    val deckB: DeckState = DeckState(1),
    val crossfade: Float = 0f,
    val sampleRateHz: Double = 48000.0,
    val streamRunning: Boolean = false,
) {
    fun deck(index: Int): DeckState = if (index == 0) deckA else deckB
}
