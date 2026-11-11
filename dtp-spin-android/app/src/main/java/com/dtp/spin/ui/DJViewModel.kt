package com.dtp.spin.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dtp.spin.DJApplication
import com.dtp.spin.audio.AudioEngine
import com.dtp.spin.audio.AudioEngineState
import com.dtp.spin.audio.DeckFeeder
import com.dtp.spin.audio.DeckState
import com.dtp.spin.audio.TrackDecoder
import com.dtp.spin.data.CueEntity
import com.dtp.spin.data.SessionPrefs
import com.dtp.spin.data.SessionSnapshot
import com.dtp.spin.data.TrackEntity
import com.dtp.spin.data.TrackLibrary
import com.dtp.spin.library.ImportResult
import com.dtp.spin.library.PeakUtils
import com.dtp.spin.library.TrackImporter
import com.dtp.spin.library.hasPersistedRead
import com.dtp.spin.library.listAudioUris
import com.dtp.spin.library.persistTreePermission
import com.dtp.spin.playback.AudioFocus
import com.dtp.spin.playback.PlaybackService
import com.dtp.spin.util.AudioMath
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Control parameters + loaded-track binding for one deck (UI-owned). */
data class DeckControls(
    val track: TrackEntity? = null,
    val peaks: FloatArray? = null,
    val rate: Float = 1f,
    val volume: Float = 1f,
    val gainDb: Float = 0f,
    val eqLowDb: Float = 0f,
    val eqMidDb: Float = 0f,
    val eqHighDb: Float = 0f,
    val cueMs: Long? = null,
    val scrubPreviewMs: Long? = null,
)

data class DeckFullState(
    val controls: DeckControls = DeckControls(),
    val engine: DeckState = DeckState(0),
)

data class UiMessage(val text: String, val seq: Long)

data class DJUiState(
    val deckA: DeckFullState = DeckFullState(DeckControls(), DeckState(0)),
    val deckB: DeckFullState = DeckFullState(DeckControls(), DeckState(1)),
    val crossfade: Float = 0f,
    val tracks: List<TrackEntity> = emptyList(),
    val query: String = "",
    val message: UiMessage? = null,
    val restoring: Boolean = true,
    val streamRunning: Boolean = false,
)

/**
 * Everything the UI needs that is not Context-bound, behind [PlayerHost] so
 * unit tests run on plain JUnit with fakes (no Robolectric).
 */
interface PlayerHost {
    val engine: AudioEngine
    val library: TrackLibrary
    val sessions: SessionPrefs
    suspend fun importUris(uris: List<Uri>): ImportResult
    suspend fun importFolder(treeUri: Uri): ImportResult
    fun feeder(deck: Int): DeckFeeder
    fun requestFocus(): Boolean
    fun serviceStart()
    fun serviceStop()
    fun hasReadPermission(uriString: String): Boolean
    fun releasePermission(uriString: String)
}

class DJViewModel(private val host: PlayerHost) : ViewModel() {

    private val engine: AudioEngine get() = host.engine
    private val library: TrackLibrary get() = host.library

    private val controls = arrayOf(MutableStateFlow(DeckControls()), MutableStateFlow(DeckControls()))
    private val _query = MutableStateFlow("")
    private val _crossfade = MutableStateFlow(0f)
    private val _message = MutableStateFlow<UiMessage?>(null)
    private val _restoring = MutableStateFlow(true)
    private var messageSeq = 0L
    private var saveJob: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    private val libraryFlow: Flow<List<TrackEntity>> =
        _query.flatMapLatest { q -> library.search(q) }

    private data class MixerSettings(
        val crossfade: Float,
        val query: String,
        val message: UiMessage?,
        val restoring: Boolean,
    )

    private val decksFlow: Flow<Pair<DeckControls, DeckControls>> =
        combine(controls[0], controls[1]) { a, b -> a to b }

    private val settingsFlow: Flow<MixerSettings> =
        combine(_crossfade, _query, _message, _restoring) { xf, query, message, restoring ->
            MixerSettings(xf, query, message, restoring)
        }

