#pragma once

// Per-deck 3-band EQ: low shelf @ 320 Hz, peaking @ 1 kHz, high shelf @
// 3.2 kHz (RBJ cookbook biquads, direct form I, per channel). Parameter sets
// are atomic + dirty-flagged from any thread; coefficient recomputation
// happens on the audio thread inside process() — no allocation, no locks.

#include <atomic>
#include <cmath>

namespace dj {

class ThreeBandEQ {
public:
    ThreeBandEQ() { setSampleRate(48000.0); }

    void setSampleRate(double sr) {
        mSampleRate = sr;
        mDirty.store(true, std::memory_order_release);
    }

    void setGainsDb(float low, float mid, float high) {
        mLowDb.store(low, std::memory_order_relaxed);
        mMidDb.store(mid, std::memory_order_relaxed);
        mHighDb.store(high, std::memory_order_relaxed);
        mDirty.store(true, std::memory_order_release);
    }

    // In-place stereo interleave.
    void process(float* interleaved, int frames) {
        if (mDirty.load(std::memory_order_acquire)) recompute();
        for (int i = 0; i < frames; ++i) {
            float* f = &interleaved[i * 2];
            for (int ch = 0; ch < 2; ++ch) {
                float x = f[ch];
                x = runBand(x, mLow, mStateLow[ch]);
                x = runBand(x, mMid, mStateMid[ch]);
                x = runBand(x, mHigh, mStateHigh[ch]);
                f[ch] = x;
            }
        }
    }

private:
    struct Coeffs { double b0 = 1, b1 = 0, b2 = 0, a1 = 0, a2 = 0; };
    struct State { double x1 = 0, x2 = 0, y1 = 0, y2 = 0; };

    static inline float runBand(float x, const Coeffs& c, State& s) {
        const double y = c.b0 * x + c.b1 * s.x1 + c.b2 * s.x2 - c.a1 * s.y1 - c.a2 * s.y2;
        s.x2 = s.x1; s.x1 = x; s.y2 = s.y1; s.y1 = y;
        return static_cast<float>(y);
    }

    void recompute() {
        mDirty.store(false, std::memory_order_relaxed);
        shelf(mLow, 320.0, mLowDb.load(std::memory_order_relaxed), false);
        peak(mMid, 1000.0, 0.9, mMidDb.load(std::memory_order_relaxed));
        shelf(mHigh, 3200.0, mHighDb.load(std::memory_order_relaxed), true);
    }

    void shelf(Coeffs& c, double fc, float dBg, bool high) {
        const double A = pow(10.0, dBg / 40.0);
        const double w0 = 2.0 * kPi * fc / mSampleRate;
        const double cosw = cos(w0);
        const double alpha = sin(w0) / 2.0 * sqrt((A + 1.0 / A) * (1.0 / 1.0 - 1.0) + 2.0);
        const double sqrtA = 2.0 * sqrt(A) * alpha;
        double b0, b1, b2, a0, a1, a2;
        if (!high) {
            b0 = A * ((A + 1) - (A - 1) * cosw + sqrtA);
            b1 = 2 * A * ((A - 1) - (A + 1) * cosw);
            b2 = A * ((A + 1) - (A - 1) * cosw - sqrtA);
            a0 = (A + 1) + (A - 1) * cosw + sqrtA;
            a1 = -2 * ((A - 1) + (A + 1) * cosw);
            a2 = (A + 1) + (A - 1) * cosw - sqrtA;
        } else {
            b0 = A * ((A + 1) + (A - 1) * cosw + sqrtA);
            b1 = -2 * A * ((A - 1) + (A + 1) * cosw);
            b2 = A * ((A + 1) + (A - 1) * cosw - sqrtA);
            a0 = (A + 1) - (A - 1) * cosw + sqrtA;
            a1 = 2 * ((A - 1) - (A + 1) * cosw);
            a2 = (A + 1) - (A - 1) * cosw - sqrtA;
        }
        c.b0 = b0 / a0; c.b1 = b1 / a0; c.b2 = b2 / a0;
        c.a1 = a1 / a0; c.a2 = a2 / a0;
    }

    void peak(Coeffs& c, double fc, double q, float dBg) {
        const double A = pow(10.0, dBg / 40.0);
        const double w0 = 2.0 * kPi * fc / mSampleRate;
        const double alpha = sin(w0) / (2.0 * q);
        const double cosw = cos(w0);
        const double a0 = 1 + alpha / A;
        c.b0 = (1 + alpha * A) / a0;
        c.b1 = (-2 * cosw) / a0;
        c.b2 = (1 - alpha * A) / a0;
        c.a1 = (-2 * cosw) / a0;
        c.a2 = (1 - alpha / A) / a0;
    }

    static constexpr double kPi = 3.141592653589793;
    double mSampleRate;
    std::atomic<float> mLowDb{0.f}, mMidDb{0.f}, mHighDb{0.f};
    std::atomic<bool> mDirty{true};
    Coeffs mLow, mMid, mHigh;
    State mStateLow[2], mStateMid[2], mStateHigh[2];
};

} // namespace dj
