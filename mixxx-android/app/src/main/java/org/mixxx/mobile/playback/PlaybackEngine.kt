package org.mixxx.mobile.playback

import org.mixxx.mobile.effects.DjEffect
import org.mixxx.mobile.model.DeckId
import org.mixxx.mobile.model.TrackMeta

/**
 * DJ playback contract for one app session (2 decks).
 *
 * MVP implementation is Media3/ExoPlayer ([Media3PlaybackEngine]).
 * A future Oboe/AAudio native mixer can implement this same interface
 * without touching the UI layer.
 */
interface PlaybackEngine {
    fun loadTrack(deck: DeckId, track: TrackMeta)
    fun play(deck: DeckId)
    fun pause(deck: DeckId)
    fun cue(deck: DeckId)
    fun seekTo(deck: DeckId, positionMs: Long)
    fun setTempo(deck: DeckId, tempo: Double)
    fun setGain(deck: DeckId, gain: Float)
    fun setEq(deck: DeckId, low: Float, mid: Float, high: Float)
    fun setCrossfader(position: Float)
    fun setMasterVolume(volume: Float)
    fun setEffect(deck: DeckId, effect: DjEffect)
    fun positionMs(deck: DeckId): Long
    fun isPlaying(deck: DeckId): Boolean
    fun release()
}
