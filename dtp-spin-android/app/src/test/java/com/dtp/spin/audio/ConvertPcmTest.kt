package com.dtp.spin.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Channel-conversion math for the decode path: MediaCodec output
 * (16-bit/float, mono/stereo/multichannel) to interleaved stereo float32.
 * Framework encoding constants inlined (ENCODING_PCM_16BIT = 2, FLOAT = 4)
 * because android.* stubs are unavailable on JVM unit tests.
 */
class ConvertPcmTest {

    private fun collect(
        src: ByteBuffer, offset: Int, size: Int, channels: Int, encoding: Int,
    ): List<Float> {
        val out = FloatArray(64)
        val acc = mutableListOf<Float>()
        TrackDecoder.convertToStereoFloat(
            src, offset, size, channels, encoding, out,
        ) { off, frames ->
            for (i in 0 until frames * 2) acc += out[off + i]
        }
        return acc
    }

    @Test
    fun stereo16_roundTrips() {
        val buf = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder())
        buf.asShortBuffer().put(shortArrayOf(1000, -1000, 32767, -32768))
        val got = collect(buf, 0, 8, 2, 2)
        assertEquals(4, got.size)
        assertEquals(1000 / 32768f, got[0], 1e-6f)
        assertEquals(-1000 / 32768f, got[1], 1e-6f)
        assertEquals(32767 / 32768f, got[2], 1e-6f)
        assertEquals(-1f, got[3], 1e-6f)
    }

    @Test
    fun mono16_duplicatedToBothChannels() {
        val buf = ByteBuffer.allocate(2).order(ByteOrder.nativeOrder())
        buf.asShortBuffer().put(shortArrayOf(16384))
        val got = collect(buf, 0, 2, 1, 2)
        assertEquals(listOf(0.5f, 0.5f), got)
    }

    @Test
    fun stereoFloat_passthrough() {
        val buf = ByteBuffer.allocate(16).order(ByteOrder.nativeOrder())
        buf.asFloatBuffer().put(floatArrayOf(0.25f, -0.75f, 1.5f, -2f))
        val got = collect(buf, 0, 16, 2, 4)
        // Over-range samples are clamped to [-1, 1].
        assertEquals(listOf(0.25f, -0.75f, 1f, -1f), got)
    }

    @Test
    fun monoFloat_duplicated() {
        val buf = ByteBuffer.allocate(4).order(ByteOrder.nativeOrder())
        buf.asFloatBuffer().put(floatArrayOf(0.5f))
        val got = collect(buf, 0, 4, 1, 4)
        assertEquals(listOf(0.5f, 0.5f), got)
    }

    @Test
    fun quadDownmix_foldsExtraChannels() {
        val buf = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder())
        buf.asShortBuffer().put(shortArrayOf(32767, 0, 0, 0))
        val got = collect(buf, 0, 8, 4, 2)
        assertEquals(1f, got[0], 1e-3f)
        assertEquals(0f, got[1], 1e-6f)

        val buf2 = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder())
        buf2.asShortBuffer().put(shortArrayOf(0, 0, 32767, 32767))
        val got2 = collect(buf2, 0, 8, 4, 2)
        // Both extra channels fold in at 1/(channels-1) each: 1/3 + 1/3.
        assertEquals(2f / 3, got2[0], 1e-3f)
        assertEquals(2f / 3, got2[1], 1e-3f)
    }

    @Test
    fun offsetAndSize_sliceBuffer() {
        val buf = ByteBuffer.allocate(12).order(ByteOrder.nativeOrder())
        buf.asShortBuffer().put(shortArrayOf(0, 0, 16384, -16384, 999, 999))
        val got = collect(buf, 4, 4, 2, 2) // middle frame only
        assertEquals(listOf(0.5f, -0.5f), got)
    }

    @Test
    fun chunkedEmit_reconstructsSequence() {
        val frames = 40
        val buf = ByteBuffer.allocate(frames * 4).order(ByteOrder.nativeOrder())
        val shorts = ShortArray(frames * 2) { (it * 100).toShort() }
        buf.asShortBuffer().put(shorts)
        val out = FloatArray(8) // forces multiple emit calls
        val acc = mutableListOf<Float>()
        var emitCount = 0
        TrackDecoder.convertToStereoFloat(buf, 0, frames * 4, 2, 2, out) { off, n ->
            emitCount++
            for (i in 0 until n * 2) acc += out[off + i]
        }
        assertEquals(frames * 2, acc.size)
        for (i in shorts.indices) {
            assertEquals(shorts[i] / 32768f, acc[i], 1e-6f)
        }
        // 40 frames through an 8-float (4-frame) scratch buffer: 10 emits.
        assertEquals(10, emitCount)
    }
}
