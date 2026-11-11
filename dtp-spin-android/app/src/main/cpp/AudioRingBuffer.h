#pragma once

// Single-producer / single-consumer float ring buffer for streaming decoded
// PCM (interleaved stereo) from the decoder worker thread to the real-time
// audio callback. Indexed by absolute (monotonic, per-generation) frame
// counters so the callback can do fractional-position resampling via peek.
// No allocation or locks after construction.

#include <atomic>
#include <cstdint>
#include <cstring>
#include <vector>

namespace dj {

class AudioRingBuffer {
public:
    explicit AudioRingBuffer(int capacityFramesPowerOfTwo = 20)
        : mCapacity(1 << capacityFramesPowerOfTwo),
          mMask(mCapacity - 1),
          mData(static_cast<size_t>(mCapacity) * kChannels, 0.f) {}

    int capacityFrames() const { return mCapacity; }

    // Decoder thread. Returns frames accepted (0 when full, never partial
    // across a frame: either whole frames or nothing beyond capacity).
    int write(const float* src, int frames) {
        const uint64_t w = mWritePos.load(std::memory_order_relaxed);
        const uint64_t r = mReadPos.load(std::memory_order_acquire);
        const uint64_t free = static_cast<uint64_t>(mCapacity) - (w - r);
        int n = frames;
        if (static_cast<uint64_t>(n) > free) n = static_cast<int>(free);
        for (int i = 0; i < n; ++i) {
            const uint64_t idx = (w + static_cast<uint64_t>(i)) & mMask;
            mData[idx * kChannels] = src[i * kChannels];
            mData[idx * kChannels + 1] = src[i * kChannels + 1];
        }
        if (n > 0) mWritePos.store(w + static_cast<uint64_t>(n), std::memory_order_release);
        return n;
    }

    // Audio thread. Random access by absolute frame index; only valid for
    // indices in [readPos, writePos). No bounds check for speed — the Deck
    // guarantees the range via the starvation check in render().
    inline float peek(uint64_t absFrame, int channel) const {
        return mData[((absFrame & mMask) * kChannels) + channel];
    }

    uint64_t writePos() const { return mWritePos.load(std::memory_order_acquire); }

    void setReadPos(uint64_t v) { mReadPos.store(v, std::memory_order_release); }

    /**
     * Decoder thread: write [frames] starting at absolute source frame
     * [absFrame] (the decoder tracks its head; seeks jump it). Retried
     * tails re-present the same absolute range, so the write is idempotent:
     * fully-duplicate ranges are acked without rewriting, partially-new
     * ranges skip the duplicate prefix.
     *
     * Returns the covered range length (duplicate prefix + newly written),
     * so the caller can advance its head past everything presented. Returns 0
     * only when nothing was covered because the ring window is full
     * (back-pressure — retry the same range later).
     */
    int writeAt(uint64_t absFrame, const float* src, int frames) {
        if (frames <= 0) return 0;
        const uint64_t w = mWritePos.load(std::memory_order_relaxed);
        const uint64_t r = mReadPos.load(std::memory_order_acquire);
        // Entirely behind the playhead: stale (e.g. pre-seek tail) — ack and drop.
        if (absFrame + static_cast<uint64_t>(frames) <= r) return frames;
        // Skip the already-written prefix on retry.
        int skip = 0;
        if (absFrame < w && absFrame + static_cast<uint64_t>(frames) > w) {
            skip = static_cast<int>(w - absFrame);
        } else if (absFrame + static_cast<uint64_t>(frames) <= w) {
            return frames; // fully duplicate
        }
        const uint64_t start = absFrame + static_cast<uint64_t>(skip);
        int n = frames - skip;
        // Buffered window is [r, r + capacity); clamp the new tail to it.
        // (w < r only transiently right after a flush; then the window is empty.)
        const uint64_t buffered = (w > r) ? (w - r) : 0;
        const uint64_t free =
            static_cast<uint64_t>(mCapacity) > buffered
            ? static_cast<uint64_t>(mCapacity) - buffered : 0;
        if (static_cast<uint64_t>(n) > free) n = static_cast<int>(free);
        for (int i = 0; i < n; ++i) {
            const uint64_t idx = (start + static_cast<uint64_t>(i)) & mMask;
            mData[idx * kChannels] = src[(skip + i) * kChannels];
            mData[idx * kChannels + 1] = src[(skip + i) * kChannels + 1];
        }
        if (n > 0) mWritePos.store(start + static_cast<uint64_t>(n), std::memory_order_release);
        return skip + n;
    }

    // Audio thread only: drop everything (seek / new track).
    void reset() {
        mWritePos.store(0, std::memory_order_relaxed);
        mReadPos.store(0, std::memory_order_relaxed);
    }

private:
    static constexpr int kChannels = 2;
    const int mCapacity;
    const uint64_t mMask;
    std::vector<float> mData;
    alignas(64) std::atomic<uint64_t> mWritePos{0};
    alignas(64) std::atomic<uint64_t> mReadPos{0};
};

} // namespace dj
