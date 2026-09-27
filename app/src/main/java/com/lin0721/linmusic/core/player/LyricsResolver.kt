package com.lin0721.linmusic.core.player

import com.lin0721.linmusic.core.localmusic.LocalMusicApi
import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.core.player.domain.LyricLine
import com.lin0721.linmusic.core.player.domain.LyricParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

// 播放页、悬浮歌词、外部歌词共用的取词入口。
// 已匹配网易的歌优先网易歌词（逐字与翻译更全），拿不到再读本地；未匹配的本地歌只读本地
class LyricsResolver(
    private val playbackRepository: PlaybackRepository,
    private val localMusicApi: LocalMusicApi,
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
        val lines = localMusicApi.readLyrics(localUri)?.let(LyricParser::parseLrc).orEmpty()
        emit(Result.success(lines))
    }
}
