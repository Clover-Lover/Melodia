package com.lin0721.linmusic.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.lin0721.linmusic.desktop.platform.DesktopPreferences
import com.lin0721.linmusic.desktop.platform.LibraryMode
import com.lin0721.linmusic.desktop.platform.LibraryViewMode
import com.lin0721.linmusic.desktop.ui.navigation.BackStack
import com.lin0721.linmusic.desktop.ui.navigation.DesktopRoute
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.desktop.ui.theme.DesktopDimens
import com.lin0721.linmusic.feature.artist.ui.ArtistViewModel
import com.lin0721.linmusic.feature.home.ui.HomeViewModel
import com.lin0721.linmusic.feature.library.ui.LibraryItem
import com.lin0721.linmusic.feature.library.ui.LibraryItemType
import com.lin0721.linmusic.feature.library.ui.LibraryViewModel
import com.lin0721.linmusic.feature.player.ui.PlayerViewModel
import com.lin0721.linmusic.feature.playlist.ui.PlaylistViewModel
import com.lin0721.linmusic.feature.search.ui.DiscoveryUiState
import com.lin0721.linmusic.feature.search.ui.PlaylistCategoryViewModel
import com.lin0721.linmusic.feature.search.ui.SearchViewModel
import com.lin0721.linmusic.desktop.player.MpvPlaybackController
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
    val desktopPreferences = remember { koin.get<DesktopPreferences>() }
    val mpvController = playbackController as? MpvPlaybackController

    val backStack = remember { BackStack(DesktopRoute.Home) }
    val userProfile by homeViewModel.userProfile.collectAsState()
    var showLogin by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val isMaximized = windowState.placement == WindowPlacement.Maximized
    val nowPlaying by playbackController.nowPlaying.collectAsState()
    // 初值为关闭：读到已保存的开启状态后，侧栏随动画展开
    val nowPlayingOpen by desktopPreferences.nowPlayingPanelOpen.collectAsState(initial = false)
    val scope = rememberCoroutineScope()
    val setNowPlayingOpen: (Boolean) -> Unit = { open ->
        scope.launch { desktopPreferences.saveNowPlayingPanelOpen(open) }
    }
    // 启动时同步读到上次的形态，避免先按默认宽度再动画到收起
    val initialLibraryMode = remember { runBlocking { desktopPreferences.libraryMode.first() } }
    val libraryMode by desktopPreferences.libraryMode.collectAsState(initial = initialLibraryMode)
    val setLibraryMode: (LibraryMode) -> Unit = { mode ->
        scope.launch { desktopPreferences.saveLibraryMode(mode) }
    }
    val initialViewMode = remember { runBlocking { desktopPreferences.libraryViewMode.first() } }
    val libraryViewMode by desktopPreferences.libraryViewMode.collectAsState(initial = initialViewMode)
    val setLibraryViewMode: (LibraryViewMode) -> Unit = { mode ->
        scope.launch { desktopPreferences.saveLibraryViewMode(mode) }
    }
    // 音乐库展开时右侧栏先收成窄条，期间的开合只在本次展开内有效，不改保存的开关值；
    // 手动打开右侧栏时音乐库保持展开，只是让出宽度
    val isLibraryExpanded = libraryMode == LibraryMode.EXPANDED
    var expandedDockOpen by remember { mutableStateOf(false) }
    LaunchedEffect(isLibraryExpanded) { expandedDockOpen = false }
    val dockOpen = if (isLibraryExpanded) expandedDockOpen else nowPlayingOpen
    val setDockOpen: (Boolean) -> Unit = { open ->
        if (isLibraryExpanded) expandedDockOpen = open else setNowPlayingOpen(open)
    }
    val searchInput by searchViewModel.inputState.collectAsState()
    val discovery by searchViewModel.discoveryState.collectAsState()
    val defaultKeyword = (discovery as? DiscoveryUiState.Success)?.defaultKeyword.orEmpty()
    val openSearch = { backStack.navigate(DesktopRoute.Search) }
    val navigatorMessages = remember { MutableSharedFlow<String>(extraBufferCapacity = 8) }
    val navigator = DesktopNavigator(
        isLoggedIn = userProfile != null,
        openArtist = { id, name -> backStack.navigate(DesktopRoute.Artist(id, name)) },
        openPlaylist = { id, name -> backStack.navigate(DesktopRoute.Playlist(id, name)) },
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

    val openLibraryItem: (LibraryItem) -> Unit = { item ->
        item.id.toLongOrNull()?.let { id ->
            when (item.type) {
                LibraryItemType.PLAYLIST -> backStack.navigate(DesktopRoute.Playlist(id, item.title))
                LibraryItemType.ALBUM -> navigator.openAlbum(id, item.title)
                LibraryItemType.ARTIST -> navigator.openArtist(id, item.title)
                // 桌面端暂不支持 MV 播放
                LibraryItemType.MV -> Unit
            }
        }
    }

    val dockState = rememberNowPlayingDockState(hasTrack = nowPlaying != null, open = dockOpen)
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
                    onLoginClick = { showLogin = true },
                    onSettingsClick = { backStack.navigate(DesktopRoute.Settings) },
                    onLogoutClick = homeViewModel::logout,
                    onMinimize = { windowState.isMinimized = true },
                    onToggleMaximize = {
                        windowState.placement = if (isMaximized) WindowPlacement.Floating else WindowPlacement.Maximized
                    },
                    onClose = onClose
                )
                BoxWithConstraints(Modifier.weight(1f)) {
                    val workspace = rememberWorkspaceLayout(libraryMode, maxWidth - DesktopDimens.PaneGap * 2, dockState)
                    CompositionLocalProvider(LocalPaneWidthExtra provides { workspace.centerWidthExtra }) {
                        Row(Modifier.fillMaxSize().padding(horizontal = DesktopDimens.PaneGap)) {
                            LibraryPane(
                                mode = libraryMode,
                                width = workspace.libraryWidth,
                                expandedWidth = workspace.expandedWidth,
                                viewModel = libraryViewModel,
                                isLoggedIn = userProfile != null,
                                onLoginClick = { showLogin = true },
                                onItemClick = openLibraryItem,
                                onModeChange = setLibraryMode,
                                viewMode = libraryViewMode,
                                onViewModeChange = setLibraryViewMode
                            )
                            Spacer(Modifier.width(workspace.centerGap))
                            Pane(Modifier.weight(1f)) {
                                Box(Modifier.settledLayoutWidth(LocalPaneWidthExtra.current).fillMaxSize()) {
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
                                        DesktopRoute.Settings -> SettingsPage()
                                        DesktopRoute.Search -> SearchPage(
                                            viewModel = searchViewModel,
                                            controller = playbackController,
                                            onOpenPlaylist = { id, title, isAlbum ->
                                                backStack.navigate(DesktopRoute.Playlist(id, title, isAlbum))
                                            }
                                        )
                                    }
                                }
                            }
                            NowPlayingDock(
                                state = dockState,
                                hasTrack = nowPlaying != null,
                                open = dockOpen,
                                onOpenChange = setDockOpen,
                                controller = playbackController,
                                playerViewModel = playerViewModel
                            )
                        }
                    }
                }
                val volume = mpvController?.volume?.collectAsState()?.value
                PlayerBar(
                    controller = playbackController,
                    playerViewModel = playerViewModel,
                    volume = volume,
                    onVolumeChange = { mpvController?.setVolume(it) },
                    nowPlayingOpen = dockOpen,
                    onToggleNowPlaying = { setDockOpen(!dockOpen) }
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