    val uiState: StateFlow<DJUiState> = combine(
        engine.state,
        decksFlow,
        libraryFlow,
        settingsFlow,
    ) { st, decks, tracks, s ->
        DJUiState(
            deckA = DeckFullState(decks.first, st.deckA),
            deckB = DeckFullState(decks.second, st.deckB),
            crossfade = s.crossfade,
            tracks = tracks,
            query = s.query,
            message = s.message,
            restoring = s.restoring,
            streamRunning = st.streamRunning,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DJUiState())

    init {
        host.feeder(0).onEvent = { onDecoderEvent(0, it) }
        host.feeder(1).onEvent = { onDecoderEvent(1, it) }
        restoreSession()
    }

    // ---------- library ----------

    fun setQuery(q: String) {
        _query.value = q
    }

    fun importUris(uris: List<Uri>) {
        viewModelScope.launch {
            say(describe(host.importUris(uris)))
        }
    }

    fun importFolder(treeUri: Uri) {
        viewModelScope.launch {
            say(describe(host.importFolder(treeUri)))
        }
    }

    fun deleteTrack(track: TrackEntity) {
        viewModelScope.launch {
            for (deck in 0..1) {
                if (controls[deck].value.track?.id == track.id) unloadDeck(deck)
            }
            library.deleteTrack(track.id)
            host.releasePermission(track.uri)
            scheduleSave()
        }
    }

    fun dismissMessage() {
        _message.value = null
    }

    // ---------- deck loading ----------

    fun loadToDeck(deck: Int, track: TrackEntity, startMs: Long = 0L) {
        viewModelScope.launch {
            loadToDeckInternal(deck, track, startMs)
            scheduleSave()
        }
    }

    private suspend fun loadToDeckInternal(deck: Int, track: TrackEntity, startMs: Long) {
        if (!host.hasReadPermission(track.uri)) {
            say("“${track.title}” is unavailable — its file was moved or permission was revoked")
            return
        }
        val gen = engine.nextGeneration(deck)
        try {
            host.feeder(deck).load(track.uri, gen, startMs)
        } catch (e: Exception) {
            say("could not load “${track.title}”: ${e.message}")
            return
        }
        engine.setDeckTrackId(deck, track.id)
        pushParams(deck, controls[deck].value)
        val cue = library.firstCue(track.id)
        controls[deck].value = controls[deck].value.copy(
            track = track,
            peaks = PeakUtils.decode(track.peaks),
            cueMs = cue?.positionMs,
        )
    }

    private fun unloadDeck(deck: Int) {
        host.feeder(deck).stop()
        engine.pause(deck)
        engine.setDeckTrackId(deck, null)
        controls[deck].value = controls[deck].value.copy(
            track = null, peaks = null, cueMs = null, scrubPreviewMs = null,
        )
    }

    // ---------- transport ----------

    fun togglePlay(deck: Int) {
        if (uiState.value.let { if (deck == 0) it.deckA.engine.playing else it.deckB.engine.playing }) {
            pause(deck)
        } else {
            play(deck)
        }
    }

    fun play(deck: Int) {
        val full = if (deck == 0) uiState.value.deckA else uiState.value.deckB
        if (full.controls.track == null) {
            say("load a track into deck ${if (deck == 0) "A" else "B"} first")
            return
        }
        if (!host.requestFocus()) {
            say("could not gain audio focus")
            return
        }
        if (full.engine.ended) {
            seekTo(deck, full.controls.cueMs ?: 0L)
        }
        engine.play(deck)
        host.serviceStart()
    }

    fun pause(deck: Int) {
        engine.pause(deck)
        maybeStopService()
        scheduleSave()
    }

    fun pauseAll() {
        engine.pause(0)
        engine.pause(1)
        maybeStopService()
        scheduleSave()
    }

    private fun maybeStopService() {
        val st = engine.state.value
        if (!st.deckA.playing && !st.deckB.playing) host.serviceStop()
    }

    fun cueDown(deck: Int) {
        if (controls[deck].value.track == null) return
        engine.cueDown(deck)
    }

    fun cueUp(deck: Int) {
        if (controls[deck].value.track == null) return
        engine.cueUp(deck)
    }

    /** Long-press Cue: persist the current playhead as this track's cue point. */
    fun setCueAtCurrent(deck: Int) {
        viewModelScope.launch {
            val full = if (deck == 0) uiState.value.deckA else uiState.value.deckB
            val track = full.controls.track ?: return@launch
            val posMs = full.engine.positionMs
            val rate = full.controls.track?.sampleRateHz ?: 44100
            engine.setCue(deck, posMs * rate / 1000)
            library.upsertCue(CueEntity(trackId = track.id, positionMs = posMs))
            controls[deck].value = controls[deck].value.copy(cueMs = posMs)
            say("cue set at ${formatMs(posMs)}")
        }
    }

    fun seekTo(deck: Int, positionMs: Long) {
        val track = controls[deck].value.track ?: return
        val clamped = positionMs.coerceIn(0L, track.durationMs)
        val gen = engine.nextGeneration(deck)
        val frame = clamped * track.sampleRateHz / 1000
        engine.seek(deck, frame, gen)
        host.feeder(deck).seekTo(clamped, gen)
        scheduleSave()
    }

    fun scrubPreview(deck: Int, positionMs: Long?) {
        controls[deck].value = controls[deck].value.copy(scrubPreviewMs = positionMs)
    }

    // ---------- mixer params ----------

    fun setTempoSlider(deck: Int, slider: Float) {
        val rate = AudioMath.tempoToRate(slider, TEMPO_RANGE)
        controls[deck].value = controls[deck].value.copy(rate = rate)
        engine.setRate(deck, rate)
        scheduleSave()
    }

    fun nudgeTempo(deck: Int, slider: Float) {
        // Live drag without persistence churn; commit on release via setTempoSlider.
        engine.setRate(deck, AudioMath.tempoToRate(slider, TEMPO_RANGE))
    }

    fun setVolume(deck: Int, volume: Float) {
        controls[deck].value = controls[deck].value.copy(volume = volume.coerceIn(0f, 1f))
        engine.setVolume(deck, controls[deck].value.volume)
        scheduleSave()
    }

    fun setGainDb(deck: Int, gainDb: Float) {
        controls[deck].value = controls[deck].value.copy(gainDb = gainDb.coerceIn(-24f, 6f))
        engine.setGain(deck, AudioMath.dbToLinear(controls[deck].value.gainDb))
        scheduleSave()
    }

    fun setEq(deck: Int, lowDb: Float, midDb: Float, highDb: Float) {
        controls[deck].value = controls[deck].value.copy(
            eqLowDb = lowDb.coerceIn(-24f, 6f),
            eqMidDb = midDb.coerceIn(-24f, 6f),
            eqHighDb = highDb.coerceIn(-24f, 6f),
        )
        val c = controls[deck].value
        engine.setEq(deck, c.eqLowDb, c.eqMidDb, c.eqHighDb)
        scheduleSave()
    }

    fun resetEq(deck: Int) = setEq(deck, 0f, 0f, 0f)

    fun setCrossfade(xfade: Float) {
        _crossfade.value = xfade.coerceIn(-1f, 1f)
        engine.setCrossfade(_crossfade.value)
        scheduleSave()
    }

    // ---------- internals ----------

    private fun pushParams(deck: Int, c: DeckControls) {
        engine.setRate(deck, c.rate)
        engine.setVolume(deck, c.volume)
        engine.setGain(deck, AudioMath.dbToLinear(c.gainDb))
        engine.setEq(deck, c.eqLowDb, c.eqMidDb, c.eqHighDb)
    }

    private fun onDecoderEvent(deck: Int, event: TrackDecoder.Event) {
        when (event) {
            is TrackDecoder.Event.Error -> {
                val title = controls[deck].value.track?.title ?: "track"
                android.util.Log.w(
                    "DJViewModel",
                    "decoder error on deck $deck ($title): ${event.message}",
                )
                say("playback error on deck ${if (deck == 0) "A" else "B"} (“$title”): ${event.message}")
            }
            TrackDecoder.Event.EndOfStream -> {
                // Native deck auto-parks at the track end; persist final position.
                scheduleSave()
            }
        }
    }

    private fun restoreSession() {
        viewModelScope.launch {
            val snap = host.sessions.session.first()
            _crossfade.value = snap.crossfade
            engine.setCrossfade(snap.crossfade)
            for (deck in 0..1) {
                controls[deck].value = DeckControls(
                    rate = snap.deckRate[deck],
                    volume = snap.deckVolume[deck],
                    gainDb = snap.deckGainDb[deck],
                    eqLowDb = snap.deckEqLowDb[deck],
                    eqMidDb = snap.deckEqMidDb[deck],
                    eqHighDb = snap.deckEqHighDb[deck],
                )
                pushParams(deck, controls[deck].value)
                val uri = snap.deckUri[deck]
                if (!uri.isNullOrBlank()) {
                    val track = try {
                        host.library.findByUri(uri)
                    } catch (_: Exception) {
                        null
                    }
                    if (track != null) {
                        loadToDeckInternal(deck, track, snap.deckPositionMs[deck])
                    } else {
                        say("previous track on deck ${if (deck == 0) "A" else "B"} is no longer in the library")
                    }
                }
            }
            _restoring.value = false
        }
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            saveNow()
        }
    }

