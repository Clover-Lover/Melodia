package com.lin0721.linmusic.di

import com.lin0721.linmusic.core.localmusic.LocalMusicApi
import com.lin0721.linmusic.core.player.LyricsResolver
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.PlayerManager
import com.lin0721.linmusic.core.player.ExternalInterruptionResumeController
import com.lin0721.linmusic.core.player.external.ExternalLyricCoordinator
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val playerModule = module {
    single { PlayerManager(androidContext(), get(), get(), get(), get(), get(), get()) }
    single<PlaybackController> { get<PlayerManager>() }
    single {
        val playerManager = get<PlayerManager>()
        val localMusicApi = get<LocalMusicApi>()
        LyricsResolver(
            playbackRepository = get(),
            readLocalLyrics = localMusicApi::readLyrics,
            localUriOf = { songId -> playerManager.queue.value.firstOrNull { it.songId == songId }?.localUri }
        )
    }
    single { ExternalLyricCoordinator(androidContext(), get(), get(), get()) }
    single { ExternalInterruptionResumeController(androidContext(), get()) }
}

