package org.mixxx.mobile.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TempoMathTest {
    @Test
    fun clampKeepsSliderRange() {
        assertEquals(1.08, TempoMath.clampTempo(2.0), 1e-9)
        assertEquals(0.92, TempoMath.clampTempo(0.1), 1e-9)
        assertEquals(1.0, TempoMath.clampTempo(1.0), 1e-9)
    }

    @Test
    fun effectiveBpmScalesWithTempo() {
        assertEquals(126.0, TempoMath.effectiveBpm(120.0, 1.05)!!, 1e-9)
        // Out-of-range tempo is clamped before scaling.
        assertEquals(129.6, TempoMath.effectiveBpm(120.0, 1.1)!!, 1e-9)
        assertNull(TempoMath.effectiveBpm(null, 1.0))
    }

    @Test
    fun syncMatchesLeaderBpm() {
        // 120 BPM leader, 100 BPM follower -> 1.2x, clamped to 1.08 max.
        assertEquals(1.08, TempoMath.syncTempoTo(120.0, 100.0)!!, 1e-9)
        // Exact match inside range.
        assertEquals(1.0, TempoMath.syncTempoTo(120.0, 120.0)!!, 1e-9)
        assertNull(TempoMath.syncTempoTo(null, 120.0))
        assertNull(TempoMath.syncTempoTo(120.0, null))
    }
}
