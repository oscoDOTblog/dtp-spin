package com.dtp.spin.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeakUtilsTest {

    @Test
    fun emptyOrNull_roundTrip() {
        assertEquals(0, PeakUtils.computeBuckets(FloatArray(0)).size)
        assertNull(PeakUtils.decode(null))
        assertNull(PeakUtils.decode(ByteArray(3)))
    }

    @Test
    fun buckets_captureMinMax() {
        val samples = FloatArray(1000) { i -> if (i % 2 == 0) 0.5f else -0.25f }
        val peaks = PeakUtils.computeBuckets(samples, 10)
        assertEquals(20, peaks.size)
        for (b in 0 until 10) {
            assertEquals(-0.25f, peaks[b * 2], 1e-6f)
            assertEquals(0.5f, peaks[b * 2 + 1], 1e-6f)
        }
    }

    @Test
    fun encodeDecode_roundTrips() {
        val peaks = floatArrayOf(-0.5f, 0.5f, -0.1f, 0.9f)
        val decoded = PeakUtils.decode(PeakUtils.encode(peaks))
        assertNotNull(decoded)
        assertEquals(peaks.toList(), decoded!!.toList())
    }

    @Test
    fun singleSpike_landsInOneBucket() {
        val samples = FloatArray(1200)
        samples[600] = 1f
        samples[601] = -1f
        val peaks = PeakUtils.computeBuckets(samples, 12)
        var hot = 0
        for (b in 0 until 12) {
            if (peaks[b * 2 + 1] > 0.9f || peaks[b * 2] < -0.9f) hot++
        }
        assertTrue("spike should land in exactly one bucket, landed in $hot", hot == 1)
    }
}
