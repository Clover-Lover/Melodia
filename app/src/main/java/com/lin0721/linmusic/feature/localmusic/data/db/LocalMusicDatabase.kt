package com.lin0721.linmusic.feature.localmusic.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [LocalTrackEntity::class],
    version = 1,
    exportSchema = true
)
abstract class LocalMusicDatabase : RoomDatabase() {
    abstract fun localTrackDao(): LocalTrackDao

    companion object {
        const val NAME = "local_music.db"
    }
}
