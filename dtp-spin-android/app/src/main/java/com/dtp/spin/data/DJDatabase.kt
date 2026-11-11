package com.dtp.spin.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TrackEntity::class, CueEntity::class],
    version = 1,
    exportSchema = true, // schemas versioned under app/schemas for future migrations
)
abstract class DJDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun cueDao(): CueDao

    companion object {
        @Volatile private var instance: DJDatabase? = null

        fun get(context: Context): DJDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DJDatabase::class.java,
                    "dj.db",
                ).build().also { instance = it }
            }
    }
}
