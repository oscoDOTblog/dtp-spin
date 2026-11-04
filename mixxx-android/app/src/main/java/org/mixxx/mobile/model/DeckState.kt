package org.mixxx.mobile.model

enum class DeckId { LEFT, RIGHT }

/** UI-observable state for one deck. Engine owns position/playing truth. */
data class DeckState(
    val id: DeckId,
    val track: TrackMeta? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    /** Tempo slider: 1.0 = original tempo. Mixxx-style range ~0.92..1.08. */
    val tempo: Double = 1.0,
    val syncEnabled: Boolean = false,
    val hotcues: Map<Int, CuePoint> = emptyMap(),
    val loop: LoopState = LoopState(),
    /** Per-deck effect wet mix 0..1. */
    val effectWet: Float = 0f,
    /** 3-band EQ gains: Triple(low, mid, high), each 0..2 (1 = unity). */
    val eq: Triple<Float, Float, Float> = Triple(1f, 1f, 1f),
    val gain: Float = 1f
)
