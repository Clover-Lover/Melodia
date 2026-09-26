package com.lin0721.linmusic.feature.localmusic.data

import com.lin0721.linmusic.feature.localmusic.data.db.LocalTrackEntity
import com.lin0721.linmusic.feature.localmusic.domain.LocalTrackSource

data class LocalLibrarySyncDiff(
    val upserts: List<LocalTrackEntity>,
    val deleteUris: Set<String>
)

// 库内已有数据与本次 MediaStore 扫描结果比对：只写入变化的条目；
// MediaStore 来源的条目扫描不到即视为已删除，导入条目由 isImportedAlive 判断文件是否还在
fun computeLocalLibrarySyncDiff(
    existing: List<LocalTrackEntity>,
    scanned: List<LocalTrackEntity>,
    isImportedAlive: (LocalTrackEntity) -> Boolean
): LocalLibrarySyncDiff {
    val existingByUri = existing.associateBy { it.uri }
    val scannedUris = scanned.mapTo(HashSet()) { it.uri }

    val upserts = scanned.filter { existingByUri[it.uri] != it }
    val deleteUris = existing.asSequence()
        .filter { it.uri !in scannedUris }
        .filter { it.source != LocalTrackSource.IMPORTED.name || !isImportedAlive(it) }
        .mapTo(HashSet()) { it.uri }

    return LocalLibrarySyncDiff(upserts, deleteUris)
}
