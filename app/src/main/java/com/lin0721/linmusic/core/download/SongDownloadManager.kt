package com.lin0721.linmusic.core.download

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.TimeUnit

// 下载管理面板中的任务状态
enum class DownloadTaskStatus { WAITING, RUNNING, SUCCEEDED, FAILED }

// 下载管理面板中的一条任务
data class DownloadTask(
    val meta: DownloadTaskMeta,
    val status: DownloadTaskStatus,
    val progress: Int,
    val failureReason: String?,
    val skipped: Boolean
) {
    val isActive: Boolean get() = status == DownloadTaskStatus.WAITING || status == DownloadTaskStatus.RUNNING
}

// 歌曲下载任务调度管理器
class SongDownloadManager(
    private val context: Context,
    private val downloadPreferences: DownloadPreferences,
    private val taskStore: DownloadTaskStore
) : SongDownloader {

    companion object {
        private const val TAG_DOWNLOAD = "song_download"
        private const val TAG_STREAM_CACHE = "stream_cache"
        private const val TAG_SONG_PREFIX = "song_id:"
        // 任务元数据先于 WorkManager 落库，清理时给刚创建的记录留出宽限
        private const val META_GRACE_MS = 60_000L
        private fun uniqueWorkName(songId: Long) = "song_download_$songId"
    }

    private val workManager get() = WorkManager.getInstance(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun isDownloaded(songId: Long): Boolean = downloadPreferences.isDownloaded(songId)

    override fun enqueueSingle(track: DownloadTrackInfo, level: String): UUID {
        val request = buildRequest(track, level)
        scope.launch {
            // 先写元数据再入队，面板不会出现缺少歌名的任务
            taskStore.add(listOf(metaFor(request.id, track, level, batchTag = null, batchLabel = null)))
            workManager.enqueueUniqueWork(uniqueWorkName(track.songId), ExistingWorkPolicy.REPLACE, request)
        }
        return request.id
    }

    // 边听边存入队
    fun enqueueStreamCache(track: DownloadTrackInfo, level: String): UUID {
        val request = buildRequest(track, level, batchTag = TAG_STREAM_CACHE, batchLabel = "边听边存")
        workManager.enqueueUniqueWork(uniqueWorkName(track.songId), ExistingWorkPolicy.KEEP, request)
        return request.id
    }

    // 批量下载入队：跳过已下载同等或更高音质的歌曲，以及已在队列中的歌曲
    override suspend fun enqueueBatch(
        tracks: List<DownloadTrackInfo>,
        level: String,
        batchTag: String,
        batchLabel: String
    ): BatchEnqueueResult {
        val distinctTracks = tracks.distinctBy { it.songId }
        val downloadedIds = downloadPreferences.findVerifiedRecords(distinctTracks.map { it.songId })
            .filter { it.satisfies(level) }
            .mapTo(HashSet()) { it.songId }
        val infos = currentWorkInfos()
        val queuedIds = infos.filterNot { it.state.isFinished }.mapNotNullTo(HashSet()) { songIdOf(it) }
        val pending = distinctTracks.filterNot { it.songId in downloadedIds || it.songId in queuedIds }

        val requests = pending.map { track -> track to buildRequest(track, level, batchTag, batchLabel) }
        taskStore.retainExisting(infos.mapTo(HashSet()) { it.id.toString() }, System.currentTimeMillis() - META_GRACE_MS)
        taskStore.add(requests.map { (track, request) -> metaFor(request.id, track, level, batchTag, batchLabel) })
        requests.forEach { (track, request) ->
            // 未带歌曲标签的旧版本任务仍由 KEEP 兜底，不会被打断
            workManager.enqueueUniqueWork(uniqueWorkName(track.songId), ExistingWorkPolicy.KEEP, request)
        }
        return BatchEnqueueResult(
            enqueuedCount = pending.size,
            skippedCount = downloadedIds.size,
            queuedCount = distinctTracks.count { it.songId in queuedIds && it.songId !in downloadedIds }
        )
    }

    fun cancel(songId: Long) {
        workManager.cancelUniqueWork(uniqueWorkName(songId))
    }

    // 观察下载管理面板的任务列表，排除边听边存与已取消的任务
    fun observeTasks(): Flow<List<DownloadTask>> =
        combine(taskStore.tasks, workManager.getWorkInfosByTagFlow(TAG_DOWNLOAD)) { metas, infos ->
            val infoById = infos.associateBy { it.id.toString() }
            metas.mapNotNull { meta ->
                val info = infoById[meta.workId] ?: return@mapNotNull null
                val status = when (info.state) {
                    WorkInfo.State.RUNNING -> DownloadTaskStatus.RUNNING
                    WorkInfo.State.SUCCEEDED -> DownloadTaskStatus.SUCCEEDED
                    WorkInfo.State.FAILED -> DownloadTaskStatus.FAILED
                    WorkInfo.State.CANCELLED -> return@mapNotNull null
                    else -> DownloadTaskStatus.WAITING
                }
                DownloadTask(
                    meta = meta,
                    status = status,
                    progress = info.progress.getInt(SongDownloadWorker.KEY_PROGRESS_PERCENT, 0),
                    failureReason = info.outputData.getString(SongDownloadWorker.KEY_REASON),
                    skipped = info.outputData.getBoolean(SongDownloadWorker.KEY_SKIPPED, false)
                )
            }
        }

    // 重新下载失败的任务，沿用原音质与所属批次
    suspend fun retry(tasks: List<DownloadTask>) {
        val requests = tasks.filter { it.status == DownloadTaskStatus.FAILED }.map { task ->
            val meta = task.meta
            meta to buildRequest(meta.toTrackInfo(), meta.level, meta.batchTag, meta.batchLabel)
        }
        if (requests.isEmpty()) return
        taskStore.add(requests.map { (meta, request) -> meta.copy(workId = request.id.toString(), createdAt = System.currentTimeMillis()) })
        requests.forEach { (meta, request) ->
            workManager.enqueueUniqueWork(uniqueWorkName(meta.songId), ExistingWorkPolicy.REPLACE, request)
        }
    }

    // 取消进行中的任务
    suspend fun cancel(tasks: List<DownloadTask>) {
        val active = tasks.filter { it.isActive }
        active.forEach { workManager.cancelWorkById(UUID.fromString(it.meta.workId)) }
        taskStore.remove(active.mapTo(HashSet()) { it.meta.workId })
    }

    // 从列表移除已结束的任务，不影响已下载的文件
    suspend fun dismiss(tasks: List<DownloadTask>) {
        taskStore.remove(tasks.filterNot { it.isActive }.mapTo(HashSet()) { it.meta.workId })
    }

    fun observeBatch(batchTag: String): Flow<List<WorkInfo>> = workManager.getWorkInfosByTagFlow(batchTag)

    fun observeSingle(songId: Long): Flow<List<WorkInfo>> =
        workManager.getWorkInfosForUniqueWorkFlow(uniqueWorkName(songId))

    private suspend fun currentWorkInfos(): List<WorkInfo> = withContext(Dispatchers.IO) {
        runCatching { workManager.getWorkInfosByTag(TAG_DOWNLOAD).get() }.getOrDefault(emptyList())
    }

    private fun songIdOf(info: WorkInfo): Long? =
        info.tags.firstOrNull { it.startsWith(TAG_SONG_PREFIX) }?.removePrefix(TAG_SONG_PREFIX)?.toLongOrNull()

    private fun metaFor(
        workId: UUID,
        track: DownloadTrackInfo,
        level: String,
        batchTag: String?,
        batchLabel: String?
    ) = DownloadTaskMeta(
        workId = workId.toString(),
        songId = track.songId,
        songName = track.songName,
        artistName = track.artistName,
        albumName = track.albumName,
        coverUrl = track.coverUrl,
        albumYear = track.albumYear,
        level = level,
        batchTag = batchTag,
        batchLabel = batchLabel,
        createdAt = System.currentTimeMillis()
    )

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
        .addTag("$TAG_SONG_PREFIX${track.songId}")
        .apply { batchTag?.let { addTag(it) } }
        .build()
}
