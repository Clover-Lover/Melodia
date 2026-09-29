package com.lin0721.linmusic.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import com.lin0721.linmusic.core.auth.LoginViewModel
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.desktop.ui.navigation.BackStack
import com.lin0721.linmusic.desktop.ui.navigation.DesktopRoute
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.desktop.ui.theme.DesktopDimens
import com.lin0721.linmusic.feature.artist.ui.ArtistViewModel
import com.lin0721.linmusic.feature.home.ui.HomeViewModel
import com.lin0721.linmusic.feature.library.ui.LibraryViewModel
import com.lin0721.linmusic.feature.player.ui.PlayerViewModel
import com.lin0721.linmusic.feature.playlist.ui.PlaylistViewModel
import com.lin0721.linmusic.feature.search.ui.DiscoveryUiState
import com.lin0721.linmusic.feature.search.ui.PlaylistCategoryViewModel
import com.lin0721.linmusic.feature.search.ui.SearchViewModel
import com.lin0721.linmusic.desktop.player.MpvPlaybackController
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.merge
import org.koin.core.context.GlobalContext

@Composable
fun WindowScope.MelodiaDesktopApp(windowState: WindowState, onClose: () -> Unit) {
    val koin = remember { GlobalContext.get() }
    val homeViewModel = remember { koin.get<HomeViewModel>() }
    val libraryViewModel = remember { koin.get<LibraryViewModel>() }
    val loginViewModel = remember { koin.get<LoginViewModel>() }
    val playbackController = remember { koin.get<PlaybackController>() }
    val playerViewModel = remember { koin.get<PlayerViewModel>() }
    val searchViewModel = remember { koin.get<SearchViewModel>() }
    val playlistViewModel = remember { koin.get<PlaylistViewModel>() }
    val categoryViewModel = remember { koin.get<PlaylistCategoryViewModel>() }
    val artistViewModel = remember { koin.get<ArtistViewModel>() }
    val mpvController = playbackController as? MpvPlaybackController

    val backStack = remember { BackStack(DesktopRoute.Home) }
    val userProfile by homeViewModel.userProfile.collectAsState()
    var showLogin by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val isMaximized = windowState.placement == WindowPlacement.Maximized
    val searchInput by searchViewModel.inputState.collectAsState()
    val discovery by searchViewModel.discoveryState.collectAsState()
    val defaultKeyword = (discovery as? DiscoveryUiState.Success)?.defaultKeyword.orEmpty()
    val openSearch = { backStack.navigate(DesktopRoute.Search) }
    val navigatorMessages = remember { MutableSharedFlow<String>(extraBufferCapacity = 8) }
    val navigator = DesktopNavigator(
        isLoggedIn = userProfile != null,
        openArtist = { id, name -> backStack.navigate(DesktopRoute.Artist(id, name)) },
        openAlbum = { id, name -> backStack.navigate(DesktopRoute.Playlist(id, name, isAlbum = true)) },
        showMessage = { navigatorMessages.tryEmit(it) }
    )

    LaunchedEffect(Unit) {
        val playbackMessages = mpvController?.messages ?: emptyFlow()
        merge(
            homeViewModel.toastEvent,
            libraryViewModel.toastEvent,
            searchViewModel.toastEvent,
            playlistViewModel.toastEvent,
            categoryViewModel.toastEvent,
            playerViewModel.toastEvent,
            artistViewModel.toastEvent,
            navigatorMessages,
            playbackMessages
        )
            .collect { snackbarHostState.showSnackbar(it) }
    }

    CompositionLocalProvider(LocalDesktopNavigator provides navigator) {
        Box(Modifier.fillMaxSize().background(DesktopColors.WindowBackground)) {
            Column(Modifier.fillMaxSize()) {
                TitleBar(
                    backStack = backStack,
                    isMaximized = isMaximized,
                    userProfile = userProfile,
                    searchQuery = searchInput.query,
                    searchPlaceholder = defaultKeyword.ifBlank { "想播放什么？" },
                    onSearchQueryChange = { query ->
                        openSearch()
                        // 发现态下开始输入才切到输入态，聚焦本身不切换，保证热搜榜可见
                        searchViewModel.activateSearch()
                        searchViewModel.updateQuery(query)
                    },
                    onSearchFocused = openSearch,
                    onSearchSubmit = {
                        openSearch()
                        searchViewModel.searchWithKeyword(searchInput.query.ifBlank { defaultKeyword })
                    },
                    isBrowseActive = backStack.current == DesktopRoute.Browse,
                    onBrowseClick = { backStack.navigate(DesktopRoute.Browse) },
                    onAvatarClick = { if (userProfile == null) showLogin = true },
                    onMinimize = { windowState.isMinimized = true },
                    onToggleMaximize = {
                        windowState.placement = if (isMaximized) WindowPlacement.Floating else WindowPlacement.Maximized
                    },
                    onClose = onClose
                )
                Row(
                    Modifier.weight(1f).padding(horizontal = DesktopDimens.PaneGap),
                    horizontalArrangement = Arrangement.spacedBy(DesktopDimens.PaneGap)
                ) {
                    Pane(Modifier.width(DesktopDimens.SidebarWidth)) {
                        LibrarySidebar(
                            viewModel = libraryViewModel,
                            isLoggedIn = userProfile != null,
                            onLoginClick = { showLogin = true },
                            onPlaylistClick = { item ->
                                item.id.toLongOrNull()?.let { backStack.navigate(DesktopRoute.Playlist(it, item.title)) }
                            },
                            onAlbumClick = { item ->
                                item.id.toLongOrNull()?.let { navigator.openAlbum(it, item.title) }
                            },
                            onArtistClick = { item ->
                                item.id.toLongOrNull()?.let { navigator.openArtist(it, item.title) }
                            }
                        )
                    }
                    Pane(Modifier.weight(1f)) {
                        when (val route = backStack.current) {
                            DesktopRoute.Home -> HomePage(
                                viewModel = homeViewModel,
                                onPlaylistClick = { id, title -> backStack.navigate(DesktopRoute.Playlist(id, title)) }
                            )
                            is DesktopRoute.Playlist -> PlaylistPage(
                                playlistId = route.id,
                                isAlbum = route.isAlbum,
                                viewModel = playlistViewModel,
                                controller = playbackController
                            )
                            DesktopRoute.Browse -> BrowsePage(
                                viewModel = searchViewModel,
                                onHotSearchClick = { keyword ->
                                    backStack.navigate(DesktopRoute.Search)
                                    searchViewModel.searchWithKeyword(keyword)
                                },
                                onCategoryClick = { backStack.navigate(DesktopRoute.PlaylistCategory(it)) }
                            )
                            is DesktopRoute.PlaylistCategory -> PlaylistCategoryPage(
                                category = route.name,
                                viewModel = categoryViewModel,
                                onPlaylistClick = { id, title -> backStack.navigate(DesktopRoute.Playlist(id, title)) }
                            )
                            is DesktopRoute.Artist -> ArtistPage(
                                artistId = route.id,
                                viewModel = artistViewModel,
                                controller = playbackController
                            )
                            DesktopRoute.Search -> SearchPage(
                                viewModel = searchViewModel,
                                controller = playbackController,
                                onOpenPlaylist = { id, title, isAlbum ->
                                    backStack.navigate(DesktopRoute.Playlist(id, title, isAlbum))
                                }
                            )
                        }
                    }
                    Pane(Modifier.width(DesktopDimens.NowPlayingWidth)) {
                        NowPlayingPanel(playbackController, playerViewModel)
                    }
                }
                val volume = mpvController?.volume?.collectAsState()?.value
                PlayerBar(
                    controller = playbackController,
                    playerViewModel = playerViewModel,
                    volume = volume,
                    onVolumeChange = { mpvController?.setVolume(it) }
                )
            }
            SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
            WindowResizeHandles(enabled = !isMaximized)
        }
    }

    if (showLogin) {
        LoginDialog(
            viewModel = loginViewModel,
            onLoginSuccess = { cookies ->
                homeViewModel.handleLoginSuccess(cookies)
                showLogin = false
            },
            onDismiss = { showLogin = false }
        )
    }
}

@Composable
private fun Pane(modifier: Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.fillMaxHeight().clip(RoundedCornerShape(DesktopDimens.PaneRadius)).background(DesktopColors.Pane)
    ) {
        content()
    }
}
