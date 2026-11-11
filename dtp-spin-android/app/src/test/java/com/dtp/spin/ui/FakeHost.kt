package com.dtp.spin.ui

import android.net.Uri
import com.dtp.spin.audio.AudioEngineState
import com.dtp.spin.audio.DeckFeeder
import com.dtp.spin.audio.FakeAudioEngine
import com.dtp.spin.audio.TrackDecoder
import com.dtp.spin.data.CueEntity
import com.dtp.spin.data.SessionPrefs
import com.dtp.spin.data.SessionSnapshot
import com.dtp.spin.data.TrackEntity
import com.dtp.spin.data.TrackLibrary
import com.dtp.spin.library.ImportResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeLibrary(initial: List<TrackEntity> = emptyList()) : TrackLibrary {
    val tracksFlow = MutableStateFlow(initial)
    val cues = mutableMapOf<Long, CueEntity>()
    var deleted = mutableListOf<Long>()
    var lastUpsert: CueEntity? = null
    var failFindByUri = false

    override fun observeAll(): Flow<List<TrackEntity>> = tracksFlow
    override fun search(query: String): Flow<List<TrackEntity>> =
        tracksFlow.map { list ->
            if (query.isBlank()) list
            else list.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.artist.contains(query, ignoreCase = true)
            }
        }

    override suspend fun getById(id: Long): TrackEntity? = tracksFlow.value.firstOrNull { it.id == id }
    override suspend fun findByUri(uri: String): TrackEntity? {
        if (failFindByUri) throw SecurityException("revoked")
        return tracksFlow.value.firstOrNull { it.uri == uri }
    }
    override suspend fun insert(track: TrackEntity): Long {
        tracksFlow.value += track
        return track.id
    }
    override suspend fun updatePeaks(id: Long, peaks: ByteArray?) {}
    override suspend fun deleteTrack(id: Long) {
        tracksFlow.value = tracksFlow.value.filterNot { it.id == id }
        deleted += id
    }
    override fun observeCues(trackId: Long): Flow<List<CueEntity>> =
        tracksFlow.map { cues.values.filter { it.trackId == trackId } }
    override suspend fun firstCue(trackId: Long): CueEntity? = cues[trackId]
    override suspend fun upsertCue(cue: CueEntity): Long {
        cues[cue.trackId] = cue
        lastUpsert = cue
        return 1L
    }
    override suspend fun deleteCue(id: Long) {}
}

class FakeSessions(initial: SessionSnapshot = SessionSnapshot()) : SessionPrefs {
    val flow = MutableStateFlow(initial)
    var saved: SessionSnapshot? = null
    override val session: Flow<SessionSnapshot> get() = flow
    override suspend fun save(snapshot: SessionSnapshot) {
        saved = snapshot
    }
}

class FakeFeeder : DeckFeeder {
    override var onEvent: ((TrackDecoder.Event) -> Unit)? = null
    val loads = mutableListOf<Triple<String, Int, Long>>()
    val seeks = mutableListOf<Pair<Long, Int>>()
    var stopped = 0
    override val isRunning: Boolean get() = loads.isNotEmpty() && stopped == 0

    override fun load(uriString: String, generation: Int, startPositionMs: Long) {
        loads += Triple(uriString, generation, startPositionMs)
    }

    override fun seekTo(positionMs: Long, generation: Int) {
        seeks += positionMs to generation
    }

    override fun stop() {
        stopped++
    }
}

class FakeHost(
    engineInitial: AudioEngineState = AudioEngineState(),
    libraryInitial: List<TrackEntity> = emptyList(),
    sessionInitial: SessionSnapshot = SessionSnapshot(),
    val permissions: MutableSet<String> = mutableSetOf(),
    val focusGranted: Boolean = true,
) : PlayerHost {
    override val engine: FakeAudioEngine = FakeAudioEngine(engineInitial)
    override val library = FakeLibrary(libraryInitial)
    override val sessions = FakeSessions(sessionInitial)
    val feeders = arrayOf(FakeFeeder(), FakeFeeder())
    var serviceStarts = 0
    var serviceStops = 0
    var lastImport: List<Uri>? = null
    var released = mutableListOf<String>()

    override suspend fun importUris(uris: List<Uri>): ImportResult {
        lastImport = uris
        return ImportResult(uris.size, 0, emptyList())
    }

    override fun feeder(deck: Int): DeckFeeder = feeders[deck]
    override fun requestFocus(): Boolean = focusGranted
    override fun serviceStart() {
        serviceStarts++
    }
    override fun serviceStop() {
        serviceStops++
    }
    override fun hasReadPermission(uriString: String): Boolean = uriString in permissions
    override fun releasePermission(uriString: String) {
        released += uriString
    }
}

