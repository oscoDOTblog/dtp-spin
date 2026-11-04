package org.mixxx.mobile.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MixerMathTest {
    @Test
    fun centerCrossfadeIsEqualPower() {
        val (left, right) = MixerMath.crossfadeGains(0.5f)
        assertEquals(left, right, 1e-4f)
        // Equal-power: gains ~0.707 at center, constant power sum ~1.
        assertEquals(1f, left * left + right * right, 1e-3f)
    }

    @Test
    fun hardLeftMutesRight() {
        val (left, right) = MixerMath.crossfadeGains(0f)
        assertEquals(1f, left, 1e-4f)
        assertEquals(0f, right, 1e-4f)
    }

    @Test
    fun hardRightMutesLeft() {
        val (left, right) = MixerMath.crossfadeGains(1f)
        assertEquals(0f, left, 1e-4f)
        assertEquals(1f, right, 1e-4f)
    }

    @Test
    fun deckOutputGainMultipliesStages() {
        assertEquals(0.4f, MixerMath.deckOutputGain(1f, 0.5f, 0.8f), 1e-4f)
        assertEquals(0f, MixerMath.deckOutputGain(0f, 1f, 1f), 1e-4f)
    }

    @Test
    fun outOfRangeInputsAreClamped() {
        val (left, right) = MixerMath.crossfadeGains(2f)
        assertEquals(0f, left, 1e-4f)
        assertEquals(1f, right, 1e-4f)
        assertTrue(MixerMath.clampEqBand(5f) == 2f)
        assertTrue(MixerMath.clampEqBand(-1f) == 0f)
    }
}
