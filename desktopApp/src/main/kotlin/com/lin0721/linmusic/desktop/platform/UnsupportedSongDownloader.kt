package com.lin0721.linmusic.desktop.platform

import com.lin0721.linmusic.core.download.DownloadTrackInfo
import com.lin0721.linmusic.core.download.SongDownloader
import com.lin0721.linmusic.core.log.AppLogger
import java.util.UUID

private const val TAG = "SongDownloader"

// 桌面第一版不做下载，入口已在界面隐藏；兜底只记日志不抛异常
class UnsupportedSongDownloader : SongDownloader {
    override fun enqueueSingle(track: DownloadTrackInfo, level: String): UUID {
        AppLogger.w(TAG, "桌面端暂不支持下载：${track.songId}")
        return UUID.randomUUID()
    }

    override fun enqueueBatch(
        tracks: List<DownloadTrackInfo>,
        level: String,
        batchTag: String,
        batchLabel: String
    ): List<UUID> {
        AppLogger.w(TAG, "桌面端暂不支持批量下载：${tracks.size} 首")
        return tracks.map { UUID.randomUUID() }
    }
}
