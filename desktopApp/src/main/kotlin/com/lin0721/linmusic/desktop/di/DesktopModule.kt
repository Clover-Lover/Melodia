package com.lin0721.linmusic.desktop.di

import com.lin0721.linmusic.core.auth.LoginViewModel
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.contentfilter.ContentFilter
import com.lin0721.linmusic.core.download.SongDownloader
import com.lin0721.linmusic.core.network.NetworkStateProvider
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.crypto.XeapiKeyStore
import com.lin0721.linmusic.core.network.crypto.XeapiKeyStoreImpl
import com.lin0721.linmusic.core.player.LyricsResolver
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.preferences.PreferencesStores
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.desktop.platform.DesktopLibraryPreferences
import com.lin0721.linmusic.desktop.platform.DesktopPaths
import com.lin0721.linmusic.desktop.platform.DesktopResourceProvider
import com.lin0721.linmusic.desktop.platform.SilentPlaybackController
import com.lin0721.linmusic.desktop.platform.UnsupportedSongDownloader
import com.lin0721.linmusic.feature.home.ui.HomeViewModel
import com.lin0721.linmusic.feature.library.data.LibraryPreferences
import com.lin0721.linmusic.feature.library.ui.LibraryViewModel
import com.lin0721.linmusic.feature.player.ui.PlayerViewModel
import com.lin0721.linmusic.feature.playlist.ui.PlaylistViewModel
import com.lin0721.linmusic.feature.profile.ui.ProfileViewModel
import com.lin0721.linmusic.feature.search.data.SearchHistoryPreferences
import com.lin0721.linmusic.feature.search.ui.SearchViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

private fun store(name: String) = PreferencesStores.get(DesktopPaths.preferencesFile(name))

val desktopPlatformModule = module {
    single { UserPreferences(store(PreferencesStores.USER)) }
    single { SettingsPreferences(store(PreferencesStores.SETTINGS)) }
    single { SearchHistoryPreferences(store(PreferencesStores.SEARCH_HISTORY)) }
    single<XeapiKeyStore> { XeapiKeyStoreImpl(store(PreferencesStores.XEAPI_KEY)) }
    single { ContentFilter(get()) }
    single<ResourceProvider> { DesktopResourceProvider() }
    // 桌面端不区分 Wi-Fi 与移动网络，统一按 Wi-Fi 音质
    single<NetworkStateProvider> { NetworkStateProvider { true } }
    single<LibraryPreferences> { DesktopLibraryPreferences() }
    single<SongDownloader> { UnsupportedSongDownloader() }
    single<PlaybackController> { SilentPlaybackController() }
    // 桌面第一版没有本地音乐，只取在线歌词
    single { LyricsResolver(get(), readLocalLyrics = { null }, localUriOf = { null }) }
}

// 单窗口应用，页面级 ViewModel 随窗口常驻
val desktopViewModelModule = module {
    singleOf(::LoginViewModel)
    singleOf(::HomeViewModel)
    singleOf(::LibraryViewModel)
    singleOf(::ProfileViewModel)
    singleOf(::SearchViewModel)
    singleOf(::PlaylistViewModel)
    singleOf(::PlayerViewModel)
}
