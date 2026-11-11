#include "AudioEngine.h"

#include <cmath>

#include <oboe/Oboe.h>

#include "MasterProcessor.h"
#include "Mixer.h"

namespace dj {

namespace {

class EngineCallbacks : public oboe::AudioStreamCallback {
public:
    explicit EngineCallbacks(AudioEngine* engine) : mEngine(engine) {}

    oboe::DataCallbackResult onAudioReady(
            oboe::AudioStream* /*stream*/, void* audioData, int32_t numFrames) override {
        mEngine->renderCallback(static_cast<float*>(audioData), numFrames);
        return oboe::DataCallbackResult::Continue;
    }

    void onErrorAfterClose(oboe::AudioStream* /*stream*/, oboe::Result /*error*/) override {
        // Real fix-up happens in pump() on a non-real-time thread.
        if (mEngine != nullptr) mEngine->onStreamError();
    }

private:
    AudioEngine* mEngine;
};

} // namespace

AudioEngine::AudioEngine() = default;

AudioEngine::~AudioEngine() { closeStream(); }

bool AudioEngine::openStream() {
    // Oboe keeps only a raw callback pointer: hold ownership in the member.
    auto callbacks = std::make_shared<EngineCallbacks>(this);
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Exclusive)
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(2)
        ->setSampleRate(oboe::kUnspecified)
        ->setDataCallback(callbacks)
        ->setErrorCallback(callbacks);
    // Oboe copies the callback pointers; keep ownership here.
    mCallbackHolder = callbacks;

    oboe::Result result = builder.openStream(&mStream);
    if (result != oboe::Result::OK || mStream == nullptr) {
        mStream = nullptr;
        return false;
    }
    mSampleRate.store(static_cast<double>(mStream->getSampleRate()),
                      std::memory_order_release);
    return true;
}

void AudioEngine::closeStream() {
    if (mStream != nullptr) {
        mStream->stop();
        mStream->close();
        mStream = nullptr;
    }
    mCallbackHolder.reset();
}

bool AudioEngine::start() {
    mUserRunning.store(true, std::memory_order_release);
    if (mStream == nullptr && !openStream()) {
        mUserRunning.store(false, std::memory_order_release);
        return false;
    }
    const oboe::Result r = mStream->requestStart();
    return r == oboe::Result::OK;
}

void AudioEngine::stop() {
    mUserRunning.store(false, std::memory_order_release);
    if (mStream != nullptr) mStream->requestStop();
}

void AudioEngine::pump() {
    if (mNeedsRestart.load(std::memory_order_acquire)) {
        mNeedsRestart.store(false, std::memory_order_release);
        closeStream();
        if (mUserRunning.load(std::memory_order_acquire)) {
            if (openStream()) mStream->requestStart();
        }
    }
}

void AudioEngine::renderCallback(float* outStereo, int numFrames) {
    // 1. Drain UI-thread commands (bounded: queue capacity is 256).
    Command cmd;
    while (mCommands.tryPop(cmd)) {
        if (cmd.type == CommandType::SetCrossfade) {
            mCrossfadeTarget = cmd.floats[0] < -1.f ? -1.f
                : (cmd.floats[0] > 1.f ? 1.f : cmd.floats[0]);
        } else if (cmd.deck >= 0 && cmd.deck < kNumDecks) {
            mDecks[cmd.deck].applyCommand(cmd, mSampleRate.load(std::memory_order_relaxed));
        }
    }

    const double deviceRate = mSampleRate.load(std::memory_order_relaxed);
    const float smooth = static_cast<float>(1.0 - exp(-1.0 / (0.005 * deviceRate)));

    int remaining = numFrames;
    int offset = 0;
    while (remaining > 0) {
        const int n = remaining > kMaxChunkFrames ? kMaxChunkFrames : remaining;
        mDecks[kDeckA].render(mScratchA, n, deviceRate);
        mDecks[kDeckB].render(mScratchB, n, deviceRate);
        for (int i = 0; i < n; ++i) {
            mCrossfade += (mCrossfadeTarget - mCrossfade) * smooth;
            float gA, gB;
            Mixer::equalPowerGains(mCrossfade, gA, gB);
            const int o = (offset + i) * 2;
            outStereo[o] =
                mScratchA[i * 2] * gA + mScratchB[i * 2] * gB;
            outStereo[o + 1] =
                mScratchA[i * 2 + 1] * gA + mScratchB[i * 2 + 1] * gB;
        }
        remaining -= n;
        offset += n;
    }
    MasterProcessor::process(outStereo, numFrames);
}

} // namespace dj
