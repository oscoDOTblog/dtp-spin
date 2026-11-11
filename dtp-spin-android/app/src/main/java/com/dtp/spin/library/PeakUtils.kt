package com.dtp.spin.library

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

/**
 * Waveform-overview math: pure functions over PCM so they stay unit-testable.
 * Peaks are stored per track as interleaved min/max pairs in [-1, 1].
 */
object PeakUtils {
    const val BUCKET_COUNT = 1200

    /** Downsamples mono [samples] into interleaved min/max pairs. */
    fun computeBuckets(samples: FloatArray, buckets: Int = BUCKET_COUNT): FloatArray {
        if (samples.isEmpty() || buckets <= 0) return FloatArray(0)
        val out = FloatArray(buckets * 2)
        val perBucket = samples.size.toDouble() / buckets
        for (b in 0 until buckets) {
            val from = (b * perBucket).toInt()
            val to = max(from + 1, ((b + 1) * perBucket).toInt()).coerceAtMost(samples.size)
            var mn = Float.MAX_VALUE
            var mx = -Float.MAX_VALUE
            for (i in from until to) {
                val s = samples[i]
                if (s < mn) mn = s
                if (s > mx) mx = s
            }
            if (mn == Float.MAX_VALUE) {
                mn = 0f
                mx = 0f
            }
            out[b * 2] = mn
            out[b * 2 + 1] = mx
        }
        return out
    }

    fun encode(peaks: FloatArray): ByteArray {
        val buf = ByteBuffer.allocate(peaks.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        buf.asFloatBuffer().put(peaks)
        return buf.array()
    }

    fun decode(blob: ByteArray?): FloatArray? {
        if (blob == null || blob.size % 4 != 0) return null
        val out = FloatArray(blob.size / 4)
        ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(out)
        return out
    }
}