    private suspend fun saveNow() {
        val st = engine.state.value
        host.sessions.save(
            SessionSnapshot(
                deckUri = arrayOf(
                    controls[0].value.track?.uri,
                    controls[1].value.track?.uri,
                ),
                deckPositionMs = longArrayOf(st.deckA.positionMs, st.deckB.positionMs),
                deckRate = floatArrayOf(controls[0].value.rate, controls[1].value.rate),
                deckVolume = floatArrayOf(controls[0].value.volume, controls[1].value.volume),
                deckGainDb = floatArrayOf(controls[0].value.gainDb, controls[1].value.gainDb),
                deckEqLowDb = floatArrayOf(controls[0].value.eqLowDb, controls[1].value.eqLowDb),
                deckEqMidDb = floatArrayOf(controls[0].value.eqMidDb, controls[1].value.eqMidDb),
                deckEqHighDb = floatArrayOf(controls[0].value.eqHighDb, controls[1].value.eqHighDb),
                crossfade = _crossfade.value,
            ),
        )
    }

    private fun say(text: String) {
        _message.value = UiMessage(text, ++messageSeq)
    }

    private fun describe(r: ImportResult): String {
        val parts = mutableListOf<String>()
        if (r.imported > 0) parts += "imported ${r.imported}"
        if (r.duplicates > 0) parts += "${r.duplicates} already in library"
        r.failures.forEach { parts += it.second }
        return if (parts.isEmpty()) "nothing to import" else parts.joinToString(" • ")
    }

