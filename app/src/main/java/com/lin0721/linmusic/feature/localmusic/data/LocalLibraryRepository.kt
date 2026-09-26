package com.lin0721.linmusic.feature.localmusic.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.core.content.ContextCompat
import com.lin0721.linmusic.core.download.DownloadPreferences
import com.lin0721.linmusic.core.download.isDefaultDownloadDirectoryUri
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.feature.localmusic.data.db.LocalTrackDao
import com.lin0721.linmusic.feature.localmusic.data.db.LocalTrackEntity
import com.lin0721.linmusic.feature.localmusic.data.db.toDomain
import com.lin0721.linmusic.feature.localmusic.data.legacy.LegacyImportedMusicStore
import com.lin0721.linmusic.feature.localmusic.data.scan.ImportResult
import com.lin0721.linmusic.feature.localmusic.data.scan.LocalMusicImporter
import com.lin0721.linmusic.feature.localmusic.data.scan.MediaStoreScanner
import com.lin0721.linmusic.feature.localmusic.domain.LocalTrack
import com.lin0721.linmusic.feature.localmusic.domain.LocalTrackSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val TAG = "LocalLibraryRepository"

// 本地曲库：Room 为唯一数据源，sync 把 MediaStore 与导入文件的变化增量写回
class LocalLibraryRepository(
    private val context: Context,
    private val dao: LocalTrackDao,
    private val scanner: MediaStoreScanner,
    private val importer: LocalMusicImporter,
    private val downloadPreferences: DownloadPreferences,
    private val legacyImportedStore: LegacyImportedMusicStore
) {

    private val syncMutex = Mutex()

    val tracks: Flow<List<LocalTrack>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun requiredPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, requiredPermission()) == PackageManager.PERMISSION_GRANTED

    fun availableStorageBytes(): Long = runCatching {
        val stat = StatFs(Environment.getExternalStorageDirectory().path)
        stat.availableBlocksLong * stat.blockSizeLong
    }.getOrDefault(0L)

    suspend fun sync() = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            migrateLegacyImports()
            if (!hasPermission()) return@withLock
            val rawScanned = scanner.scan() ?: return@withLock

            val downloadRecords = downloadPreferences.records.first()
                .filter { isDefaultDownloadDirectoryUri(it.mediaStoreUri) }
                .associateBy { it.mediaStoreUri }
            val scanned = rawScanned.map { entity ->
                val record = downloadRecords[entity.uri] ?: return@map entity
                entity.copy(
                    songId = record.songId,
                    title = record.songName.takeIf { it.isNotBlank() } ?: entity.title,
                    artist = record.artistName.takeIf { it.isNotBlank() } ?: entity.artist,
                    source = LocalTrackSource.MELODIA_DOWNLOAD.name
                )
            }

            val diff = computeLocalLibrarySyncDiff(
                existing = dao.getAll(),
                scanned = scanned,
                isImportedAlive = { isReadable(Uri.parse(it.uri)) }
            )
            dao.applySync(diff.upserts, diff.deleteUris)
        }
    }

    suspend fun importFiles(uris: List<Uri>): ImportResult = withContext(Dispatchers.IO) {
        val distinct = uris.distinct()
        if (distinct.isEmpty()) return@withContext ImportResult(0, 0, 0)
        val knownUris = dao.getAllUris().toHashSet()
        val parsed = importer.parseFiles(distinct, knownUris)
        val added = dao.insertIgnoringExisting(parsed).count { it != -1L }
        ImportResult(addedCount = added, skippedCount = distinct.size - added, totalFound = distinct.size)
    }

    suspend fun importFolder(treeUri: Uri): ImportResult = importFiles(importer.collectFolder(treeUri))

    // 导入条目只移出曲库并释放授权，不删文件；其余条目删除文件本身
    suspend fun delete(track: LocalTrack): Boolean = withContext(Dispatchers.IO) {
        val uriString = track.uri.toString()
        if (track.source == LocalTrackSource.IMPORTED) {
            dao.deleteByUris(listOf(uriString))
            runCatching {
                context.contentResolver.releasePersistableUriPermission(track.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            return@withContext true
        }

        val deleted = runCatching { context.contentResolver.delete(track.uri, null, null) > 0 }
            .onFailure { AppLogger.w(TAG, "删除本地文件失败 uri=$uriString", it) }
            .getOrDefault(false)
        if (deleted) {
            dao.deleteByUris(listOf(uriString))
            track.songId?.let { downloadPreferences.removeRecord(it) }
        }
        deleted
    }

    // 旧版导入记录迁移：写入成功后才清空旧数据，失败保留下次重试
    private suspend fun migrateLegacyImports() {
        val legacy = legacyImportedStore.readAll()
        if (legacy.isEmpty()) return
        runCatching {
            dao.insertIgnoringExisting(
                legacy.map { record ->
                    LocalTrackEntity(
                        uri = record.uriString,
                        mediaStoreId = null,
                        songId = null,
                        title = record.title,
                        artist = record.artist,
                        album = record.album,
                        durationMs = record.durationMs,
                        sizeBytes = record.sizeBytes,
                        path = null,
                        dateAddedMs = record.dateAddedMs,
                        dateModifiedMs = record.dateAddedMs,
                        source = LocalTrackSource.IMPORTED.name
                    )
                }
            )
            legacyImportedStore.clear()
        }.onFailure { AppLogger.e(TAG, "旧版导入记录迁移失败", it) }
    }

    private fun isReadable(uri: Uri): Boolean = runCatching {
        context.contentResolver.openInputStream(uri)?.use { true } ?: false
    }.getOrDefault(false)
}
