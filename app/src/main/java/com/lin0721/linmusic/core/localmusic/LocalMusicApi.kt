package com.lin0721.linmusic.core.localmusic

import android.net.Uri

// 本地音乐对外唯一入口，播放器等 core 组件只依赖此接口，实现在 feature/localmusic
interface LocalMusicApi {

    // 提取音频内嵌封面并缓存，返回可供展示的 uri；无内嵌封面时为 null
    suspend fun coverUriFor(sourceUri: Uri): Uri?
}
