#include "Deck.h"

#include <algorithm>
#include <cmath>

namespace dj {

Deck::Deck() : mRing(20 /* 2^20 frames ~21 s @48 kHz stereo */) {}

int Deck::feedPcm(const float* src, int frames, int generation, uint64_t absStartFrame) {
    if (generation != mGeneration.load(std::memory_order_acquire)) return -1; // stale
    return mRing.writeAt(absStartFrame, src, frames);
}

void Deck::applyCommand(const Command& cmd, double deviceRateHz) {
    switch (cmd.type) {
        case CommandType::Load: {
            mSourceRateHz = cmd.floats[0] > 0 ? cmd.floats[0] : 44100.0;
            mTotalFrames = cmd.ints[0];
            mGeneration.store(static_cast<int>(cmd.ints[1]), std::memory_order_release);
            mRing.reset();
            mEq.setSampleRate(deviceRateHz);
            mSrcPos = 0.0;
            mCueFrame = 0;
            mPlayingRt = false;
            mPreviewing = false;
            mSeekPending = false;
            mEnded.store(false, std::memory_order_relaxed);
            mStarved.store(true, std::memory_order_relaxed);
            mPositionFrames.store(0.0, std::memory_order_relaxed);
            mPlaying.store(false, std::memory_order_relaxed);
            break;
        }
        case CommandType::Play:
            if (mTotalFrames <= 0 || mSrcPos < static_cast<double>(mTotalFrames)) {
                mPlayingRt = true;
                mPreviewing = false;
                mEnded.store(false, std::memory_order_relaxed);
            }
            mPlaying.store(mPlayingRt, std::memory_order_relaxed);
            break;
        case CommandType::Pause:
            mPlayingRt = false;
            mPreviewing = false;
            mPlaying.store(false, std::memory_order_relaxed);
            break;
        case CommandType::CueDown:
            if (mPlayingRt) {
                mPlayingRt = false;
                mPreviewing = false;
                mSrcPos = static_cast<double>(mCueFrame);
            } else {
                mSrcPos = static_cast<double>(mCueFrame);
                mPlayingRt = true; // preview while held
                mPreviewing = true;
                mEnded.store(false, std::memory_order_relaxed);
            }
            mSeekPending = false;
            mPlaying.store(mPlayingRt, std::memory_order_relaxed);
            break;
        case CommandType::CueUp:
            if (mPreviewing) {
                mPlayingRt = false;
                mPreviewing = false;
                mSrcPos = static_cast<double>(mCueFrame);
            }
            mPlaying.store(mPlayingRt, std::memory_order_relaxed);
            break;
        case CommandType::SetCue: {
            const int64_t pos = cmd.ints[0] < 0
                ? static_cast<int64_t>(mSrcPos)
                : cmd.ints[0];
            mCueFrame = pos < 0 ? 0 : pos;
            break;
        }
        case CommandType::Seek:
            mGeneration.store(static_cast<int>(cmd.ints[1]), std::memory_order_release);
            mSeekTarget = cmd.ints[0] < 0 ? 0 : cmd.ints[0];
            mSeekPending = true;
            break;
        case CommandType::SetRate:
            mRate = cmd.floats[0] < 0.25 ? 0.25 : (cmd.floats[0] > 2.0 ? 2.0 : cmd.floats[0]);
            break;
        case CommandType::SetVolume:
            mVolumeTarget = std::clamp(cmd.floats[0], 0.f, 1.f);
            break;
        case CommandType::SetGain:
            mGainTarget = std::clamp(cmd.floats[0], 0.f, 4.f);
            break;
        case CommandType::SetEq:
            mEq.setGainsDb(cmd.floats[0], cmd.floats[1], cmd.floats[2]);
            break;
        default:
            break;
    }
}

void Deck::applyPendingSeek() {
    mRing.reset();
    mSrcPos = static_cast<double>(mSeekTarget);
    mSeekPending = false;
    mEnded.store(false, std::memory_order_relaxed);
    mStarved.store(true, std::memory_order_relaxed);
    mPositionFrames.store(mSrcPos, std::memory_order_relaxed);
}

void Deck::render(float* out, int frames, double deviceRateHz) {
    if (mSeekPending) applyPendingSeek();
    if (deviceRateHz != mEqRateHz) {
        mEqRateHz = deviceRateHz;
        mEq.setSampleRate(deviceRateHz);
    }
    float blockPeak = 0.f;
    const double ratio = Resampler::ratioFor(mSourceRateHz, mRate, deviceRateHz);
    // ~5 ms smoothing for click-free gain moves.
    const float smooth = static_cast<float>(1.0 - exp(-1.0 / (0.005 * deviceRateHz)));

    for (int i = 0; i < frames; ++i) {
        float sL = 0.f, sR = 0.f;
        if (mPlayingRt) {
            // Park at (not past) the last source frame: the final tap has no
            // i0+1 neighbour, so it renders flat and then ends the deck.
            // (Requiring i0+1 < w would strand the playhead one frame short
            // of the end with "buffering" forever.)
            if (mTotalFrames > 0 && mSrcPos >= static_cast<double>(mTotalFrames - 1)) {
                mSrcPos = static_cast<double>(mTotalFrames);
                mPlayingRt = false;
                mPreviewing = false;
                mEnded.store(true, std::memory_order_relaxed);
                mPlaying.store(false, std::memory_order_relaxed);
            } else {
                const uint64_t w = mRing.writePos();
                const uint64_t i0 = static_cast<uint64_t>(mSrcPos);
                if (i0 < w) {
                    const float frac = static_cast<float>(mSrcPos - static_cast<double>(i0));
                    const float s1L = (i0 + 1 < w) ? mRing.peek(i0 + 1, 0) : mRing.peek(i0, 0);
                    const float s1R = (i0 + 1 < w) ? mRing.peek(i0 + 1, 1) : mRing.peek(i0, 1);
                    const float s0L = mRing.peek(i0, 0);
                    const float s0R = mRing.peek(i0, 1);
                    sL = s0L + (s1L - s0L) * frac;
                    sR = s0R + (s1R - s0R) * frac;
                    mSrcPos += ratio;
                    mStarved.store(false, std::memory_order_relaxed);
                } else {
                    mStarved.store(true, std::memory_order_relaxed);
                }
            }
        }
        mVolume += (mVolumeTarget - mVolume) * smooth;
        mGain += (mGainTarget - mGain) * smooth;
        const float g = mVolume * mGain;
        float pair[2] = {sL * g, sR * g};
        mEq.process(pair, 1);
        out[i * 2] = pair[0];
        out[i * 2 + 1] = pair[1];
        const float p = std::max(std::fabs(pair[0]), std::fabs(pair[1]));
        if (p > blockPeak) blockPeak = p;
    }
    mPositionFrames.store(mSrcPos, std::memory_order_relaxed);
    mRing.setReadPos(static_cast<uint64_t>(mSrcPos));
    const float decayed = mLevel.load(std::memory_order_relaxed) * 0.92f;
    mLevel.store(blockPeak > decayed ? blockPeak : decayed, std::memory_order_relaxed);
}

} // namespace dj
