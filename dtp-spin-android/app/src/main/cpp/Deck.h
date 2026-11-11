#pragma once

// One deck: transport, tempo (rate), cue, per-deck EQ/gain/volume, and the
// streaming PCM ring fed by the Kotlin decoder thread. All state mutated by
// the audio thread only (via commands); the JNI/decoder threads communicate
// through the command queue, the generation-guarded feed path, and atomics.
// Only render() and applyCommand() run in the real-time callback.

#include <atomic>
#include <cstdint>

#include "AudioRingBuffer.h"
#include "EngineCommandQueue.h"
#include "Resampler.h"
#include "ThreeBandEQ.h"

namespace dj {

class Deck {
public:
    Deck();

    // --- real-time thread ---
    void applyCommand(const Command& cmd, double deviceRateHz);
    void render(float* outInterleavedStereo, int frames, double deviceRateHz);

    // --- decoder thread (generation-guarded, lock-free) ---
    // Returns frames accepted, or -1 when the generation is stale.
    // [absStartFrame] is the absolute source frame of src[0]; the decoder
    // tracks its head and jumps it on seek (see AudioRingBuffer::writeAt).
    int feedPcm(const float* src, int frames, int generation, uint64_t absStartFrame);

    // --- any thread (atomics) ---
    double positionFrames() const { return mPositionFrames.load(std::memory_order_acquire); }
    bool isPlaying() const { return mPlaying.load(std::memory_order_acquire); }
    bool isStarved() const { return mStarved.load(std::memory_order_acquire); }
    bool hasEnded() const { return mEnded.load(std::memory_order_acquire); }
    float level() const { return mLevel.load(std::memory_order_acquire); }
    int generation() const { return mGeneration.load(std::memory_order_acquire); }

private:
    void applyPendingSeek();

    AudioRingBuffer mRing;
    ThreeBandEQ mEq;

    // Audio-thread-owned render state:
    double mSrcPos = 0.0;        // fractional source frames consumed
    double mSourceRateHz = 44100.0;
    int64_t mTotalFrames = 0;
    int64_t mCueFrame = 0;
    double mRate = 1.0;
    float mVolume = 1.0f;        // smoothed in render
    float mVolumeTarget = 1.0f;
    float mGain = 1.0f;          // smoothed in render
    float mGainTarget = 1.0f;
    bool mPlayingRt = false;
    bool mPreviewing = false;
    bool mSeekPending = false;
    int64_t mSeekTarget = 0;
    double mEqRateHz = 0.0;

    // Published snapshot (audio-thread writes, any-thread reads):
    std::atomic<double> mPositionFrames{0.0};
    std::atomic<bool> mPlaying{false};
    std::atomic<bool> mStarved{true};
    std::atomic<bool> mEnded{false};
    std::atomic<float> mLevel{0.f};
    std::atomic<int> mGeneration{0};
};

} // namespace dj
