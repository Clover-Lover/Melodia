package com.lin0721.linmusic.desktop

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.desktop.di.desktopPlatformModule
import com.lin0721.linmusic.desktop.di.desktopViewModelModule
import com.lin0721.linmusic.desktop.platform.GlobalHotkeys
import com.lin0721.linmusic.desktop.platform.HotkeyAction
import com.lin0721.linmusic.desktop.player.MpvPlaybackController
import com.lin0721.linmusic.desktop.ui.MelodiaDesktopApp
import com.lin0721.linmusic.desktop.ui.lyrics.DesktopLyricWindow
import com.lin0721.linmusic.desktop.ui.theme.MelodiaDesktopTheme
import com.lin0721.linmusic.di.networkModule
import com.lin0721.linmusic.di.repositoryModule
import com.lin0721.linmusic.feature.player.ui.PlayerViewModel
import kotlinx.coroutines.launch
import org.koin.core.context.startKoin
import java.awt.Dimension

private const val VOLUME_STEP = 5

fun main() {
    val koin = startKoin {
        modules(desktopPlatformModule, networkModule, repositoryModule, desktopViewModelModule)
    }.koin
    val controller = koin.get<PlaybackController>()
    val mpvController = controller as? MpvPlaybackController
    val settingsPreferences = koin.get<SettingsPreferences>()
    val playerViewModel = koin.get<PlayerViewModel>()

    application {
        val scope = rememberCoroutineScope()
        var isMainVisible by remember { mutableStateOf(true) }
        var isLyricLocked by remember { mutableStateOf(false) }
        val showDesktopLyric by settingsPreferences.showDesktopLrc.collectAsState(initial = false)
        val isPlaying by controller.playWhenReady.collectAsState()

        val setDesktopLyric: (Boolean) -> Unit = { enabled ->
            scope.launch { settingsPreferences.saveShowDesktopLrc(enabled) }
        }
        // 退出前补报当前曲目播放时长并销毁 mpv 句柄
        val exit = {
            mpvController?.release()
            exitApplication()
        }

        val hotkeys = remember {
            GlobalHotkeys { action ->
                when (action) {
                    HotkeyAction.PlayPause -> controller.togglePlayPause()
                    HotkeyAction.Previous -> controller.skipToPrevious()
                    HotkeyAction.Next -> controller.playNext()
                    HotkeyAction.VolumeUp -> mpvController?.let { it.setVolume(it.volume.value + VOLUME_STEP) }
                    HotkeyAction.VolumeDown -> mpvController?.let { it.setVolume(it.volume.value - VOLUME_STEP) }
                    HotkeyAction.ToggleDesktopLyric -> setDesktopLyric(!showDesktopLyric)
                }
            }
        }
        DisposableEffect(Unit) {
            hotkeys.start()
            onDispose { hotkeys.stop() }
        }

        val notePainter = rememberVectorPainter(Icons.Rounded.MusicNote)
        val trayIcon = remember(notePainter) { TintedPainter(notePainter, Color.White) }
        Tray(
            icon = trayIcon,
            tooltip = "Melodia",
            onAction = { isMainVisible = true },
            menu = {
                Item("显示主窗口", onClick = { isMainVisible = true })
                Separator()
                Item(if (isPlaying) "暂停" else "播放", onClick = controller::togglePlayPause)
                Item("上一首", onClick = controller::skipToPrevious)
                Item("下一首", onClick = controller::playNext)
                Separator()
                CheckboxItem("桌面歌词", checked = showDesktopLyric, onCheckedChange = setDesktopLyric)
                CheckboxItem("锁定桌面歌词", checked = isLyricLocked, onCheckedChange = { isLyricLocked = it })
                Separator()
                Item("退出", onClick = exit)
            }
        )

        val windowState = rememberWindowState(
            size = DpSize(1280.dp, 800.dp),
            position = WindowPosition(Alignment.Center)
        )
        Window(
            // 关闭只隐藏到托盘，音乐继续播放
            onCloseRequest = { isMainVisible = false },
            visible = isMainVisible,
            state = windowState,
            title = "Melodia",
            undecorated = true
        ) {
            LaunchedEffect(Unit) {
                window.minimumSize = Dimension(960, 600)
            }
            LaunchedEffect(isMainVisible) {
                if (isMainVisible) {
                    windowState.isMinimized = false
                    window.toFront()
                }
            }
            MelodiaDesktopTheme {
                MelodiaDesktopApp(windowState = windowState, onClose = { isMainVisible = false })
            }
        }

        DesktopLyricWindow(
            visible = showDesktopLyric,
            locked = isLyricLocked,
            playerViewModel = playerViewModel,
            controller = controller,
            settingsPreferences = settingsPreferences,
            onHide = { setDesktopLyric(false) }
        )
    }
}

// 托盘位于深色任务栏，矢量图标默认黑色需着色
private class TintedPainter(private val source: Painter, private val color: Color) : Painter() {
    override val intrinsicSize: Size get() = source.intrinsicSize

    override fun DrawScope.onDraw() {
        with(source) { draw(size, colorFilter = ColorFilter.tint(color)) }
    }
}
