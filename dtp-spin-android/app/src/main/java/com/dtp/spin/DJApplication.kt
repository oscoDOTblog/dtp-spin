package com.dtp.spin

import android.app.Application
import com.dtp.spin.audio.NativeAudioEngine
import com.dtp.spin.data.DJDatabase
import com.dtp.spin.data.SessionStore
import com.dtp.spin.data.TrackRepository
import com.dtp.spin.library.TrackImporter
import com.dtp.spin.playback.AudioFocus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual DI root (the app is small enough that Hilt would add more than it
 * saves — see plan Phase 0). Owns the engine, database, session store, and
 * importer for the process lifetime.
 */
class DJApplication : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: DJDatabase by lazy { DJDatabase.get(this) }
    val trackRepository: TrackRepository by lazy { TrackRepository(database) }
    val sessionStore: SessionStore by lazy { SessionStore(this) }

    val engine: NativeAudioEngine by lazy { NativeAudioEngine(appScope) }

    val importer: TrackImporter by lazy {
        TrackImporter(this, trackRepository, appScope)
    }

    val audioFocus: AudioFocus by lazy {
        AudioFocus(this) {
            // Focus loss (call, notification, another player): park both decks.
            engine.pause(0)
            engine.pause(1)
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Open the Oboe stream early so device latency is settled before the
        // first play press; the stream idles silently until a deck plays.
        engine.start()
    }
}
