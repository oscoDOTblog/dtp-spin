package org.mixxx.mobile.library

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val uri: String,
    val durationMs: Long,
    val bpm: Double?
)

@Entity(tableName = "cues", primaryKeys = ["trackId", "slot"])
data class CueEntity(
    val trackId: Long,
    val slot: Int,
    val positionMs: Long
)

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY artist, title")
    fun observeTracks(): Flow<List<TrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(track: TrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCue(cue: CueEntity)

    @Query("SELECT * FROM cues WHERE trackId = :trackId ORDER BY slot")
    suspend fun cuesFor(trackId: Long): List<CueEntity>
}

@Database(entities = [TrackEntity::class, CueEntity::class], version = 1, exportSchema = false)
abstract class DjDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
}
