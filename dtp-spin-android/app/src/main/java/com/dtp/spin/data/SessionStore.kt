package com.dtp.spin.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sessionStore by preferencesDataStore(name = "session")

/** Persisted practice session: loaded tracks + mixer settings, restored on launch. */
data class SessionSnapshot(
    val deckUri: Array<String?> = arrayOf(null, null),
    val deckPositionMs: LongArray = longArrayOf(0L, 0L),
    val deckRate: FloatArray = floatArrayOf(1f, 1f),
    val deckVolume: FloatArray = floatArrayOf(1f, 1f),
    val deckGainDb: FloatArray = floatArrayOf(0f, 0f),
    val deckEqLowDb: FloatArray = floatArrayOf(0f, 0f),
    val deckEqMidDb: FloatArray = floatArrayOf(0f, 0f),
    val deckEqHighDb: FloatArray = floatArrayOf(0f, 0f),
    val crossfade: Float = 0f,
)

/** Session persistence contract; DataStore impl below, fakes in tests. */
interface SessionPrefs {
    val session: Flow<SessionSnapshot>
    suspend fun save(snapshot: SessionSnapshot)
}

class SessionStore(context: Context) : SessionPrefs {
    private val store = context.applicationContext.sessionStore

    override val session: Flow<SessionSnapshot> = store.data.map { p ->
        SessionSnapshot(
            deckUri = arrayOf(p[KEY_URI_0], p[KEY_URI_1]),
            deckPositionMs = longArrayOf(p[KEY_POS_0] ?: 0L, p[KEY_POS_1] ?: 0L),
            deckRate = floatArrayOf(p[KEY_RATE_0] ?: 1f, p[KEY_RATE_1] ?: 1f),
            deckVolume = floatArrayOf(p[KEY_VOL_0] ?: 1f, p[KEY_VOL_1] ?: 1f),
            deckGainDb = floatArrayOf(p[KEY_GAIN_0] ?: 0f, p[KEY_GAIN_1] ?: 0f),
            deckEqLowDb = floatArrayOf(p[KEY_EQ_LOW_0] ?: 0f, p[KEY_EQ_LOW_1] ?: 0f),
            deckEqMidDb = floatArrayOf(p[KEY_EQ_MID_0] ?: 0f, p[KEY_EQ_MID_1] ?: 0f),
            deckEqHighDb = floatArrayOf(p[KEY_EQ_HIGH_0] ?: 0f, p[KEY_EQ_HIGH_1] ?: 0f),
            crossfade = p[KEY_XFADE] ?: 0f,
        )
    }

    override suspend fun save(s: SessionSnapshot) {
        store.edit { p ->
            s.deckUri[0]?.let { p[KEY_URI_0] = it } ?: p.remove(KEY_URI_0)
            s.deckUri[1]?.let { p[KEY_URI_1] = it } ?: p.remove(KEY_URI_1)
            p[KEY_POS_0] = s.deckPositionMs[0]
            p[KEY_POS_1] = s.deckPositionMs[1]
            p[KEY_RATE_0] = s.deckRate[0]
            p[KEY_RATE_1] = s.deckRate[1]
            p[KEY_VOL_0] = s.deckVolume[0]
            p[KEY_VOL_1] = s.deckVolume[1]
            p[KEY_GAIN_0] = s.deckGainDb[0]
            p[KEY_GAIN_1] = s.deckGainDb[1]
            p[KEY_EQ_LOW_0] = s.deckEqLowDb[0]
            p[KEY_EQ_LOW_1] = s.deckEqLowDb[1]
            p[KEY_EQ_MID_0] = s.deckEqMidDb[0]
            p[KEY_EQ_MID_1] = s.deckEqMidDb[1]
            p[KEY_EQ_HIGH_0] = s.deckEqHighDb[0]
            p[KEY_EQ_HIGH_1] = s.deckEqHighDb[1]
            p[KEY_XFADE] = s.crossfade
        }
    }

    companion object {
        private val KEY_URI_0 = stringPreferencesKey("deck_uri_0")
        private val KEY_URI_1 = stringPreferencesKey("deck_uri_1")
        private val KEY_POS_0 = longPreferencesKey("deck_pos_0")
        private val KEY_POS_1 = longPreferencesKey("deck_pos_1")
        private val KEY_RATE_0 = floatPreferencesKey("deck_rate_0")
        private val KEY_RATE_1 = floatPreferencesKey("deck_rate_1")
        private val KEY_VOL_0 = floatPreferencesKey("deck_vol_0")
        private val KEY_VOL_1 = floatPreferencesKey("deck_vol_1")
        private val KEY_GAIN_0 = floatPreferencesKey("deck_gain_0")
        private val KEY_GAIN_1 = floatPreferencesKey("deck_gain_1")
        private val KEY_EQ_LOW_0 = floatPreferencesKey("deck_eq_low_0")
        private val KEY_EQ_LOW_1 = floatPreferencesKey("deck_eq_low_1")
        private val KEY_EQ_MID_0 = floatPreferencesKey("deck_eq_mid_0")
        private val KEY_EQ_MID_1 = floatPreferencesKey("deck_eq_mid_1")
        private val KEY_EQ_HIGH_0 = floatPreferencesKey("deck_eq_high_0")
        private val KEY_EQ_HIGH_1 = floatPreferencesKey("deck_eq_high_1")
        private val KEY_XFADE = floatPreferencesKey("crossfade")
    }
}
