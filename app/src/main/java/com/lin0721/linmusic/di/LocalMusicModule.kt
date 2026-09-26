package com.lin0721.linmusic.di

import androidx.room.Room
import com.lin0721.linmusic.core.localmusic.LocalMusicApi
import com.lin0721.linmusic.feature.localmusic.data.LocalCoverArtCache
import com.lin0721.linmusic.feature.localmusic.data.LocalLibraryRepository
import com.lin0721.linmusic.feature.localmusic.data.LocalMusicApiImpl
import com.lin0721.linmusic.feature.localmusic.data.db.LocalMusicDatabase
import com.lin0721.linmusic.feature.localmusic.data.legacy.LegacyImportedMusicStore
import com.lin0721.linmusic.feature.localmusic.data.scan.LocalMusicImporter
import com.lin0721.linmusic.feature.localmusic.data.scan.MediaStoreScanner
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val localMusicModule = module {
    single {
        Room.databaseBuilder(androidContext(), LocalMusicDatabase::class.java, LocalMusicDatabase.NAME).build()
    }
    single { get<LocalMusicDatabase>().localTrackDao() }
    single { LegacyImportedMusicStore(androidContext()) }
    single { MediaStoreScanner(androidContext()) }
    single { LocalMusicImporter(androidContext()) }
    single {
        LocalLibraryRepository(
            context = androidContext(),
            dao = get(),
            scanner = get(),
            importer = get(),
            downloadPreferences = get(),
            legacyImportedStore = get()
        )
    }
    single { LocalCoverArtCache(androidContext()) }
    single<LocalMusicApi> { LocalMusicApiImpl(coverArtCache = get()) }
}
