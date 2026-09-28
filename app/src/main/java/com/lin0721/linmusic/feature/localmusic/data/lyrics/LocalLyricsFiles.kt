package com.lin0721.linmusic.feature.localmusic.data.lyrics

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

private const val PRIMARY_VOLUME_PATH = "/storage/emulated/0/"
private const val STORAGE_ROOT = "/storage/"
private val GBK: Charset = Charset.forName("GBK")

fun lrcNameFor(audioName: String): String {
    val dot = audioName.lastIndexOf('.')
    val base = if (dot > 0) audioName.substring(0, dot) else audioName
    return "$base.lrc"
}

// 主存储卷为 primary，SD 卡为卷 id
fun documentIdForPath(path: String): String? = when {
    path.startsWith(PRIMARY_VOLUME_PATH) -> "primary:" + path.removePrefix(PRIMARY_VOLUME_PATH)
    path.startsWith(STORAGE_ROOT) -> {
        val rest = path.removePrefix(STORAGE_ROOT)
        val volume = rest.substringBefore('/')
        if (volume.isBlank() || volume == "emulated" || !rest.contains('/')) null
        else "$volume:" + rest.substringAfter('/')
    }
    else -> null
}

// 授权树是否覆盖该文档：同卷且目录前缀匹配到路径分隔符为止，避免 Music 误匹配 Music2
fun treeCoversDocument(treeDocumentId: String, documentId: String): Boolean {
    if (documentId == treeDocumentId) return true
    // 授权整个卷时文档 id 形如 "primary:"，前缀本身已以冒号结尾
    val prefix = if (treeDocumentId.endsWith(':') || treeDocumentId.endsWith('/')) treeDocumentId else "$treeDocumentId/"
    return documentId.startsWith(prefix)
}

fun siblingLrcDocumentId(audioDocumentId: String): String {
    val slash = audioDocumentId.lastIndexOf('/')
    val colon = audioDocumentId.lastIndexOf(':')
    val nameStart = maxOf(slash, colon) + 1
    return audioDocumentId.substring(0, nameStart) + lrcNameFor(audioDocumentId.substring(nameStart))
}

// 优先 UTF-8（去 BOM），解码失败再按 GBK：老歌词文件大多是 GBK
fun decodeLyricsBytes(bytes: ByteArray): String {
    val hasBom = bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
    val content = if (hasBom) bytes.copyOfRange(3, bytes.size) else bytes
    return try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(content))
            .toString()
    } catch (e: CharacterCodingException) {
        String(content, GBK)
    }
}
