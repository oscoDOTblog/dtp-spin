package com.dtp.spin.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.HandlerThread
import androidx.core.net.toUri
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Per-deck streaming decoder. Runs its own HandlerThread (never the audio
 * thread): pulls compressed samples via MediaExtractor, decodes with
 * MediaCodec, converts to interleaved stereo float32, and pushes chunks into
 * the native engine with the current generation token. Stale generations are
 * dropped natively; the loop then re-seeks and continues.
 *
 * Supports MP3/WAV out of the box; M4A/FLAC wherever the device has codecs.
 */
class TrackDecoder(
    private val appContext: Context,
    private val engine: AudioEngine,
    private val deck: Int,
) : DeckFeeder {
    sealed interface Event {
        data object EndOfStream : Event
        data class Error(val message: String) : Event
    }

    override var onEvent: ((Event) -> Unit)? = null

    private var thread: HandlerThread? = null
    private val stopFlag = AtomicBoolean(false)
    private val pendingSeekUs = AtomicLong(NO_SEEK)

    @Volatile private var generation: Int = 0
    @Volatile private var running = false

    override val isRunning: Boolean get() = running

    /** Start streaming [uriString] from the top under [generation]. */
    @Synchronized
    override fun load(uriString: String, generation: Int, startPositionMs: Long) {
        val uri = uriString.toUri()
        stopLocked()
        this.generation = generation
        stopFlag.set(false)
        running = true
        val t = HandlerThread("decoder-$deck").also { it.start() }
        thread = t
        Thread({
            try {
                decodeLoop(uri, startPositionMs * 1000, generation)
            } catch (e: Exception) {
                if (!stopFlag.get()) onEvent?.invoke(Event.Error(e.message ?: "decode failed"))
                Log.w(TAG, "decode loop ended", e)
            } finally {
                running = false
            }
        }, "decode-run-$deck").start()
    }

    /** Seek the running stream; buffers are flushed natively via engine.seek. */
    override fun seekTo(positionMs: Long, generation: Int) {
        this.generation = generation
        pendingSeekUs.set(positionMs * 1000)
    }

    @Synchronized
    override fun stop() {
        stopLocked()
    }

    private fun stopLocked() {
        stopFlag.set(true)
        thread?.quitSafely()
        thread = null
        running = false
        pendingSeekUs.set(NO_SEEK)
    }

    private fun decodeLoop(uri: Uri, startUs: Long, gen: Int) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(appContext, uri, null)
        } catch (e: Exception) {
            onEvent?.invoke(Event.Error("cannot open file: ${e.message}"))
            return
        }
        val trackIndex = selectAudioTrack(extractor)
        if (trackIndex < 0) {
            onEvent?.invoke(Event.Error("no audio track found"))
            extractor.release()
            return
        }
        val format = extractor.getTrackFormat(trackIndex)
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
            format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2
        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION))
            format.getLong(MediaFormat.KEY_DURATION) else -1L
        val totalFrames = if (durationUs > 0) durationUs * sampleRate / 1_000_000 else 0L

        val mime = format.getString(MediaFormat.KEY_MIME) ?: run {
            onEvent?.invoke(Event.Error("unknown mime"))
            extractor.release()
            return
        }
        extractor.selectTrack(trackIndex)
        Log.i(TAG, "deck=$deck mime=$mime sr=$sampleRate ch=$channels totalFrames=$totalFrames startUs=$startUs gen=$gen")

        // WAV (and other PCM containers) expose "audio/raw": no codec to
        // create — stream the samples straight from the extractor instead.
        if (mime == "audio/raw") {
            extractor.release()
            decodeRawLoop(uri, format, startUs, gen)
            return
        }

        if (startUs > 0) extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

        val codec = try {
            MediaCodec.createDecoderByType(mime)
        } catch (e: Exception) {
            onEvent?.invoke(Event.Error("no decoder for $mime"))
            extractor.release()
            return
        }

        var outChannels = channels
        var outEncoding = android.media.AudioFormat.ENCODING_PCM_16BIT
        try {
            codec.configure(format, null, null, 0)
            codec.start()
        } catch (e: Exception) {
            onEvent?.invoke(Event.Error("decoder start failed: ${e.message}"))
            codec.release()
            extractor.release()
            return
        }

        engine.loadDeck(deck, sampleRate.toFloat(), totalFrames, gen)
        if (startUs > 0) {
            // Align the native position base with the extractor offset so the
            // playhead is correct from the first frame (session restore).
            engine.seek(deck, startUs * sampleRate / 1_000_000, gen)
        }

        val bufferInfo = MediaCodec.BufferInfo()
        val chunkFrames = 2048
        val direct = ByteBuffer.allocateDirect(chunkFrames * 2 * 4).order(ByteOrder.nativeOrder())
        val floatChunk = FloatArray(chunkFrames * 2)
        var inputEos = false
        // Absolute source frame corresponding to the next frame we will feed.
        // Jumped on seek; the native ring indexes everything absolutely.
        var absHead = startUs * sampleRate / 1_000_000
        // Parked after end-of-stream; a later seek resumes feeding (same
        // contract as the raw path: a seek never strands the deck).
        var parkedAtEos = false

        try {
            while (!stopFlag.get()) {
                // --- apply pending seek ---
                val seekUs = pendingSeekUs.getAndSet(NO_SEEK)
                if (seekUs != NO_SEEK) {
                    parkedAtEos = false
                    try {
                        extractor.seekTo(seekUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                    } catch (e: Exception) {
                        Log.w(TAG, "seek failed, stopping feed", e)
                        onEvent?.invoke(Event.Error("seek failed: ${e.message}"))
                        return
                    }
                    codec.flush()
                    inputEos = false
                    absHead = seekUs * sampleRate / 1_000_000
                    engine.seek(deck, absHead, this.generation)
                }
                if (parkedAtEos) {
                    // End of stream reached earlier; idle until seek/stop.
                    Thread.sleep(50)
                    continue
                }
                val curGen = this.generation

                // --- feed input ---
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
                            codec.queueInputBuffer(
                                inIndex, 0, n, extractor.sampleTime, 0,
                            )
                            extractor.advance()
                        }
                    }
                }

                // --- drain output ---
                val outIndex = codec.dequeueOutputBuffer(bufferInfo, 5_000)
                when {
                    outIndex >= 0 -> {
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            parkedAtEos = true
                            onEvent?.invoke(Event.EndOfStream)
                        }
                        if (bufferInfo.size > 0) {
                            val outBuf = codec.getOutputBuffer(outIndex)!!
                            // Output format can change mid-stream (e.g. AAC float).
                            val fmt = codec.outputFormat
                            if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                                outChannels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            }
                            if (fmt.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                                outEncoding = fmt.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            }
                            convertToStereoFloat(
                                outBuf, bufferInfo.offset, bufferInfo.size,
                                outChannels, outEncoding, floatChunk,
                            ) { off, frames ->
                                absHead += pushFrames(floatChunk, off, frames, direct, curGen, absHead)
                            }
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                    }
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val fmt = codec.outputFormat
                        if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            outChannels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                        if (fmt.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            outEncoding = fmt.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        }
                    }
                    else -> Unit // timeout / codec config — keep looping
                }

                // Back-pressure: ring holds ~21 s; sleep when full instead of spinning.
                if (inputEos && !parkedAtEos) Thread.sleep(2)
            }
        } finally {
            try { codec.stop() } catch (_: Exception) {}
            codec.release()
            extractor.release()
        }
    }

    /**
     * PCM passthrough for "audio/raw" tracks (WAV): samples come straight
     * from the extractor with no MediaCodec involved. Supports 16-bit int
     * and 32-bit float PCM; anything else surfaces a clear error so the
     * user can convert the file.
     *
     * Positioning never uses MediaExtractor.seekTo(): on some devices a
     * seek on a raw stream breaks subsequent reads (immediate EOS, silent
     * stall). Instead every (re)position opens a fresh extractor and
     * drains forward to the target — cheap memcpys for local files.
     */
    private fun decodeRawLoop(
        uri: Uri,
        format: MediaFormat,
        startUs: Long,
        gen: Int,
    ) {
        val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE))
            format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
        val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
            format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2
        val bitsPerSample = if (format.containsKey("bits-per-sample"))
            format.getInteger("bits-per-sample") else 16
        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION))
            format.getLong(MediaFormat.KEY_DURATION) else -1L
        val totalFrames = if (durationUs > 0) durationUs * sampleRate / 1_000_000 else 0L

        val encoding = when (bitsPerSample) {
            16 -> android.media.AudioFormat.ENCODING_PCM_16BIT
            32 -> android.media.AudioFormat.ENCODING_PCM_FLOAT
            else -> {
                onEvent?.invoke(
                    Event.Error("$bitsPerSample-bit WAV unsupported — convert to 16-bit PCM or MP3"),
                )
                return
            }
        }
        if (bitsPerSample != 16 && bitsPerSample != 32) {
            Log.w(TAG, "deck=$deck unexpected bits-per-sample=$bitsPerSample")
        }
        val bytesPerFrame = (channels * (bitsPerSample / 8)).coerceAtLeast(1)

        engine.loadDeck(deck, sampleRate.toFloat(), totalFrames, gen)

        val chunkFrames = 2048
        val direct = ByteBuffer.allocateDirect(chunkFrames * 2 * 4).order(ByteOrder.nativeOrder())
        val floatChunk = FloatArray(chunkFrames * 2)
        val readBuf = ByteBuffer.allocate(64 * 1024)

        var extractor = openRawExtractor(uri) ?: return
        // Absolute source frame of the next frame we will feed; re-based on
        // every (re)position so the native ring stays consistent.
        var absHead = 0L
        try {
            absHead = drainRawTo(extractor, readBuf, startUs, sampleRate, bytesPerFrame)
            if (startUs > 0) engine.seek(deck, absHead, gen)
            // The thread lives until stop()/load(): after end-of-stream it parks
            // and only wakes for a seek (which repositions and resumes feeding)
            // so a later seek can never strand the deck without a feeder.
            var parkedAtEos = false
            while (!stopFlag.get()) {
                val seekUs = pendingSeekUs.getAndSet(NO_SEEK)
                if (seekUs != NO_SEEK) {
                    extractor.release()
                    extractor = openRawExtractor(uri) ?: return
                    absHead = drainRawTo(extractor, readBuf, seekUs, sampleRate, bytesPerFrame)
                    engine.seek(deck, absHead, this.generation)
                    parkedAtEos = false
                }
                if (parkedAtEos) {
                    Thread.sleep(50)
                    continue
                }
                val curGen = this.generation
                readBuf.clear()
                val n: Int
                try {
                    n = extractor.readSampleData(readBuf, 0)
                } catch (e: Exception) {
                    Log.w(TAG, "readSampleData threw, stopping feed", e)
                    onEvent?.invoke(Event.Error("read failed: ${e.message}"))
                    return
                }
                if (n < 0) {
                    parkedAtEos = true
                    onEvent?.invoke(Event.EndOfStream)
                } else if (n == 0) {
                    // Stale/empty read: briefly yield rather than hot-spin.
                    Thread.sleep(5)
                } else {
                    convertToStereoFloat(readBuf, 0, n, channels, encoding, floatChunk) { off, frames ->
                        absHead += pushFrames(floatChunk, off, frames, direct, curGen, absHead)
                    }
                    extractor.advance()
                }
            }
        } finally {
            extractor.release()
        }
    }

    /** Fresh extractor positioned at the start of the audio track. */
    private fun openRawExtractor(uri: Uri): MediaExtractor? {
        return try {
            val ex = MediaExtractor()
            ex.setDataSource(appContext, uri, null)
            val track = selectAudioTrack(ex)
            if (track < 0) {
                ex.release()
                onEvent?.invoke(Event.Error("no audio track found"))
                return null
            }
            ex.selectTrack(track)
            ex
        } catch (e: Exception) {
            Log.w(TAG, "cannot open $uri", e)
            onEvent?.invoke(Event.Error("cannot open file: ${e.message}"))
            null
        }
    }

    /**
     * Advances [extractor] (from position 0) to [targetUs] by reading and
     * discarding, returning the exact source frame reached. Never seeks —
     * see [decodeRawLoop].
     */
    private fun drainRawTo(
        extractor: MediaExtractor,
        readBuf: ByteBuffer,
        targetUs: Long,
        sampleRate: Int,
        bytesPerFrame: Int,
    ): Long {
        var skipped = 0L
        while (skipped * 1_000_000L / sampleRate < targetUs && !stopFlag.get()) {
            readBuf.clear()
            val n = try {
                extractor.readSampleData(readBuf, 0)
            } catch (e: Exception) {
                Log.w(TAG, "drain read failed", e)
                break
            }
            if (n <= 0) break
            skipped += n / bytesPerFrame
            extractor.advance()
        }
        return skipped
    }

    /**
     * Pushes [frames] of [floats] (from [offset]) into the engine at absolute
     * source frame [absFrame], retrying the unaccepted tail on back-pressure.
     * Returns total frames accepted; the caller advances its absolute head
     * by the return value. A stale generation (-1) drops the tail — the
     * caller re-syncs its head on the next seek/load.
     */
    private fun pushFrames(
        floats: FloatArray, offset: Int, frames: Int,
        direct: ByteBuffer, gen: Int, absFrame: Long,
    ): Long {
        var remaining = frames
        var pos = offset
        var abs = absFrame
        var acceptedTotal = 0L
        while (remaining > 0 && !stopFlag.get()) {
            val n = minOf(remaining, CHUNK_FRAMES)
            direct.clear()
            direct.asFloatBuffer().put(floats, pos, n * 2)
            val accepted = engine.feedPcm(deck, direct, n, gen, abs)
            if (accepted < 0) return acceptedTotal // stale: drop tail, caller re-syncs
            if (accepted == 0) {
                Thread.sleep(3) // ring full: back-pressure, retry same data
                continue
            }
            acceptedTotal += accepted
            pos += accepted * 2
            abs += accepted
            remaining -= accepted
        }
        return acceptedTotal
    }

    companion object {
        private const val TAG = "TrackDecoder"
        private const val NO_SEEK = Long.MIN_VALUE
        private const val CHUNK_FRAMES = 2048

        fun selectAudioTrack(extractor: MediaExtractor): Int {
            for (i in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(i)
                    .getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) return i
            }
            return -1
        }

        /**
         * Converts one decoded output buffer to interleaved stereo float32.
         * Handles 16-bit PCM, 32-bit float PCM, and mono/stereo/multichannel.
         * Takes raw offset/size (not BufferInfo) so it stays unit-testable on JVM.
         */
        fun convertToStereoFloat(
            src: ByteBuffer,
            offset: Int,
            size: Int,
            channels: Int,
            encoding: Int,
            out: FloatArray,
            emit: (offset: Int, frames: Int) -> Unit,
        ) {
            val dup = src.duplicate().order(ByteOrder.nativeOrder())
            dup.position(offset)
            dup.limit(offset + size)
            var produced = 0
            if (encoding == android.media.AudioFormat.ENCODING_PCM_FLOAT) {
                val fb = dup.asFloatBuffer()
                val total = fb.remaining() / channels
                var f = 0
                while (f < total) {
                    val n = minOf(total - f, (out.size / 2 - produced))
                    for (i in 0 until n) {
                        var accL = 0f
                        var accR = 0f
                        for (c in 0 until channels) {
                            val s = fb.get()
                            when (c) {
                                0 -> accL += s
                                1 -> accR += s
                                else -> {
                                    accL += s / (channels - 1)
                                    accR += s / (channels - 1)
                                }
                            }
                        }
                        if (channels == 1) accR = accL
                        out[(produced + i) * 2] = accL.coerceIn(-1f, 1f)
                        out[(produced + i) * 2 + 1] = accR.coerceIn(-1f, 1f)
                    }
                    produced += n
                    f += n
                    if (produced == out.size / 2) {
                        emit(0, produced)
                        produced = 0
                    }
                }
            } else {
                // 16-bit PCM (also tolerates 8-bit? no — 16-bit only, else error upstream).
                val sb = dup.asShortBuffer()
                val total = sb.remaining() / channels
                var f = 0
                while (f < total) {
                    val n = minOf(total - f, (out.size / 2 - produced))
                    for (i in 0 until n) {
                        var accL = 0f
                        var accR = 0f
                        for (c in 0 until channels) {
                            val s = sb.get() / 32768f
                            when (c) {
                                0 -> accL += s
                                1 -> accR += s
                                else -> {
                                    accL += s / (channels - 1)
                                    accR += s / (channels - 1)
                                }
                            }
                        }
                        if (channels == 1) accR = accL
                        out[(produced + i) * 2] = accL.coerceIn(-1f, 1f)
                        out[(produced + i) * 2 + 1] = accR.coerceIn(-1f, 1f)
                    }
                    produced += n
                    f += n
                    if (produced == out.size / 2) {
                        emit(0, produced)
                        produced = 0
                    }
                }
            }
            if (produced > 0) emit(0, produced)
        }
    }
}
