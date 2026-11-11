package com.dtp.spin.audio

import java.nio.ByteBuffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory engine double for unit tests and Compose previews. */
class FakeAudioEngine(
    initial: AudioEngineState = AudioEngineState(),
) : AudioEngine {
    private val _state = MutableStateFlow(initial)
    override val state: StateFlow<AudioEngineState> = _state.asStateFlow()

    val calls = mutableListOf<String>()
    var feedResult = 1024
    private var gen = 0

    private fun mutateDeck(index: Int, f: (DeckState) -> DeckState) {
        val cur = _state.value
        _state.value = if (index == 0) cur.copy(deckA = f(cur.deckA))
        else cur.copy(deckB = f(cur.deckB))
    }

    override fun start(): Boolean {
        _state.value = _state.value.copy(streamRunning = true)
        return true
    }

    override fun shutdown() {
        _state.value = _state.value.copy(streamRunning = false)
    }

    override fun loadDeck(deck: Int, sourceSampleRateHz: Float, totalFrames: Long, generation: Int) {
        calls += "loadDeck($deck)"
        mutateDeck(deck) {
            it.copy(
                durationMs = (totalFrames / sourceSampleRateHz * 1000).toLong(),
                positionMs = 0L, ended = false, generation = generation,
            )
        }
    }

    override fun feedPcm(
        deck: Int, buffer: ByteBuffer, frames: Int, generation: Int, absStartFrame: Long,
    ): Int = feedResult
    override fun nextGeneration(deck: Int): Int = ++gen

    override fun setDeckTrackId(deck: Int, trackId: Long?) {
        mutateDeck(deck) { it.copy(trackId = trackId) }
    }

    override fun play(deck: Int) {
        calls += "play($deck)"
        mutateDeck(deck) { it.copy(playing = true, ended = false) }
    }

    override fun pause(deck: Int) {
        calls += "pause($deck)"
        mutateDeck(deck) { it.copy(playing = false) }
    }

    override fun cueDown(deck: Int) {
        calls += "cueDown($deck)"
    }

    override fun cueUp(deck: Int) {
        calls += "cueUp($deck)"
    }

    override fun setCue(deck: Int, cueFrame: Long) {
        calls += "setCue($deck,$cueFrame)"
    }

    override fun seek(deck: Int, targetFrame: Long, generation: Int) {
        calls += "seek($deck,$targetFrame)"
    }

    override fun setRate(deck: Int, rate: Float) {
        calls += "setRate($deck,$rate)"
    }

    override fun setVolume(deck: Int, volume: Float) {
        calls += "setVolume($deck,$volume)"
    }

    override fun setGain(deck: Int, gainLinear: Float) {
        calls += "setGain($deck,$gainLinear)"
    }

    override fun setEq(deck: Int, lowDb: Float, midDb: Float, highDb: Float) {
        calls += "setEq"
    }

    override fun setCrossfade(crossfade: Float) {
        calls += "setCrossfade($crossfade)"
        _state.value = _state.value.copy(crossfade = crossfade)
    }
}
