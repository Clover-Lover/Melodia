package com.lin0721.linmusic.desktop.platform.win

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.win32.W32APIOptions

// 仅映射用到的几个 user32 函数；结构体按 64 位 Windows 布局
@Suppress("FunctionName")
internal interface User32 : Library {
    fun RegisterHotKey(hWnd: Pointer?, id: Int, fsModifiers: Int, vk: Int): Boolean
    fun UnregisterHotKey(hWnd: Pointer?, id: Int): Boolean
    fun GetMessageW(msg: Msg, hWnd: Pointer?, wMsgFilterMin: Int, wMsgFilterMax: Int): Int
    fun PostThreadMessageW(idThread: Int, msg: Int, wParam: Long, lParam: Long): Boolean
    fun GetWindowLongPtrW(hWnd: Pointer, nIndex: Int): Long
    fun SetWindowLongPtrW(hWnd: Pointer, nIndex: Int, dwNewLong: Long): Long

    companion object {
        val INSTANCE: User32 by lazy { Native.load("user32", User32::class.java, W32APIOptions.DEFAULT_OPTIONS) }

        const val WM_QUIT = 0x0012
        const val WM_HOTKEY = 0x0312
        const val WM_APP = 0x8000

        const val MOD_ALT = 0x0001
        const val MOD_CONTROL = 0x0002
        const val MOD_SHIFT = 0x0004
        const val MOD_NOREPEAT = 0x4000

        const val GWL_EXSTYLE = -20
        const val WS_EX_TRANSPARENT = 0x00000020L
        const val WS_EX_LAYERED = 0x00080000L
    }
}

@Suppress("FunctionName")
internal interface Kernel32 : Library {
    fun GetCurrentThreadId(): Int

    companion object {
        val INSTANCE: Kernel32 by lazy { Native.load("kernel32", Kernel32::class.java, W32APIOptions.DEFAULT_OPTIONS) }
    }
}

@Structure.FieldOrder("hwnd", "message", "wParam", "lParam", "time", "ptX", "ptY", "lPrivate")
internal class Msg : Structure() {
    @JvmField var hwnd: Pointer? = null
    @JvmField var message: Int = 0
    @JvmField var wParam: Long = 0
    @JvmField var lParam: Long = 0
    @JvmField var time: Int = 0
    @JvmField var ptX: Int = 0
    @JvmField var ptY: Int = 0
    @JvmField var lPrivate: Int = 0
}
