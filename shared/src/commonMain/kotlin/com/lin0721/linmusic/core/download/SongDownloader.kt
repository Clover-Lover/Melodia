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

// 批量入队结果，已下载过同等或更高音质的歌曲会被跳过
data class BatchEnqueueResult(
    val enqueuedCount: Int,
    val skippedCount: Int
)

// 批量下载入队结果提示文案
fun BatchEnqueueResult.toToastMessage(): String = when {
    enqueuedCount == 0 && skippedCount > 0 -> "$skippedCount 首歌曲均已下载，无需重复下载"
    skippedCount > 0 -> "已将 $enqueuedCount 首歌曲加入下载队列，跳过 $skippedCount 首已下载"
    else -> "已将 $enqueuedCount 首歌曲加入下载队列"
}

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
    suspend fun enqueueBatch(tracks: List<DownloadTrackInfo>, level: String, batchTag: String, batchLabel: String): BatchEnqueueResult
}
