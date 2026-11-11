package com.dtp.spin.data

import kotlinx.coroutines.flow.Flow

/** Library contract consumed by the ViewModel; Room impl below, fakes in tests. */
interface TrackLibrary {
    fun observeAll(): Flow<List<TrackEntity>>
    fun search(query: String): Flow<List<TrackEntity>>
    suspend fun getById(id: Long): TrackEntity?
    suspend fun findByUri(uri: String): TrackEntity?
    suspend fun insert(track: TrackEntity): Long
    suspend fun updatePeaks(id: Long, peaks: ByteArray?)
    suspend fun deleteTrack(id: Long)
    fun observeCues(trackId: Long): Flow<List<CueEntity>>
    suspend fun firstCue(trackId: Long): CueEntity?
    suspend fun upsertCue(cue: CueEntity): Long
    suspend fun deleteCue(id: Long)
}

/** Room-backed library repository: tracks, duplicate detection, cue points. */
class TrackRepository(private val db: DJDatabase) : TrackLibrary {
    private val tracks = db.trackDao()
    private val cues = db.cueDao()

    override fun observeAll(): Flow<List<TrackEntity>> = tracks.observeAll()
    override fun search(query: String): Flow<List<TrackEntity>> =
        if (query.isBlank()) observeAll() else tracks.search(query)

    override suspend fun getById(id: Long): TrackEntity? = tracks.getById(id)
    override suspend fun findByUri(uri: String): TrackEntity? = tracks.findByUri(uri)

    /** Returns -1 when the URI is already imported (duplicate). */
    override suspend fun insert(track: TrackEntity): Long = tracks.insert(track)

    override suspend fun updatePeaks(id: Long, peaks: ByteArray?) = tracks.updatePeaks(id, peaks)

    override suspend fun deleteTrack(id: Long) {
        cues.deleteForTrack(id)
        tracks.deleteById(id)
    }

    override fun observeCues(trackId: Long): Flow<List<CueEntity>> = cues.observeForTrack(trackId)
    override suspend fun firstCue(trackId: Long): CueEntity? = cues.firstForTrack(trackId)
    override suspend fun upsertCue(cue: CueEntity): Long = cues.upsert(cue)
    override suspend fun deleteCue(id: Long) = cues.deleteById(id)
}
