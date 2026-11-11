package com.dtp.spin.audio

import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Production [AudioEngine]: owns the native engine handle, forwards control
 * commands, and publishes a ~25 Hz state snapshot polled off the audio
 * thread (the real-time callback never touches JNI).
 */
class NativeAudioEngine(
    private val scope: CoroutineScope,
) : AudioEngine {

    private val handle: Long = NativeLib.nativeCreate()
    private val _state = MutableStateFlow(AudioEngineState())
    override val state: StateFlow<AudioEngineState> = _state.asStateFlow()

    private val generations = arrayOf(AtomicInteger(0), AtomicInteger(0))
    private val sourceRates = FloatArray(2) { 0f }
    private val totalFrames = LongArray(2) { 0L }
    private val trackIds = arrayOf<Long?>(null, null)
    private var crossfade = 0f
    private var poller: Job? = null

    override fun start(): Boolean {
        val ok = NativeLib.nativeStart(handle)
        if (ok && poller == null) {
            poller = scope.launch {
                while (isActive) {
                    pollOnce()
                    delay(POLL_MS)
                }
            }
        }
        _state.value = _state.value.copy(streamRunning = ok)
        return ok
    }

    override fun shutdown() {
        poller?.cancel()
        poller = null
        runCatching { NativeLib.nativeStop(handle) }
        runCatching { NativeLib.nativeDestroy(handle) }
    }

    private fun pollOnce() {
        runCatching { NativeLib.nativePump(handle) }
        val sampleRate = runCatching { NativeLib.nativeSampleRate(handle) }.getOrDefault(48000.0)
        val cur = _state.value
        _state.value = AudioEngineState(
            deckA = readDeck(0, cur.deckA),
            deckB = readDeck(1, cur.deckB),
            crossfade = crossfade,
            sampleRateHz = sampleRate,
            streamRunning = cur.streamRunning,
        )
    }

    private fun readDeck(index: Int, prev: DeckState): DeckState {
        val posFrames = runCatching { NativeLib.nativeDeckGetPositionFrames(handle, index) }
            .getOrDefault(0.0)
        val rate = sourceRates[index]
        return DeckState(
            index = index,
            positionMs = if (rate > 0) (posFrames / rate * 1000).toLong() else 0L,
            durationMs = if (rate > 0 && totalFrames[index] > 0)
                (totalFrames[index] / rate * 1000).toLong() else 0L,
            playing = runCatching { NativeLib.nativeDeckIsPlaying(handle, index) }
                .getOrDefault(false),
            starved = runCatching { NativeLib.nativeDeckIsStarved(handle, index) }
                .getOrDefault(false),
            ended = runCatching { NativeLib.nativeDeckHasEnded(handle, index) }
                .getOrDefault(false),
            level = runCatching { NativeLib.nativeDeckGetLevel(handle, index) }
                .getOrDefault(0f),
            trackId = trackIds[index],
            generation = runCatching { NativeLib.nativeDeckGetGeneration(handle, index) }
                .getOrDefault(prev.generation),
        )
    }

    override fun loadDeck(deck: Int, sourceSampleRateHz: Float, totalFrames: Long, generation: Int) {
        sourceRates[deck] = sourceSampleRateHz
        this.totalFrames[deck] = totalFrames
        NativeLib.nativeDeckLoad(handle, deck, sourceSampleRateHz, totalFrames, generation)
    }

    override fun setDeckTrackId(deck: Int, trackId: Long?) {
        trackIds[deck] = trackId
    }

    override fun feedPcm(
        deck: Int, buffer: ByteBuffer, frames: Int, generation: Int, absStartFrame: Long,
    ): Int =
        runCatching {
            NativeLib.nativeDeckFeedPcm(handle, deck, buffer, frames, generation, absStartFrame)
        }.getOrDefault(-1)

    override fun nextGeneration(deck: Int): Int = generations[deck].incrementAndGet()

    override fun play(deck: Int) = NativeLib.nativeDeckPlay(handle, deck)
    override fun pause(deck: Int) = NativeLib.nativeDeckPause(handle, deck)
    override fun cueDown(deck: Int) = NativeLib.nativeDeckCueDown(handle, deck)
    override fun cueUp(deck: Int) = NativeLib.nativeDeckCueUp(handle, deck)
    override fun setCue(deck: Int, cueFrame: Long) = NativeLib.nativeDeckSetCue(handle, deck, cueFrame)
    override fun seek(deck: Int, targetFrame: Long, generation: Int) =
        NativeLib.nativeDeckSeek(handle, deck, targetFrame, generation)

    override fun setRate(deck: Int, rate: Float) = NativeLib.nativeDeckSetRate(handle, deck, rate)
    override fun setVolume(deck: Int, volume: Float) =
        NativeLib.nativeDeckSetVolume(handle, deck, volume)
    override fun setGain(deck: Int, gainLinear: Float) =
        NativeLib.nativeDeckSetGain(handle, deck, gainLinear)
    override fun setEq(deck: Int, lowDb: Float, midDb: Float, highDb: Float) =
        NativeLib.nativeDeckSetEq(handle, deck, lowDb, midDb, highDb)
    override fun setCrossfade(crossfade: Float) {
        this.crossfade = crossfade
        NativeLib.nativeSetCrossfade(handle, crossfade)
    }

    companion object {
        private const val POLL_MS = 40L // ~25 Hz state publish
    }
}
