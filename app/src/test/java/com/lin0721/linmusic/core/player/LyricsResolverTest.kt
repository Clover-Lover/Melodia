package com.lin0721.linmusic.core.player

import com.lin0721.linmusic.core.localmusic.LocalMusicApi
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.core.player.domain.LyricLine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsResolverTest {

    private val onlineLines = listOf(LyricLine(timeMs = 0, text = "网易歌词"))
    private val localLrc = "[00:01.00]本地歌词"

    private class FakePlaybackRepository(private val lyrics: Result<List<LyricLine>>) : PlaybackRepository {
        var lyricRequests = 0
        override fun getLyrics(songId: Long): Flow<Result<List<LyricLine>>> {
            lyricRequests++
            return flowOf(lyrics)
        }
        override fun getSongUrl(songId: Long): Flow<Result<String>> = emptyFlow()
        override fun getSimilarSongs(songId: Long): Flow<Result<List<Track>>> = emptyFlow()
        override fun getIntelligenceSongs(songId: Long, playlistId: Long): Flow<Result<List<Track>>> = emptyFlow()
        override fun reportStartPlay(songId: Long): Flow<Result<Unit>> = emptyFlow()
        override fun reportPlayEnd(songId: Long, playedSeconds: Long): Flow<Result<Unit>> = emptyFlow()
    }

    private class FakeLocalMusicApi(private val lyrics: String?) : LocalMusicApi {
        var lyricReads = 0
        override suspend fun coverUriFor(sourceUri: android.net.Uri): android.net.Uri? = null
        override suspend fun readLyrics(sourceUri: String): String? {
            lyricReads++
            return lyrics
        }
    }

    private fun resolver(repo: PlaybackRepository, local: LocalMusicApi, localUri: String?) =
        LyricsResolver(repo, local, localUriOf = { localUri })

    @Test
    fun `未匹配的本地歌只读本地歌词不请求网易`() = runTest {
        val repo = FakePlaybackRepository(Result.success(onlineLines))
        val lines = resolver(repo, FakeLocalMusicApi(localLrc), "content://a").lyricsFor(-5L).first().getOrThrow()
        assertEquals("本地歌词", lines.single().text)
        assertEquals(0, repo.lyricRequests)
    }

    @Test
    fun `已匹配的本地歌优先网易歌词`() = runTest {
        val local = FakeLocalMusicApi(localLrc)
        val lines = resolver(FakePlaybackRepository(Result.success(onlineLines)), local, "content://a").lyricsFor(7L).first().getOrThrow()
        assertEquals("网易歌词", lines.single().text)
        assertEquals(0, local.lyricReads)
    }

    @Test
    fun `网易歌词为空时回退本地歌词`() = runTest {
        val lines = resolver(FakePlaybackRepository(Result.success(emptyList())), FakeLocalMusicApi(localLrc), "content://a")
            .lyricsFor(7L).first().getOrThrow()
        assertEquals("本地歌词", lines.single().text)
    }

    @Test
    fun `网易歌词请求失败时回退本地歌词`() = runTest {
        val lines = resolver(FakePlaybackRepository(Result.failure(RuntimeException())), FakeLocalMusicApi(localLrc), "content://a")
            .lyricsFor(7L).first().getOrThrow()
        assertEquals("本地歌词", lines.single().text)
    }

    @Test
    fun `在线歌曲直接透传网易结果`() = runTest {
        val failure = Result.failure<List<LyricLine>>(RuntimeException("网络错误"))
        val result = resolver(FakePlaybackRepository(failure), FakeLocalMusicApi(null), null).lyricsFor(7L).first()
        assertTrue(result.isFailure)
    }

    @Test
    fun `没有本地文件的占位 id 返回空歌词且不请求网易`() = runTest {
        val repo = FakePlaybackRepository(Result.success(onlineLines))
        val lines = resolver(repo, FakeLocalMusicApi(localLrc), null).lyricsFor(-5L).first().getOrThrow()
        assertTrue(lines.isEmpty())
        assertEquals(0, repo.lyricRequests)
    }

    @Test
    fun `本地找不到歌词时返回空列表`() = runTest {
        val lines = resolver(FakePlaybackRepository(Result.success(emptyList())), FakeLocalMusicApi(null), "content://a")
            .lyricsFor(-5L).first().getOrThrow()
        assertTrue(lines.isEmpty())
    }
}
