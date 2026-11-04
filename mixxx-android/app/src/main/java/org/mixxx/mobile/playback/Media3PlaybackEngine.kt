package org.mixxx.mobile.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import org.mixxx.mobile.effects.DjEffect
import org.mixxx.mobile.engine.MixerMath
import org.mixxx.mobile.model.DeckId
import org.mixxx.mobile.model.TrackMeta

/**
 * Media3-backed MVP engine: one ExoPlayer per deck.
 *
 * Crossfade/gain staging is applied as per-player volume; EQ and effects
 * are parameter stubs in MVP (stored, applied where the platform allows)
 * so the UI contract stays stable when the Oboe native mixer lands.
 */
class Media3PlaybackEngine(context: Context) : PlaybackEngine {
    private val players: Map<DeckId, ExoPlayer> = mapOf(
        DeckId.LEFT to ExoPlayer.Builder(context).build(),
        DeckId.RIGHT to ExoPlayer.Builder(context).build()
    )
    private val gains = mutableMapOf(DeckId.LEFT to 1f, DeckId.RIGHT to 1f)
    private val tempos = mutableMapOf(DeckId.LEFT to 1.0, DeckId.RIGHT to 1.0)
    private var crossfader = 0.5f
    private var master = 0.8f

    override fun loadTrack(deck: DeckId, track: TrackMeta) {
        val player = players.getValue(deck)
        player.setMediaItem(MediaItem.fromUri(track.uri))
        player.prepare()
        applyTempo(deck)
    }

    override fun play(deck: DeckId) {
        players.getValue(deck).play()
    }

    override fun pause(deck: DeckId) {
        players.getValue(deck).pause()
    }

    override fun cue(deck: DeckId) {
        val player = players.getValue(deck)
        player.pause()
        player.seekTo(0)
    }

    override fun seekTo(deck: DeckId, positionMs: Long) {
        players.getValue(deck).seekTo(positionMs.coerceAtLeast(0))
    }

    override fun setTempo(deck: DeckId, tempo: Double) {
        tempos[deck] = tempo.coerceIn(0.92, 1.08)
        applyTempo(deck)
    }

    private fun applyTempo(deck: DeckId) {
        val speed = tempos.getValue(deck).toFloat()
        players.getValue(deck).playbackParameters = PlaybackParameters(speed)
    }

    override fun setGain(deck: DeckId, gain: Float) {
        gains[deck] = gain
        applyVolumes()
    }

    override fun setEq(deck: DeckId, low: Float, mid: Float, high: Float) {
        // MVP: EQ bands stored in DeckState; platform EQ (LoudnessEnhancer /
        // Equalizer) wiring lands with the native mixer. No-op by design.
    }

    override fun setCrossfader(position: Float) {
        crossfader = position.coerceIn(0f, 1f)
        applyVolumes()
    }

    override fun setMasterVolume(volume: Float) {
        master = volume.coerceIn(0f, 1f)
        applyVolumes()
    }

    private fun applyVolumes() {
        val (leftX, rightX) = MixerMath.crossfadeGains(crossfader)
        players.getValue(DeckId.LEFT).volume =
            MixerMath.deckOutputGain(gains.getValue(DeckId.LEFT), leftX, master)
        players.getValue(DeckId.RIGHT).volume =
            MixerMath.deckOutputGain(gains.getValue(DeckId.RIGHT), rightX, master)
    }

    override fun setEffect(deck: DeckId, effect: DjEffect) {
        // MVP: effect params stored in DeckState; DSP lands with native mixer.
    }

    override fun positionMs(deck: DeckId): Long =
        players.getValue(deck).currentPosition

    override fun isPlaying(deck: DeckId): Boolean =
        players.getValue(deck).isPlaying

    override fun release() {
        players.values.forEach { it.release() }
    }
}
