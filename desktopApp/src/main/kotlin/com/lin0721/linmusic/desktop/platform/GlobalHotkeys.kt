package com.lin0721.linmusic.desktop.platform

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.desktop.platform.win.Kernel32
import com.lin0721.linmusic.desktop.platform.win.Msg
import com.lin0721.linmusic.desktop.platform.win.User32
import java.util.concurrent.CountDownLatch
import javax.swing.SwingUtilities

private const val TAG = "GlobalHotkeys"

private const val VK_LEFT = 0x25
private const val VK_UP = 0x26
private const val VK_RIGHT = 0x27
private const val VK_DOWN = 0x28
private const val VK_L = 0x4C
private const val VK_P = 0x50
private const val VK_MEDIA_NEXT_TRACK = 0xB0
private const val VK_MEDIA_PREV_TRACK = 0xB1
private const val VK_MEDIA_PLAY_PAUSE = 0xB3

enum class HotkeyAction { PlayPause, Previous, Next, VolumeUp, VolumeDown, ToggleDesktopLyric }

private data class Binding(val id: Int, val modifiers: Int, val vk: Int, val action: HotkeyAction, val label: String)

// 热键注册与 WM_HOTKEY 必须在同一线程，因此独占一条线程跑消息循环
class GlobalHotkeys(private val onAction: (HotkeyAction) -> Unit) {

    private val ctrlAlt = User32.MOD_CONTROL or User32.MOD_ALT or User32.MOD_NOREPEAT

    private val bindings = listOf(
        Binding(1, 0, VK_MEDIA_PLAY_PAUSE, HotkeyAction.PlayPause, "媒体键 播放/暂停"),
        Binding(2, 0, VK_MEDIA_NEXT_TRACK, HotkeyAction.Next, "媒体键 下一首"),
        Binding(3, 0, VK_MEDIA_PREV_TRACK, HotkeyAction.Previous, "媒体键 上一首"),
        Binding(4, ctrlAlt, VK_P, HotkeyAction.PlayPause, "Ctrl+Alt+P"),
        Binding(5, ctrlAlt, VK_LEFT, HotkeyAction.Previous, "Ctrl+Alt+←"),
        Binding(6, ctrlAlt, VK_RIGHT, HotkeyAction.Next, "Ctrl+Alt+→"),
        Binding(7, ctrlAlt, VK_UP, HotkeyAction.VolumeUp, "Ctrl+Alt+↑"),
        Binding(8, ctrlAlt, VK_DOWN, HotkeyAction.VolumeDown, "Ctrl+Alt+↓"),
        Binding(9, ctrlAlt, VK_L, HotkeyAction.ToggleDesktopLyric, "Ctrl+Alt+L")
    )

    @Volatile private var threadId = 0
    private var thread: Thread? = null

    fun start() {
        if (thread != null) return
        val ready = CountDownLatch(1)
        thread = Thread({ runLoop(ready) }, "global-hotkeys").apply {
            isDaemon = true
            start()
        }
        ready.await()
    }

    fun stop() {
        val id = threadId
        if (id != 0) User32.INSTANCE.PostThreadMessageW(id, User32.WM_QUIT, 0, 0)
        thread?.join(1000)
        thread = null
    }

    private fun runLoop(ready: CountDownLatch) {
        val user32 = try {
            User32.INSTANCE.also { threadId = Kernel32.INSTANCE.GetCurrentThreadId() }
        } catch (e: UnsatisfiedLinkError) {
            AppLogger.e(TAG, "user32 加载失败，全局快捷键不可用", e)
            ready.countDown()
            return
        }
        val registered = bindings.filter { binding ->
            user32.RegisterHotKey(null, binding.id, binding.modifiers, binding.vk).also { ok ->
                // 被其他程序占用时只跳过该键，不影响其余热键
                if (!ok) AppLogger.w(TAG, "热键已被占用：${binding.label}")
            }
        }
        ready.countDown()
        val msg = Msg()
        // GetMessage 收到 WM_QUIT 返回 0，出错返回 -1
        while (user32.GetMessageW(msg, null, 0, 0) > 0) {
            if (msg.message != User32.WM_HOTKEY) continue
            val action = registered.firstOrNull { it.id == msg.wParam.toInt() }?.action ?: continue
            SwingUtilities.invokeLater { onAction(action) }
        }
        registered.forEach { user32.UnregisterHotKey(null, it.id) }
        threadId = 0
    }
}
