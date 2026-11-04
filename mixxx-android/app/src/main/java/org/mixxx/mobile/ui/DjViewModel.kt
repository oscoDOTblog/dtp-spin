package org.mixxx.mobile.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.mixxx.mobile.engine.TempoMath
import org.mixxx.mobile.model.CuePoint
import org.mixxx.mobile.model.DeckId
import org.mixxx.mobile.model.DeckState
import org.mixxx.mobile.model.LoopState
import org.mixxx.mobile.model.MixerState
import org.mixxx.mobile.model.TrackMeta
import org.mixxx.mobile.playback.Media3PlaybackEngine
import org.mixxx.mobile.playback.PlaybackEngine

data class DjUiState(
    val left: DeckState = DeckState(DeckId.LEFT),
    val right: DeckState = DeckState(DeckId.RIGHT),
    val mixer: MixerState = MixerState(),
    val library: List<TrackMeta> = listOf(
        TrackMeta(1, "Demo Track A", "Mixxx Mobile", "", 180_000, 124.0),
        TrackMeta(2, "Demo Track B", "Mixxx Mobile", "", 210_000, 122.0)
    )
)

/** Owns deck/mixer state; delegates audio to [PlaybackEngine]. */
class DjViewModel(app: Application) : AndroidViewModel(app) {
    private var engine: PlaybackEngine? = null
    private var positionPoller: Job? = null

    private val _state = MutableStateFlow(DjUiState())
    val state: StateFlow<DjUiState> = _state.asStateFlow()

    fun attachEngine(e: PlaybackEngine) {
        engine = e
        startPositionPolling()
    }

    fun ensureEngine() {
        if (engine == null) attachEngine(Media3PlaybackEngine(getApplication()))
    }

    private fun deck(id: DeckId): DeckState =
        if (id == DeckId.LEFT) _state.value.left else _state.value.right

    private fun updateDeck(id: DeckId, f: (DeckState) -> DeckState) {
        _state.update {
            if (id == DeckId.LEFT) it.copy(left = f(it.left))
            else it.copy(right = f(it.right))
        }
    }

    fun loadTrack(id: DeckId, track: TrackMeta) {
        ensureEngine()
        if (track.uri.isNotEmpty()) engine?.loadTrack(id, track)
        updateDeck(id) { it.copy(track = track, positionMs = 0, isPlaying = false) }
    }

    fun togglePlay(id: DeckId) {
        ensureEngine()
        val d = deck(id)
        if (d.track == null) return
        if (d.isPlaying) {
            engine?.pause(id)
            updateDeck(id) { it.copy(isPlaying = false) }
        } else {
            engine?.play(id)
            updateDeck(id) { it.copy(isPlaying = true) }
        }
    }

    fun cue(id: DeckId) {
        engine?.cue(id)
        updateDeck(id) { it.copy(isPlaying = false, positionMs = 0) }
    }

    fun seek(id: DeckId, positionMs: Long) {
        engine?.seekTo(id, positionMs)
        updateDeck(id) { it.copy(positionMs = positionMs.coerceAtLeast(0)) }
    }

    fun setTempo(id: DeckId, tempo: Double) {
        val clamped = TempoMath.clampTempo(tempo)
        engine?.setTempo(id, clamped)
        updateDeck(id) { it.copy(tempo = clamped) }
    }

    fun toggleSync(id: DeckId) {
        val other = if (id == DeckId.LEFT) _state.value.right else _state.value.left
        val me = deck(id)
        val enabling = !me.syncEnabled
        updateDeck(id) { it.copy(syncEnabled = enabling) }
        if (enabling) {
            val t = TempoMath.syncTempoTo(other.track?.bpm, me.track?.bpm)
            if (t != null) setTempo(id, t)
        } else {
            setTempo(id, 1.0)
        }
    }

    fun setHotcue(id: DeckId, slot: Int, positionMs: Long) {
        updateDeck(id) {
            it.copy(hotcues = it.hotcues + (slot to CuePoint(slot, positionMs)))
        }
    }

    fun triggerHotcue(id: DeckId, slot: Int) {
        deck(id).hotcues[slot]?.let { seek(id, it.positionMs) }
    }

    fun setLoopIn(id: DeckId, positionMs: Long) {
        updateDeck(id) { it.copy(loop = it.loop.copy(startMs = positionMs)) }
    }

    fun setLoopOut(id: DeckId, positionMs: Long) {
        updateDeck(id) {
            val loop = it.loop.copy(endMs = positionMs, enabled = true)
            it.copy(loop = if (loop.isValid()) loop else loop.copy(enabled = false))
        }
    }

    fun clearLoop(id: DeckId) {
        updateDeck(id) { it.copy(loop = LoopState()) }
    }

    fun setGain(id: DeckId, gain: Float) {
        engine?.setGain(id, gain)
        updateDeck(id) { it.copy(gain = gain.coerceIn(0f, 2f)) }
    }

    fun setEq(id: DeckId, low: Float, mid: Float, high: Float) {
        engine?.setEq(id, low, mid, high)
        updateDeck(id) {
            it.copy(eq = Triple(low.coerceIn(0f, 2f), mid.coerceIn(0f, 2f), high.coerceIn(0f, 2f)))
        }
    }

    fun setCrossfader(v: Float) {
        engine?.setCrossfader(v)
        _state.update { it.copy(mixer = it.mixer.copy(crossfader = v.coerceIn(0f, 1f))) }
    }

    fun setMaster(v: Float) {
        engine?.setMasterVolume(v)
        _state.update { it.copy(mixer = it.mixer.copy(masterVolume = v.coerceIn(0f, 1f))) }
    }

    fun setEffectWet(id: DeckId, wet: Float) {
        updateDeck(id) { it.copy(effectWet = wet.coerceIn(0f, 1f)) }
    }

    private fun startPositionPolling() {
        positionPoller?.cancel()
        positionPoller = viewModelScope.launch {
            while (true) {
                delay(500)
                val e = engine ?: break
                _state.update {
                    it.copy(
                        left = it.left.copy(positionMs = e.positionMs(DeckId.LEFT)),
                        right = it.right.copy(positionMs = e.positionMs(DeckId.RIGHT))
                    )
                }
            }
        }
    }

    override fun onCleared() {
        positionPoller?.cancel()
        engine?.release()
    }
}

