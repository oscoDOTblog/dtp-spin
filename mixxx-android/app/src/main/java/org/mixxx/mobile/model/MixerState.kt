package org.mixxx.mobile.model

/** Central mixer state: crossfader + master/headphone levels. */
data class MixerState(
    /** Crossfader 0f = full left, 1f = full right, 0.5f = center. */
    val crossfader: Float = 0.5f,
    val masterVolume: Float = 0.8f,
    val headphoneVolume: Float = 0.5f
)
