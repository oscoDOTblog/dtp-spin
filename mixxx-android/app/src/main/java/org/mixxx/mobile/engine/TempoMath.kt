package org.mixxx.mobile.engine

/**
 * Tempo / sync helpers. Pure functions for unit tests.
 */
object TempoMath {
    const val MIN_TEMPO = 0.92
    const val MAX_TEMPO = 1.08

    fun clampTempo(tempo: Double): Double = tempo.coerceIn(MIN_TEMPO, MAX_TEMPO)

    /** Effective BPM for a track at a given tempo multiplier. */
    fun effectiveBpm(trackBpm: Double?, tempo: Double): Double? =
        trackBpm?.times(clampTempo(tempo))

    /**
     * Follower tempo that matches [leaderBpm] given the follower track's
     * base [followerBpm]. Returns null when either BPM is unknown.
     * Result is clamped to the tempo slider range.
     */
    fun syncTempoTo(leaderBpm: Double?, followerBpm: Double?): Double? {
        if (leaderBpm == null || followerBpm == null || followerBpm <= 0) return null
        return clampTempo(leaderBpm / followerBpm)
    }
}
