package com.lin0721.linmusic.core.download

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import java.util.concurrent.TimeUnit

// 下载任务快照
data class DownloadWorkSnapshot(
    val workId: UUID,
    val songName: String,
    val progress: Int,
    val state: WorkInfo.State
)

// 歌曲下载任务调度管理器
class SongDownloadManager(
    private val context: Context,
    private val downloadPreferences: DownloadPreferences
) : SongDownloader {

    companion object {
        private const val TAG_DOWNLOAD = "song_download"
        private const val TAG_STREAM_CACHE = "stream_cache"
        private fun uniqueWorkName(songId: Long) = "song_download_$songId"
    }

    private val workManager get() = WorkManager.getInstance(context)

    suspend fun isDownloaded(songId: Long): Boolean = downloadPreferences.isDownloaded(songId)

    override fun enqueueSingle(track: DownloadTrackInfo, level: String): UUID {
        val request = buildRequest(track, level)
        workManager.enqueueUniqueWork(uniqueWorkName(track.songId), ExistingWorkPolicy.REPLACE, request)
        return request.id
    }

    // 边听边存入队
    fun enqueueStreamCache(track: DownloadTrackInfo, level: String): UUID {
        val request = buildRequest(track, level, batchTag = TAG_STREAM_CACHE, batchLabel = "边听边存")
        workManager.enqueueUniqueWork(uniqueWorkName(track.songId), ExistingWorkPolicy.KEEP, request)
        return request.id
    }

    // 批量下载入队
    override fun enqueueBatch(tracks: List<DownloadTrackInfo>, level: String, batchTag: String, batchLabel: String): List<UUID> =
        tracks.map { track ->
            val request = buildRequest(track, level, batchTag = batchTag, batchLabel = batchLabel)
            workManager.enqueueUniqueWork(uniqueWorkName(track.songId), ExistingWorkPolicy.REPLACE, request)
            request.id
        }

    fun cancel(songId: Long) {
        workManager.cancelUniqueWork(uniqueWorkName(songId))
    }

    fun observeBatch(batchTag: String): Flow<List<WorkInfo>> = workManager.getWorkInfosByTagFlow(batchTag)

    // 观察用户主动发起的下载任务，排除边听边存
    fun observeUserDownloads(): Flow<List<DownloadWorkSnapshot>> =
        workManager.getWorkInfosByTagFlow(TAG_DOWNLOAD).map { infos ->
            infos.filterNot { TAG_STREAM_CACHE in it.tags }
                .map { info ->
                    DownloadWorkSnapshot(
                        workId = info.id,
                        songName = info.progress.getString(SongDownloadWorker.KEY_PROGRESS_SONG_NAME) ?: "",
                        progress = info.progress.getInt(SongDownloadWorker.KEY_PROGRESS_PERCENT, 0),
                        state = info.state
                    )
                }
        }

    fun observeSingle(songId: Long): Flow<List<WorkInfo>> =
        workManager.getWorkInfosForUniqueWorkFlow(uniqueWorkName(songId))

    private fun buildRequest(
        track: DownloadTrackInfo,
        level: String,
        batchTag: String? = null,
        batchLabel: String? = null
    ) = OneTimeWorkRequestBuilder<SongDownloadWorker>()
        .setInputData(
            SongDownloadWorker.buildInputData(
                track.songId, track.songName, track.artistName, level,
                track.albumName, track.coverUrl, track.albumYear, batchTag, batchLabel
            )
        )
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
        .addTag(TAG_DOWNLOAD)
        .apply { batchTag?.let { addTag(it) } }
        .build()
}
