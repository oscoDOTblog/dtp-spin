#pragma once

// Owns the Oboe output stream and both decks. The single stereo float data
// callback drains the command queue, renders deck A/B into preallocated
// scratch buffers, applies the smoothed equal-power crossfade, and runs the
// master limiter. Real-time rules: the callback performs no I/O, no
// allocation, no locks, and no JNI.

#include <atomic>
#include <memory>

#include "Deck.h"
#include "EngineCommandQueue.h"

namespace oboe {
class AudioStream;
class AudioStreamCallback;
}

namespace dj {

class AudioEngine {
public:
    static constexpr int kDeckA = 0;
    static constexpr int kDeckB = 1;
    static constexpr int kNumDecks = 2;
    static constexpr int kMaxChunkFrames = 1024;

    AudioEngine();
    ~AudioEngine();

    AudioEngine(const AudioEngine&) = delete;
    AudioEngine& operator=(const AudioEngine&) = delete;

    bool start();
    void stop();
    bool isRunning() const { return mUserRunning.load(std::memory_order_acquire); }

    // Called from the Kotlin state poller (NOT the audio thread): reopens the
    // stream if the error callback flagged it.
    void pump();

    double sampleRate() const { return mSampleRate.load(std::memory_order_acquire); }

    EngineCommandQueue& commands() { return mCommands; }
    Deck& deck(int index) { return mDecks[index]; }

    // Invoked by the Oboe data callback trampoline.
    void renderCallback(float* outStereo, int numFrames);

    // Called on Oboe's error-callback thread: only flags for pump().
    void onStreamError() { mNeedsRestart.store(true, std::memory_order_release); }

private:
    bool openStream();
    void closeStream();

    EngineCommandQueue mCommands;
    Deck mDecks[kNumDecks];

    std::shared_ptr<oboe::AudioStreamCallback> mCallbackHolder;
    oboe::AudioStream* mStream = nullptr; // audio thread + pump() only
    std::atomic<double> mSampleRate{48000.0};
    std::atomic<bool> mUserRunning{false};
    std::atomic<bool> mNeedsRestart{false};

    float mCrossfade = 0.f;        // audio thread only
    float mCrossfadeTarget = 0.f;  // audio thread only
    float mScratchA[kMaxChunkFrames * 2];
    float mScratchB[kMaxChunkFrames * 2];
};

} // namespace dj
