package org.mixxx.mobile.effects

/** Minimal MVP effect set. Mixxx has chainable racks; this is one slot/deck. */
enum class DjEffectType { FILTER, ECHO }

data class DjEffect(
    val type: DjEffectType = DjEffectType.FILTER,
    /** Wet mix 0..1. */
    val wet: Float = 0f,
    /** Filter cutoff 0..1 (low-pass sweep) or echo feedback 0..1. */
    val param: Float = 0.5f
) {
    init {
        require(wet in 0f..1f) { "wet must be 0..1" }
        require(param in 0f..1f) { "param must be 0..1" }
    }
}
