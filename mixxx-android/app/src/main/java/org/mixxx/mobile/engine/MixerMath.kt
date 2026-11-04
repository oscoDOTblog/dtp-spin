package org.mixxx.mobile.engine

import kotlin.math.PI
import kotlin.math.cos

/**
 * Pure mix math, mirroring Mixxx crossfader behavior.
 *
 * All functions are side-effect free so they can be unit-tested on JVM.
 */
object MixerMath {

    /**
     * Equal-power crossfade gains for [crossfader] in 0..1.
     * Returns Pair(leftGain, rightGain). Uses cosine taper so perceived
     * loudness stays roughly constant through the middle.
     */
    fun crossfadeGains(crossfader: Float): Pair<Float, Float> {
        val x = crossfader.coerceIn(0f, 1f)
        val left = cos(x * PI.toFloat() / 2f)
        val right = cos((1f - x) * PI.toFloat() / 2f)
        return Pair(left, right)
    }

    /** Final per-deck output gain: deck gain * crossfade side * master. */
    fun deckOutputGain(deckGain: Float, crossfadeSide: Float, master: Float): Float =
        deckGain.coerceIn(0f, 2f) * crossfadeSide.coerceIn(0f, 1f) * master.coerceIn(0f, 1f)

    /** Clamp an EQ band to its 0..2 range. */
    fun clampEqBand(value: Float): Float = value.coerceIn(0f, 2f)
}
