#pragma once

// Master bus safety limiter: bit-transparent at and below -0.9 dBFS, a short
// Hermite knee rounds into a hard ±1.0 ceiling (C1-continuous, monotonic).
// Stateless, in-place, allocation-free.

#include <cmath>

namespace dj {

class MasterProcessor {
public:
    static inline void process(float* interleavedStereo, int frames) {
        for (int i = 0; i < frames * 2; ++i) {
            const float x = interleavedStereo[i];
            const float a = fabsf(x);
            if (a <= kKnee) continue; // transparent region
            float y;
            if (a >= 1.0f) {
                y = 1.0f; // safety ceiling: the HAL would hard-clip above this
            } else {
                // Hermite blend from slope 1 (at knee) to slope 0 (at ceiling).
                const float t = (a - kKnee) / (1.0f - kKnee);
                const float t2 = t * t;
                const float t3 = t2 * t;
                y = kKnee * (2 * t3 - 3 * t2 + 1) +
                    (1.0f - kKnee) * (t3 - 2 * t2 + t) +
                    (1.0f * (-2 * t3 + 3 * t2));
            }
            interleavedStereo[i] = copysignf(y, x);
        }
    }

private:
    static constexpr float kKnee = 0.9f;
};

} // namespace dj
