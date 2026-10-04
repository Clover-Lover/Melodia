package com.lin0721.linmusic.core.download

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lin0721.linmusic.core.log.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val TAG = "DownloadTaskStore"

private val Context.downloadTaskDataStore by preferencesDataStore(name = "download_tasks")

// 下载任务元数据：WorkInfo 不携带入参，歌名、封面、歌单等展示信息在入队时单独保存
@Serializable
data class DownloadTaskMeta(
    val workId: String,
    val songId: Long,
    val songName: String,
    val artistName: String,
    val albumName: String = "",
    val coverUrl: String? = null,
    val albumYear: Int = 0,
    val level: String,
    val batchTag: String? = null,
    val batchLabel: String? = null,
    val createdAt: Long
) {
    fun toTrackInfo() = DownloadTrackInfo(songId, songName, artistName, albumName, coverUrl, albumYear)
}

// 用户主动发起的下载任务列表持久化，供下载管理面板展示与重试
class DownloadTaskStore(private val context: Context) {

    companion object {
        private val KEY_TASKS = stringPreferencesKey("download_tasks")
        private val json = Json { ignoreUnknownKeys = true }
    }

    val tasks: Flow<List<DownloadTaskMeta>> = context.downloadTaskDataStore.data.map { prefs ->
        decode(prefs[KEY_TASKS])
    }

    // 追加任务；同一首歌只保留最新一条，避免重试或重新下载后列表出现重复
    suspend fun add(metas: List<DownloadTaskMeta>) {
        if (metas.isEmpty()) return
        val songIds = metas.mapTo(HashSet()) { it.songId }
        update { list -> list.filterNot { it.songId in songIds } + metas }
    }

    suspend fun remove(workIds: Set<String>) {
        if (workIds.isEmpty()) return
        update { list -> list.filterNot { it.workId in workIds } }
    }

    // 清理 WorkManager 已不存在的任务；刚入队的记录可能还未落库，按创建时间留出宽限
    suspend fun retainExisting(existingWorkIds: Set<String>, graceBefore: Long) {
        update { list -> list.filter { it.workId in existingWorkIds || it.createdAt >= graceBefore } }
    }

    private suspend fun update(transform: (List<DownloadTaskMeta>) -> List<DownloadTaskMeta>) {
        context.downloadTaskDataStore.edit { prefs ->
            prefs[KEY_TASKS] = json.encodeToString(transform(decode(prefs[KEY_TASKS])))
        }
    }

    private fun decode(raw: String?): List<DownloadTaskMeta> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<DownloadTaskMeta>>(raw) }
            .onFailure { AppLogger.w(TAG, "下载任务反序列化失败", it) }
            .getOrDefault(emptyList())
    }
}
