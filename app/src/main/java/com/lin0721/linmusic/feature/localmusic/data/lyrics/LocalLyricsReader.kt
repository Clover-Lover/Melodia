package com.lin0721.linmusic.feature.localmusic.data.lyrics

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.kyant.taglib.TagLib
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.feature.localmusic.data.db.LocalTrackDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "LocalLyricsReader"
private const val PRIMARY_VOLUME_PATH = "/storage/emulated/0/"

// 歌词文件远小于此值，防止同名的超大文件被整块读进内存
private const val MAX_LRC_BYTES = 1L shl 20

// 读取本地音频的歌词原文：内嵌歌词 → 同名 .lrc；只返回原文，解析交给调用方
class LocalLyricsReader(
    private val context: Context,
    private val dao: LocalTrackDao
) {

    private val resolver: ContentResolver get() = context.contentResolver

    suspend fun read(sourceUri: String): String? = withContext(Dispatchers.IO) {
        val uri = Uri.parse(sourceUri)
        val path = runCatching { dao.getByUri(sourceUri)?.path }.getOrNull()
        readEmbedded(uri)
            ?: path?.let { readSidecarByPath(it) }
            ?: path?.let { readSidecarFromMediaStore(it) }
            ?: readSidecarFromAuthorizedTree(uri, path)
    }

    private fun readEmbedded(uri: Uri): String? = runCatching {
        resolver.openFileDescriptor(uri, "r")?.use { pfd ->
            TagLib.getMetadata(pfd.dup().detachFd(), readPictures = false)
                ?.propertyMap
                ?.get("LYRICS")
                ?.firstOrNull { it.isNotBlank() }
        }
    }.onFailure { AppLogger.d(TAG, "内嵌歌词读取失败 uri=$uri", it) }.getOrNull()

    // Melodia 自己下载时在同目录写的 .lrc，以及旧系统上有存储权限时能直接读到的文件
    private fun readSidecarByPath(audioPath: String): String? = runCatching {
        val file = File(File(audioPath).parentFile, lrcNameFor(File(audioPath).name))
        if (file.isFile && file.canRead() && file.length() in 1..MAX_LRC_BYTES) decodeLyricsBytes(file.readBytes()) else null
    }.getOrNull()

    // Android 10+ 通过 MediaStore 写入的 .lrc 归本应用所有，可按相对路径查到
    private fun readSidecarFromMediaStore(audioPath: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !audioPath.startsWith(PRIMARY_VOLUME_PATH)) return null
        val relativePath = audioPath.removePrefix(PRIMARY_VOLUME_PATH).substringBeforeLast('/', "") + "/"
        val lrcName = lrcNameFor(audioPath.substringAfterLast('/'))
        return runCatching {
            val collection = MediaStore.Files.getContentUri("external")
            resolver.query(
                collection,
                arrayOf(MediaStore.Files.FileColumns._ID),
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ? AND ${MediaStore.Files.FileColumns.RELATIVE_PATH} = ?",
                arrayOf(lrcName, relativePath),
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                readDocument(Uri.withAppendedPath(collection, cursor.getLong(0).toString()))
            }
        }.getOrNull()
    }

    // 在扫描设置里授权过的文件夹内找同名 .lrc；导入条目本身就是文档 uri，MediaStore 条目按路径换算文档 id
    private fun readSidecarFromAuthorizedTree(audioUri: Uri, audioPath: String?): String? {
        val audioDocumentId = when {
            DocumentsContract.isDocumentUri(context, audioUri) -> runCatching { DocumentsContract.getDocumentId(audioUri) }.getOrNull()
            audioPath != null -> documentIdForPath(audioPath)
            else -> null
        } ?: return null
        val lrcDocumentId = siblingLrcDocumentId(audioDocumentId)

        return resolver.persistedUriPermissions
            .asSequence()
            .filter { it.isReadPermission && DocumentsContract.isTreeUri(it.uri) }
            .map { it.uri }
            .filter { tree ->
                val treeId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull()
                treeId != null && treeCoversDocument(treeId, lrcDocumentId)
            }
            .firstNotNullOfOrNull { tree ->
                readDocument(DocumentsContract.buildDocumentUriUsingTree(tree, lrcDocumentId))
            }
    }

    private fun readDocument(uri: Uri): String? = runCatching {
        resolver.openInputStream(uri)?.use { input ->
            val bytes = input.readNBytesCompat(MAX_LRC_BYTES.toInt())
            if (bytes.isEmpty()) null else decodeLyricsBytes(bytes)
        }
    }.getOrNull()
}

// InputStream.readNBytes 需要 API 33，这里自己按上限读
private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val buffer = java.io.ByteArrayOutputStream()
    val chunk = ByteArray(8 * 1024)
    var total = 0
    while (total < limit) {
        val read = read(chunk, 0, minOf(chunk.size, limit - total))
        if (read < 0) break
        buffer.write(chunk, 0, read)
        total += read
    }
    return buffer.toByteArray()
}
