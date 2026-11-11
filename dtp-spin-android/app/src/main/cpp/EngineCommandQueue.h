#pragma once

// Lock-free single-producer / single-consumer command queue.
// Producer: any Kotlin/JNI thread. Consumer: the Oboe real-time callback.
// No allocation, no locks, no JNI on either path after construction.

#include <atomic>
#include <cstdint>

namespace dj {

enum class CommandType : uint8_t {
    None = 0,
    Load,        // float0 = sourceSampleRate, int0 = totalFrames, int1 = generation
    Play,        // (no args)
    Pause,       // (no args)
    CueDown,     // press-and-hold preview / return-to-cue
    CueUp,       // release preview
    SetCue,      // int0 = cueFrame (use -1 for "current position")
    Seek,        // int0 = targetFrame, int1 = generation
    SetRate,     // float0 = rate (1.0 = normal)
    SetVolume,   // float0 = 0..1 channel fader
    SetGain,     // float0 = linear trim
    SetEq,       // float0/1/2 = low/mid/high dB
    SetCrossfade // float0 = -1..+1 (engine-wide, deck field ignored)
};

struct Command {
    CommandType type = CommandType::None;
    uint8_t deck = 0;
    float floats[3] = {0.f, 0.f, 0.f};
    int64_t ints[2] = {0, 0};
};

class EngineCommandQueue {
public:
    static constexpr int kCapacity = 256;

    bool tryPush(const Command& cmd) {
        const int write = mWrite.load(std::memory_order_relaxed);
        const int next = (write + 1) & kMask;
        if (next == mRead.load(std::memory_order_acquire)) return false; // full
        mSlots[write] = cmd;
        mWrite.store(next, std::memory_order_release);
        return true;
    }

    bool tryPop(Command& out) {
        const int read = mRead.load(std::memory_order_relaxed);
        if (read == mWrite.load(std::memory_order_acquire)) return false; // empty
        out = mSlots[read];
        mRead.store((read + 1) & kMask, std::memory_order_release);
        return true;
    }

private:
    static constexpr int kMask = kCapacity - 1;
    static_assert((kCapacity & (kCapacity - 1)) == 0, "capacity must be power of two");
    Command mSlots[kCapacity];
    alignas(64) std::atomic<int> mWrite{0};
    alignas(64) std::atomic<int> mRead{0};
};

} // namespace dj
