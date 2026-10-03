package com.lin0721.linmusic.feature.source.plugin

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.source.AudioSourceProvider
import com.lin0721.linmusic.core.source.ExternalTrack
import com.lin0721.linmusic.core.source.MusicPlatform
import com.lin0721.linmusic.core.source.SourcePreferences
import com.lin0721.linmusic.core.source.SourceResult
import kotlinx.coroutines.flow.first

private const val TAG = "LxAudioSourceProvider"

// LX 自定义插件音源 Provider 适配器
class LxAudioSourceProvider(
    private val engine: LxPluginEngine,
    private val sourcePreferences: SourcePreferences
) : AudioSourceProvider {

    override val platform: MusicPlatform = MusicPlatform.LX

    override suspend fun resolveUrl(
        songName: String,
        artists: String,
        albumName: String?,
        durationMs: Long,
        quality: String
    ): SourceResult? {
        val enabled = sourcePreferences.lxPluginEnabled.first()
        if (!enabled) return null

        val supported = engine.supportedSources
        if (supported.isEmpty()) return null

        // 优先尝试网易云对应通道，其次酷我、酷狗、QQ、咪咕
        val priorityOrder = listOf("wy", "kw", "kg", "tx", "mg")
        val trySources = priorityOrder.filter { it in supported }.ifEmpty { supported }

        for (source in trySources) {
            try {
                val url = engine.resolveMusicUrl(
                    source = source,
                    songId = "",
                    songName = songName,
                    singer = artists,
                    albumName = albumName ?: "",
                    durationMs = durationMs,
                    quality = quality
                )
                if (!url.isNullOrBlank()) {
                    AppLogger.i(TAG, "LX 插件成功通过 [$source] 解析直链: $songName")
                    return SourceResult(
                        url = url,
                        platform = platform,
                        quality = quality
                    )
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "LX 插件解析 [$source] 失败: ${e.message}")
            }
        }
        return null
    }

    override suspend fun search(keyword: String, offset: Int, limit: Int): List<ExternalTrack> {
        return emptyList()
    }
}
