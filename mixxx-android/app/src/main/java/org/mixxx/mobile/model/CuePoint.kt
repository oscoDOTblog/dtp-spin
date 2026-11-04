package org.mixxx.mobile.model

/** A saved cue/hotcue point, mirroring Mixxx hotcue slots. */
data class CuePoint(
    val slot: Int,
    val positionMs: Long,
    val label: String = "Cue ${slot + 1}"
) {
    init {
        require(slot in 0..7) { "slot must be 0..7" }
        require(positionMs >= 0) { "positionMs must be >= 0" }
    }
}

/** Loop state for one deck. Null bounds mean no active loop. */
data class LoopState(
    val startMs: Long? = null,
    val endMs: Long? = null,
    val enabled: Boolean = false
) {
    val lengthMs: Long?
        get() = if (startMs != null && endMs != null) endMs - startMs else null

    fun isValid(): Boolean =
        startMs != null && endMs != null && endMs > startMs
}
