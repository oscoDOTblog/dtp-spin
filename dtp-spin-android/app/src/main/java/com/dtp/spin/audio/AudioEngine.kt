package com.dtp.spin.audio

import java.nio.ByteBuffer
import kotlinx.coroutines.flow.StateFlow

/**
 * UI-facing audio engine contract. The production implementation
 * ([NativeAudioEngine]) drives the C++ Oboe engine; tests and Compose
 * previews use [FakeAudioEngine]. Deck indices: 0 = A, 1 = B.
 */
interface AudioEngine {
    val state: StateFlow<AudioEngineState>

    /** Opens/starts the output stream. Returns false when unavailable. */
    fun start(): Boolean
    fun shutdown()

    /** generation: monotonic token from [nextGeneration]; stale feeds are dropped natively. */
    fun loadDeck(deck: Int, sourceSampleRateHz: Float, totalFrames: Long, generation: Int)

    /** Associates a loaded library track with a deck for state reporting. */
    fun setDeckTrackId(deck: Int, trackId: Long?)

    /**
     * Feeds interleaved stereo float32 PCM ([buffer] must be direct) starting at
     * absolute source frame [absStartFrame] (the decoder tracks its head; seeks
     * jump it). Returns frames accepted, -1 if stale.
     */
    fun feedPcm(deck: Int, buffer: ByteBuffer, frames: Int, generation: Int, absStartFrame: Long): Int

    fun nextGeneration(deck: Int): Int

    fun play(deck: Int)
    fun pause(deck: Int)
    fun cueDown(deck: Int)
    fun cueUp(deck: Int)
    fun setCue(deck: Int, cueFrame: Long)
    fun seek(deck: Int, targetFrame: Long, generation: Int)

    fun setRate(deck: Int, rate: Float)
    fun setVolume(deck: Int, volume: Float)
    fun setGain(deck: Int, gainLinear: Float)
    fun setEq(deck: Int, lowDb: Float, midDb: Float, highDb: Float)
    fun setCrossfade(crossfade: Float)
}
