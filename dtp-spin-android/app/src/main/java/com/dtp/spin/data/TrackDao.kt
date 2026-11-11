package com.dtp.spin.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY dateAddedMs DESC")
    fun observeAll(): Flow<List<TrackEntity>>

    @Query(
        """SELECT * FROM tracks
           WHERE title LIKE '%' || :query || '%' COLLATE NOCASE
              OR artist LIKE '%' || :query || '%' COLLATE NOCASE
              OR album LIKE '%' || :query || '%' COLLATE NOCASE
           ORDER BY dateAddedMs DESC""",
    )
    fun search(query: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun getById(id: Long): TrackEntity?

    @Query("SELECT * FROM tracks WHERE uri = :uri LIMIT 1")
    suspend fun findByUri(uri: String): TrackEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(track: TrackEntity): Long

    @Update
    suspend fun update(track: TrackEntity)

    @Query("UPDATE tracks SET peaks = :peaks WHERE id = :id")
    suspend fun updatePeaks(id: Long, peaks: ByteArray?)

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface CueDao {
    @Query("SELECT * FROM cues WHERE trackId = :trackId ORDER BY positionMs ASC")
    fun observeForTrack(trackId: Long): Flow<List<CueEntity>>

    @Query("SELECT * FROM cues WHERE trackId = :trackId ORDER BY positionMs ASC LIMIT 1")
    suspend fun firstForTrack(trackId: Long): CueEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cue: CueEntity): Long

    @Query("DELETE FROM cues WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM cues WHERE trackId = :trackId")
    suspend fun deleteForTrack(trackId: Long)
}
