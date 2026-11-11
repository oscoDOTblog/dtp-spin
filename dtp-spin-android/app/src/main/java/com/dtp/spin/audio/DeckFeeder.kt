package com.dtp.spin.audio

/** Streaming-feed contract for one deck; production impl is [TrackDecoder]. */
interface DeckFeeder {
    var onEvent: ((TrackDecoder.Event) -> Unit)?
    val isRunning: Boolean
    fun load(uriString: String, generation: Int, startPositionMs: Long)
    fun seekTo(positionMs: Long, generation: Int)
    fun stop()
}
