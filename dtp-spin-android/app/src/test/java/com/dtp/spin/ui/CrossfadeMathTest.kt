package com.dtp.spin.ui

import com.dtp.spin.audio.AudioEngineState
import com.dtp.spin.audio.DeckState
import com.dtp.spin.util.AudioMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests for [AudioMath]: the equal-power crossfader shared by the
 * UI, these tests, and the native Mixer (keep all three in sync), plus the
 * tempo slider mapping.
 */
class CrossfadeMathTest {

    @Test
    fun hardLeft_isFullA_silentB() {
        val (a, b) = AudioMath.equalPowerGains(-1f)
        assertEquals(1f, a, 1e-6f)
        assertEquals(0f, b, 1e-6f)
    }

    @Test
    fun hardRight_isFullB_silentA() {
        val (a, b) = AudioMath.equalPowerGains(1f)
        assertEquals(0f, a, 1e-6f)
        assertEquals(1f, b, 1e-6f)
    }

    @Test
    fun center_isEqualPower() {
        val (a, b) = AudioMath.equalPowerGains(0f)
        assertEquals(a, b, 1e-6f)
        // Equal power: a² + b² = 1 so loudness holds through the transition.
        assertEquals(1f, a * a + b * b, 1e-5f)
    }

    @Test
    fun constantPower_acrossSweep() {
        var x = -1f
        while (x <= 1f) {
            val (a, b) = AudioMath.equalPowerGains(x)
            assertEquals("power dips at xfade=$x", 1f, a * a + b * b, 1e-5f)
            x += 0.05f
        }
    }

    @Test
    fun outOfRange_clamps() {
        val (a, _) = AudioMath.equalPowerGains(-2f)
        assertEquals(1f, a, 1e-6f)
        val (_, b) = AudioMath.equalPowerGains(2f)
        assertEquals(1f, b, 1e-6f)
    }

    @Test
    fun engineState_deckAccessor() {
        val st = AudioEngineState(deckA = DeckState(0), deckB = DeckState(1))
        assertEquals(0, st.deck(0).index)
        assertEquals(1, st.deck(1).index)
    }

    @Test
    fun tempoMapping_centerIsUnity() {
        assertEquals(1f, AudioMath.tempoToRate(0f), 1e-6f)
        assertEquals(0f, AudioMath.rateToTempo(1f), 1e-6f)
    }

    @Test
    fun tempoMapping_fullScaleIsPlus8Percent() {
        assertEquals(1.08f, AudioMath.tempoToRate(1f), 1e-6f)
        assertEquals(0.92f, AudioMath.tempoToRate(-1f), 1e-6f)
    }

    @Test
    fun tempoMapping_roundTrips() {
        val rate = AudioMath.tempoToRate(0.5f)
        assertEquals(0.5f, AudioMath.rateToTempo(rate), 1e-4f)
    }

    @Test
    fun dbMapping_zeroDbIsUnity() {
        assertEquals(1f, AudioMath.dbToLinear(0f), 1e-6f)
        assertTrue(AudioMath.dbToLinear(-24f) < 0.07f)
        assertTrue(AudioMath.dbToLinear(6f) > 1.9f)
    }
}
