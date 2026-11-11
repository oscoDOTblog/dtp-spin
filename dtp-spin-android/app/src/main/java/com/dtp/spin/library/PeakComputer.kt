package com.dtp.spin.library

import android.content.Context
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import com.dtp.spin.audio.TrackDecoder
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

/**
 * Background waveform scanner: decodes [uri] as fast as the device allows
 * and folds samples into min/max overview buckets. Runs on a worker thread,
 * never on the audio callback thread.
 */
object PeakComputer {
    private const val TAG = "PeakComputer"

    fun compute(context: Context, uri: Uri, buckets: Int = PeakUtils.BUCKET_COUNT): FloatArray? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
        } catch (e: Exception) {
            Log.w(TAG, "cannot open $uri", e)
            return null
        }
        val trackIndex = TrackDecoder.selectAudioTrack(extractor)
        if (trackIndex < 0) {
            extractor.release()
            return null
        }
        val format = extractor.getTrackFormat(trackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: run {
            extractor.release()
            return null
        }
        val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
            format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2
        extractor.selectTrack(trackIndex)
        val codec = try {
            MediaCodec.createDecoderByType(mime).also {
                it.configure(format, null, null, 0)
                it.start()
            }
        } catch (e: Exception) {
            Log.w(TAG, "no decoder for $mime", e)
            extractor.release()
            return null
        }

        val mins = FloatArray(buckets) { 0f }
        val maxs = FloatArray(buckets) { 0f }
        // Fold incrementally: track per-bucket sample counts by proportional mapping.
        val durationFrames = estimateTotalFrames(format)
        var decodedFrames = 0L
        val info = MediaCodec.BufferInfo()
        var inputEos = false
        var done = false
        try {
            while (!done) {
                if (!inputEos) {
                    val inIndex = codec.dequeueInputBuffer(2_000)
                    if (inIndex >= 0) {
                        val inBuf = codec.getInputBuffer(inIndex)!!
                        val n = extractor.readSampleData(inBuf, 0)
                        if (n < 0) {
                            codec.queueInputBuffer(
                                inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputEos = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, n, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, 5_000)
                if (outIndex >= 0) {
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) done = true
                    if (info.size > 0) {
                        val outBuf = codec.getOutputBuffer(outIndex)!!
                        val fmt = codec.outputFormat
                        val enc = if (fmt.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            fmt.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        } else {
                            android.media.AudioFormat.ENCODING_PCM_16BIT
                        }
                        val ch = if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        } else {
                            channels
                        }
                        decodedFrames = foldBuffer(
                            outBuf.duplicate().order(ByteOrder.nativeOrder()),
                            info, ch, enc, mins, maxs, decodedFrames, durationFrames,
                        )
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    Unit
                }
                if (inputEos && outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // Drain complete only on EOS flag; keep spinning briefly.
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "peak scan failed", e)
            return null
        } finally {
            try { codec.stop() } catch (_: Exception) {}
            codec.release()
            extractor.release()
        }
        val out = FloatArray(buckets * 2)
        for (b in 0 until buckets) {
            out[b * 2] = mins[b]
            out[b * 2 + 1] = maxs[b]
        }
        return out
    }

    private fun estimateTotalFrames(format: MediaFormat): Long {
        val durUs = if (format.containsKey(MediaFormat.KEY_DURATION))
            format.getLong(MediaFormat.KEY_DURATION) else 0L
        val sr = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE))
            format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
        return if (durUs > 0) durUs * sr / 1_000_000 else 1L
    }

    private fun foldBuffer(
        buf: java.nio.ByteBuffer,
        info: MediaCodec.BufferInfo,
        channels: Int,
        encoding: Int,
        mins: FloatArray,
        maxs: FloatArray,
        decodedSoFar: Long,
        estimatedTotal: Long,
        mono: FloatArray = FloatArray(4096),
    ): Long {
        buf.position(info.offset)
        buf.limit(info.offset + info.size)
        val buckets = mins.size
        var consumed = 0L
        if (encoding == android.media.AudioFormat.ENCODING_PCM_FLOAT) {
            val fb = buf.asFloatBuffer()
            val total = fb.remaining() / channels
            var i = 0
            while (i < total) {
                val n = min(total - i, mono.size)
                for (k in 0 until n) {
                    var m = 0f
                    for (c in 0 until channels) m += fb.get()
                    mono[k] = m / channels
                }
                accumulate(mono, n, decodedSoFar + consumed, estimatedTotal, mins, maxs)
                consumed += n
                i += n
            }
        } else {
            val sb = buf.asShortBuffer()
            val total = sb.remaining() / channels
            var i = 0
            while (i < total) {
                val n = min(total - i, mono.size)
                for (k in 0 until n) {
                    var m = 0f
                    for (c in 0 until channels) m += sb.get() / 32768f
                    mono[k] = m / channels
                }
                accumulate(mono, n, decodedSoFar + consumed, estimatedTotal, mins, maxs)
                consumed += n
                i += n
            }
        }
        return decodedSoFar + consumed
    }

    private fun accumulate(
        mono: FloatArray, n: Int, baseFrame: Long, estimatedTotal: Long,
        mins: FloatArray, maxs: FloatArray,
    ) {
        val buckets = mins.size
        for (k in 0 until n) {
            val b = ((baseFrame + k) * buckets / max(1L, estimatedTotal))
                .toInt().coerceIn(0, buckets - 1)
            val s = mono[k]
            if (s < mins[b]) mins[b] = s
            if (s > maxs[b]) maxs[b] = s
        }
    }
}

/** Reads intent flags for OpenMultipleDocuments results (read only, no write). */
fun takeReadPermission(context: Context, uri: Uri) {
    try {
        context.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    } catch (e: SecurityException) {
        Log.w("SAF", "persist permission failed for $uri", e)
    }
}
