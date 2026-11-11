#pragma once

// Plain linear-interpolation resampler helper (V1: pitch shifts with tempo,
// key lock is an explicit follow-up). Stateless: the Deck owns the fractional
// source position; this only evaluates taps.

#include <cstdint>

namespace dj {

class Resampler {
public:
    // Fractional read of one channel. Caller guarantees absPos+1 < writePos.
    template <typename Ring>
    static inline float readLinear(const Ring& ring, double absPos, int channel) {
        const uint64_t i0 = static_cast<uint64_t>(absPos);
        const float frac = static_cast<float>(absPos - static_cast<double>(i0));
        const float s0 = ring.peek(i0, channel);
        const float s1 = ring.peek(i0 + 1, channel);
        return s0 + (s1 - s0) * frac;
    }

    // Source frames consumed per output frame.
    static inline double ratioFor(double sourceRateHz, double rate, double deviceRateHz) {
        return (sourceRateHz * rate) / deviceRateHz;
    }
};

} // namespace dj
