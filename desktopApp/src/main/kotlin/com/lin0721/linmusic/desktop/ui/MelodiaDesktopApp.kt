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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import com.lin0721.linmusic.core.auth.LoginViewModel
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.desktop.ui.navigation.BackStack
import com.lin0721.linmusic.desktop.ui.navigation.DesktopRoute
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.desktop.ui.theme.DesktopDimens
import com.lin0721.linmusic.feature.home.ui.HomeViewModel
import com.lin0721.linmusic.feature.library.ui.LibraryViewModel
import com.lin0721.linmusic.feature.player.ui.PlayerViewModel
import com.lin0721.linmusic.desktop.player.MpvPlaybackController
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
    val mpvController = playbackController as? MpvPlaybackController

    val backStack = remember { BackStack(DesktopRoute.Home) }
    val userProfile by homeViewModel.userProfile.collectAsState()
    var showLogin by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val isMaximized = windowState.placement == WindowPlacement.Maximized

    LaunchedEffect(Unit) {
        val playbackMessages = mpvController?.messages ?: emptyFlow()
        merge(homeViewModel.toastEvent, libraryViewModel.toastEvent, playbackMessages)
            .collect { snackbarHostState.showSnackbar(it) }
    }

    Box(Modifier.fillMaxSize().background(DesktopColors.WindowBackground)) {
        Column(Modifier.fillMaxSize()) {
            TitleBar(
                backStack = backStack,
                isMaximized = isMaximized,
                userProfile = userProfile,
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
                        }
                    )
                }
                Pane(Modifier.weight(1f)) {
                    when (val route = backStack.current) {
                        DesktopRoute.Home -> HomePage(
                            viewModel = homeViewModel,
                            onPlaylistClick = { id, title -> backStack.navigate(DesktopRoute.Playlist(id, title)) }
                        )
                        is DesktopRoute.Playlist -> PendingPage(route.title)
                    }
                }
                Pane(Modifier.width(DesktopDimens.NowPlayingWidth)) {
                    NowPlayingPanel(playbackController, playerViewModel)
                }
            }
            val volume = mpvController?.volume?.collectAsState()?.value
            PlayerBar(
                controller = playbackController,
                volume = volume,
                onVolumeChange = { mpvController?.setVolume(it) }
            )
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
        WindowResizeHandles(enabled = !isMaximized)
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

// 歌单页在 4b 接入，4a 仅验证导航与前进后退
@Composable
private fun PendingPage(title: String) {
    Column(Modifier.fillMaxSize().padding(32.dp)) {
        Text(title, color = DesktopColors.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("歌单详情将在下一步接入", color = DesktopColors.TextGray, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
    }
}
