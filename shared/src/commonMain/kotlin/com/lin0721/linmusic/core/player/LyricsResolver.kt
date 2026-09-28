package com.lin0721.linmusic.core.player

import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.player.domain.LyricParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

// 已匹配网易的歌优先网易歌词（逐字与翻译更全），拿不到再读本地
class LyricsResolver(
    private val playbackRepository: PlaybackRepository,
    // 按来源 Uri 读取本地歌词原文，平台无本地音乐时返回 null
    private val readLocalLyrics: suspend (sourceUri: String) -> String?,
    private val localUriOf: (songId: Long) -> String?
) {

    fun lyricsFor(songId: Long): Flow<Result<List<LyricLine>>> = flow {
        val localUri = localUriOf(songId)
        if (localUri == null) {
            if (songId > 0) emitAll(playbackRepository.getLyrics(songId)) else emit(Result.success(emptyList()))
            return@flow
        }
        if (songId > 0) {
            val online = playbackRepository.getLyrics(songId).first()
            if (online.getOrNull().orEmpty().isNotEmpty()) {
                emit(online)
                return@flow
            }
        }
        val lines = readLocalLyrics(localUri)?.let(LyricParser::parseLrc).orEmpty()
        emit(Result.success(lines))
    }
}
