#pragma once

// Equal-power crossfader + per-deck gain application. Mirrors
// com.dtp.spin.util.AudioMath (unit-tested on JVM) — keep both in sync.

#include <cmath>

namespace dj {

class Mixer {
public:
    // crossfade in [-1, +1]: -1 = full A, +1 = full B.
    static inline void equalPowerGains(float crossfade, float& gainA, float& gainB) {
        float t = (crossfade + 1.0f) * 0.5f;
        if (t < 0.f) t = 0.f;
        if (t > 1.f) t = 1.f;
        constexpr float kHalfPi = 1.5707963267948966f;
        gainA = cosf(t * kHalfPi);
        gainB = sinf(t * kHalfPi);
    }
};

} // namespace dj
