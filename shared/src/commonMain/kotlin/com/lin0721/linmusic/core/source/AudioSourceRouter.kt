package com.lin0721.linmusic.core.source

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.network.AppError
import com.lin0721.linmusic.core.network.NetworkStateProvider
import com.lin0721.linmusic.core.player.PlaySource
import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.core.player.data.SongPlaybackInfo
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

private const val TAG = "AudioSourceRouter"

// PlaybackRepository 装饰器，在官方接口失败或仅为试听片段时自动通过 UNM 服务换源
class AudioSourceRouter(
    private val delegate: PlaybackRepository,
    private val unmApiClient: UnmApiClient,
    private val sourcePreferences: SourcePreferences,
    private val settingsPreferences: SettingsPreferences,
    private val networkStateProvider: NetworkStateProvider,
    private val providers: List<AudioSourceProvider> = emptyList()
) : PlaybackRepository {

    override fun getSongPlaybackInfo(songId: Long): Flow<Result<SongPlaybackInfo>> = flow {
        val fallbackEnabled = sourcePreferences.fallbackEnabled.first()

        // 1. 先尝试官方接口获取直链
        val officialInfoResult = delegate.getSongPlaybackInfo(songId).first()
        val officialInfo = officialInfoResult.getOrNull()

        // 官方返回完整音频且非试听时，直接使用官方链接
        if (officialInfo != null && !officialInfo.isFreeTrial && officialInfo.url.isNotBlank()) {
            emit(Result.success(officialInfo))
            return@flow
        }

        // 官方失败或仅为试听，检查是否启用换源
        if (!fallbackEnabled) {
            if (officialInfo != null && officialInfo.url.isNotBlank()) {
                AppLogger.d(TAG, "换源未启用，返回官方试听链接 songId=$songId")
                emit(Result.success(officialInfo))
            } else {
                AppLogger.d(TAG, "换源未启用，返回官方失败结果 songId=$songId")
                emit(Result.failure(officialInfoResult.exceptionOrNull() ?: AppError.BizError(-1, "无法获取播放链接")))
            }
            return@flow
        }

        // 2. 需换源且总开关开启
        AppLogger.i(TAG, "开始换源流程: songId=$songId (官方为试听: ${officialInfo?.isFreeTrial})")

        val enabledModules = sourcePreferences.unmEnabledModules.first()
        val moduleOrder = sourcePreferences.unmModuleOrder.first()

        var matchedUrl: String? = null
        var matchedSource: String? = null

        // 阶段一：本地原生直连模块轮询（极速、去中心化）
        val candidateModules = moduleOrder
            .filter { it in enabledModules }
            .mapNotNull { UnmModule.fromKey(it) }

        for (module in candidateModules) {
            AppLogger.d(TAG, "尝试本地直连音源: ${module.displayName} songId=$songId")
            val url = NativeSourceFetcher.fetchUrl(module, songId)
            if (!url.isNullOrBlank()) {
                AppLogger.i(TAG, "本地直连音源 ${module.displayName} 解析成功: songId=$songId")
                matchedUrl = url
                matchedSource = module.displayName
                break
            }
        }

        // 阶段二：若本地直连未命中，且启用了远程 UNM 兜底服务并且配置了服务器地址
        val remoteFallbackEnabled = sourcePreferences.unmRemoteFallbackEnabled.first()
        val serverUrl = sourcePreferences.unmServerUrl.first().trim()

        if (matchedUrl == null && remoteFallbackEnabled && serverUrl.isNotBlank()) {
            val autoMatch = sourcePreferences.unmAutoMatch.first()
            AppLogger.d(TAG, "本地音源未命中，尝试远程 UNM 服务兜底: $serverUrl songId=$songId")

            val remoteResult = if (autoMatch) {
                unmApiClient.matchSong(serverUrl, songId, null)
            } else {
                var res: UnmMatchResult? = null
                for (module in candidateModules) {
                    res = unmApiClient.matchSong(serverUrl, songId, module.key)
                    if (res != null && res.url.isNotBlank()) break
                }
                res
            }

            if (remoteResult != null && remoteResult.url.isNotBlank()) {
                AppLogger.i(TAG, "远程 UNM 服务兜底成功: songId=$songId 来源=${remoteResult.source}")
                matchedUrl = remoteResult.url
                matchedSource = "远程UNM (${remoteResult.source ?: "auto"})"
            }
        }

        // 3. 结果判定
        if (matchedUrl != null && matchedUrl.isNotBlank()) {
            AppLogger.i(TAG, "换源成功: songId=$songId 最终来源=$matchedSource")
            emit(Result.success(SongPlaybackInfo(url = matchedUrl, isFreeTrial = false)))
        } else {
            // UNM 服务均未命中，检查是否有社区扩展插件
            var lxResult: SourceResult? = null
            if (providers.isNotEmpty()) {
                val track = delegate.getSongDetail(songId).first().getOrNull()
                if (track != null) {
                    val quality = if (networkStateProvider.isWifiConnected()) {
                        settingsPreferences.wifiQuality.first()
                    } else {
                        settingsPreferences.mobileQuality.first()
                    }
                    for (p in providers) {
                        try {
                            val r = p.resolveUrl(track.name, track.ar.joinToString("/") { it.name }, track.al.name, track.dt, quality)
                            if (r != null && r.url.isNotBlank()) {
                                lxResult = r
                                break
                            }
                        } catch (e: Exception) {
                            AppLogger.w(TAG, "${p.platform.displayName} 解析失败: ${e.message}")
                        }
                    }
                }
            }

            if (lxResult != null && lxResult.url.isNotBlank()) {
                AppLogger.i(TAG, "扩展插件换源成功: songId=$songId 来源=${lxResult.platform.displayName}")
                emit(Result.success(SongPlaybackInfo(url = lxResult.url, isFreeTrial = false)))
            } else {
                AppLogger.w(TAG, "所有换源均未命中: songId=$songId")
                if (officialInfo != null && officialInfo.url.isNotBlank()) {
                    AppLogger.i(TAG, "兜底返回官方试听链接: songId=$songId")
                    emit(Result.success(officialInfo))
                } else {
                    emit(Result.failure(officialInfoResult.exceptionOrNull() ?: AppError.BizError(-1, "换源未能获取到可用播放链接")))
                }
            }
        }
    }.catch { e ->
        AppLogger.e(TAG, "换源流程异常 songId=$songId", e)
        emit(Result.failure(e))
    }

    override fun getSongUrl(songId: Long): Flow<Result<String>> =
        getSongPlaybackInfo(songId).map { result -> result.map { it.url } }

    // 委托其余方法
    override fun getLyrics(songId: Long) = delegate.getLyrics(songId)
    override fun getRawLyrics(songId: Long) = delegate.getRawLyrics(songId)
    override fun getSongDetail(songId: Long) = delegate.getSongDetail(songId)
    override fun getSimilarSongs(songId: Long) = delegate.getSimilarSongs(songId)
    override fun getIntelligenceSongs(songId: Long, playlistId: Long) = delegate.getIntelligenceSongs(songId, playlistId)
    override fun reportStartPlay(songId: Long, source: PlaySource?) = delegate.reportStartPlay(songId, source)
    override fun reportPlayEnd(songId: Long, playedSeconds: Long, source: PlaySource?) = delegate.reportPlayEnd(songId, playedSeconds, source)
    override val playlistRecorded: SharedFlow<Long> get() = delegate.playlistRecorded
}
