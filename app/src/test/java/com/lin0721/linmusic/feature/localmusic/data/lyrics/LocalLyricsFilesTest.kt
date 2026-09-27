package com.lin0721.linmusic.feature.localmusic.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class LocalLyricsFilesTest {

    @Test
    fun `同名歌词文件名替换扩展名`() {
        assertEquals("茶太 - Winter Bells♪.lrc", lrcNameFor("茶太 - Winter Bells♪.flac"))
        assertEquals("a.b.lrc", lrcNameFor("a.b.mp3"))
        assertEquals("noext.lrc", lrcNameFor("noext"))
    }

    @Test
    fun `主存储路径换算为 primary 文档 id`() {
        assertEquals("primary:Music/Melodia/a.flac", documentIdForPath("/storage/emulated/0/Music/Melodia/a.flac"))
    }

    @Test
    fun `SD 卡路径换算为卷 id 文档 id`() {
        assertEquals("1A2B-3C4D:Music/a.mp3", documentIdForPath("/storage/1A2B-3C4D/Music/a.mp3"))
    }

    @Test
    fun `无法识别的路径返回空`() {
        assertNull(documentIdForPath("/data/user/0/a.mp3"))
        assertNull(documentIdForPath("/storage/emulated/10/a.mp3"))
    }

    @Test
    fun `授权树按目录边界匹配`() {
        assertTrue(treeCoversDocument("primary:Music", "primary:Music/a.lrc"))
        assertTrue(treeCoversDocument("primary:Music", "primary:Music/sub/a.lrc"))
        assertFalse(treeCoversDocument("primary:Music", "primary:Music2/a.lrc"))
        assertFalse(treeCoversDocument("primary:Music", "1A2B-3C4D:Music/a.lrc"))
    }

    @Test
    fun `授权整个存储卷时覆盖卷内全部文档`() {
        assertTrue(treeCoversDocument("primary:", "primary:Music/a.lrc"))
    }

    @Test
    fun `同目录同名歌词文档 id`() {
        assertEquals("primary:Music/sub/a.lrc", siblingLrcDocumentId("primary:Music/sub/a.flac"))
        assertEquals("primary:a.lrc", siblingLrcDocumentId("primary:a.flac"))
    }

    @Test
    fun `UTF-8 歌词去掉 BOM`() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "[00:01.00]你好".toByteArray(Charsets.UTF_8)
        assertEquals("[00:01.00]你好", decodeLyricsBytes(bytes))
    }

    @Test
    fun `非法 UTF-8 按 GBK 解码`() {
        val bytes = "[00:01.00]冬之钟".toByteArray(Charset.forName("GBK"))
        assertEquals("[00:01.00]冬之钟", decodeLyricsBytes(bytes))
    }
}
