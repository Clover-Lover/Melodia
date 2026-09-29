package com.lin0721.linmusic.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.desktop.di.desktopPlatformModule
import com.lin0721.linmusic.desktop.player.MpvPlaybackController
import com.lin0721.linmusic.desktop.di.desktopViewModelModule
import com.lin0721.linmusic.desktop.ui.MelodiaDesktopApp
import com.lin0721.linmusic.desktop.ui.theme.MelodiaDesktopTheme
import com.lin0721.linmusic.di.networkModule
import com.lin0721.linmusic.di.repositoryModule
import org.koin.core.context.startKoin
import java.awt.Dimension

fun main() {
    val koinApp = startKoin {
        modules(desktopPlatformModule, networkModule, repositoryModule, desktopViewModelModule)
    }
    // 退出前补报当前曲目播放时长并销毁 mpv 句柄
    val shutdown = {
        (koinApp.koin.get<PlaybackController>() as? MpvPlaybackController)?.release()
    }
    application {
        val exit = {
            shutdown()
            exitApplication()
        }
        val windowState = rememberWindowState(
            size = DpSize(1280.dp, 800.dp),
            position = WindowPosition(Alignment.Center)
        )
        Window(
            onCloseRequest = exit,
            state = windowState,
            title = "Melodia",
            undecorated = true
        ) {
            LaunchedEffect(Unit) {
                window.minimumSize = Dimension(960, 600)
            }
            MelodiaDesktopTheme {
                MelodiaDesktopApp(windowState = windowState, onClose = exit)
            }
        }
    }
}
