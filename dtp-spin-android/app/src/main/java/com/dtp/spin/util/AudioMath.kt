package com.dtp.spin.util

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Pure-Kotlin DSP math shared by the UI and unit tests.
 * Must mirror the native Mixer::equalPowerGains — keep both in sync.
 */
object AudioMath {

    /** Equal-power crossfade gains. [crossfade] in [-1, +1]; -1 = full A. */
    fun equalPowerGains(crossfade: Float): Pair<Float, Float> {
        val t = (((crossfade + 1f) / 2f).coerceIn(0f, 1f) * (PI / 2)).toFloat()
        return cos(t) to sin(t)
    }

    fun dbToLinear(db: Float): Float = 10f.pow(db / 20f)

    fun linearToDb(linear: Float): Float =
        if (linear <= 0f) Float.NEGATIVE_INFINITY else 20f * kotlin.math.log10(linear)

    /** Tempo slider position [-1, +1] maps to rate with [range] (e.g. 0.08 = ±8%). */
    fun tempoToRate(slider: Float, range: Float = 0.08f): Float =
        (1f + slider.coerceIn(-1f, 1f) * range).coerceIn(0.25f, 2f)

    fun rateToTempo(rate: Float, range: Float = 0.08f): Float =
        ((rate - 1f) / range).coerceIn(-1f, 1f)
}
