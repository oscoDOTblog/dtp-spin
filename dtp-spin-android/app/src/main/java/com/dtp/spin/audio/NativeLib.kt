package com.dtp.spin.audio

import java.nio.ByteBuffer

/**
 * JNI bindings for the native `djengine` library. Signatures must match
 * app/src/main/cpp/jni_bridge.cpp exactly. Only called from
 * [NativeAudioEngine] on non-real-time threads (the audio callback never
 * calls back into Java).
 */
internal object NativeLib {
    init {
        System.loadLibrary("djengine")
    }

    external fun nativeCreate(): Long
    external fun nativeDestroy(handle: Long)
    external fun nativeStart(handle: Long): Boolean
    external fun nativeStop(handle: Long)
    external fun nativePump(handle: Long)
    external fun nativeSampleRate(handle: Long): Double

    external fun nativeDeckLoad(
        handle: Long, deck: Int, sourceSampleRate: Float, totalFrames: Long, generation: Int,
    )
    external fun nativeDeckFeedPcm(
        handle: Long, deck: Int, byteBuffer: ByteBuffer, frames: Int, generation: Int,
        absStartFrame: Long,
    ): Int

    external fun nativeDeckPlay(handle: Long, deck: Int)
    external fun nativeDeckPause(handle: Long, deck: Int)
    external fun nativeDeckCueDown(handle: Long, deck: Int)
    external fun nativeDeckCueUp(handle: Long, deck: Int)
    external fun nativeDeckSetCue(handle: Long, deck: Int, cueFrame: Long)
    external fun nativeDeckSeek(handle: Long, deck: Int, targetFrame: Long, generation: Int)

    external fun nativeDeckSetRate(handle: Long, deck: Int, rate: Float)
    external fun nativeDeckSetVolume(handle: Long, deck: Int, volume: Float)
    external fun nativeDeckSetGain(handle: Long, deck: Int, gain: Float)
    external fun nativeDeckSetEq(handle: Long, deck: Int, lowDb: Float, midDb: Float, highDb: Float)
    external fun nativeSetCrossfade(handle: Long, crossfade: Float)

    external fun nativeDeckGetPositionFrames(handle: Long, deck: Int): Double
    external fun nativeDeckIsPlaying(handle: Long, deck: Int): Boolean
    external fun nativeDeckIsStarved(handle: Long, deck: Int): Boolean
    external fun nativeDeckHasEnded(handle: Long, deck: Int): Boolean
    external fun nativeDeckGetLevel(handle: Long, deck: Int): Float
    external fun nativeDeckGetGeneration(handle: Long, deck: Int): Int
}
