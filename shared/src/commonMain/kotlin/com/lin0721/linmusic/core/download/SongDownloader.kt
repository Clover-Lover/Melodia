package com.lin0721.linmusic.core.download

import java.util.UUID

// 待下载歌曲信息
data class DownloadTrackInfo(
    val songId: Long,
    val songName: String,
    val artistName: String,
    val albumName: String = "",
    val coverUrl: String? = null,
    val albumYear: Int = 0
)

// 时间戳转年份
fun yearFromEpochMillis(epochMillis: Long): Int {
    if (epochMillis <= 0) return 0
    return runCatching {
        java.time.Instant.ofEpochMilli(epochMillis).atZone(java.time.ZoneId.systemDefault()).year
    }.getOrDefault(0)
}

// 歌曲下载入队的跨平台契约
interface SongDownloader {
    fun enqueueSingle(track: DownloadTrackInfo, level: String): UUID
    fun enqueueBatch(tracks: List<DownloadTrackInfo>, level: String, batchTag: String, batchLabel: String): List<UUID>
}