    override fun onCleared() {
        saveJob?.cancel()
        host.feeder(0).stop()
        host.feeder(1).stop()
        host.serviceStop()
    }

    companion object {
        const val TEMPO_RANGE = 0.08f // ±8 %
        private const val SAVE_DEBOUNCE_MS = 400L

        fun formatMs(ms: Long): String {
            val totalSec = (ms / 1000).toInt()
            return "%d:%02d".format(totalSec / 60, totalSec % 60)
        }
    }
}

/** Production host wiring the ViewModel to app singletons. */
class AppPlayerHost(private val app: DJApplication) : PlayerHost {
    private val feeders: Array<DeckFeeder> by lazy {
        arrayOf(
            TrackDecoder(app.applicationContext, app.engine, 0),
            TrackDecoder(app.applicationContext, app.engine, 1),
        )
    }

    override val engine: AudioEngine get() = app.engine
    override val library: TrackLibrary get() = app.trackRepository
    override val sessions: SessionPrefs get() = app.sessionStore

    override suspend fun importUris(uris: List<Uri>): ImportResult =
        TrackImporter(app.applicationContext, app.trackRepository, app.appScope)
            .importUris(uris)

    override suspend fun importFolder(treeUri: Uri): ImportResult {
        val ctx = app.applicationContext
        persistTreePermission(ctx, treeUri)
        val uris = listAudioUris(ctx, treeUri)
        if (uris.isEmpty()) {
            return ImportResult(0, 0, listOf(treeUri to "no audio files found in this folder"))
        }
        return TrackImporter(ctx, app.trackRepository, app.appScope).importUris(uris)
    }

    override fun feeder(deck: Int): DeckFeeder = feeders[deck]

    override fun requestFocus(): Boolean =
        try {
            app.audioFocus.request()
        } catch (_: Exception) {
            true
        }

    override fun serviceStart() = PlaybackService.start(app.applicationContext)
    override fun serviceStop() = PlaybackService.stop(app.applicationContext)

    override fun hasReadPermission(uriString: String): Boolean =
        hasPersistedRead(app.applicationContext, uriString)

    override fun releasePermission(uriString: String) {
        app.contentResolver.persistedUriPermissions
            .firstOrNull { it.uri.toString() == uriString }
            ?.let {
                try {
                    app.contentResolver.releasePersistableUriPermission(
                        it.uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                } catch (_: SecurityException) {
                }
            }
    }
}

class DJViewModelFactory(private val app: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(DJViewModel::class.java))
        return DJViewModel(AppPlayerHost(app as DJApplication)) as T
    }
}
