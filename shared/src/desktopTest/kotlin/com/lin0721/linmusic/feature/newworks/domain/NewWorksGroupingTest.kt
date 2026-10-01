package com.lin0721.linmusic.feature.newworks.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class NewWorksGroupingTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun millis(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun release(id: Long, publishTime: Long, isAlbum: Boolean = false, trackCount: Int = 1) =
        NewWorksRelease(id, "t$id", "", "a", isAlbum, trackCount, publishTime)

    // 2026-10-01 是周四，本周一为 9-28，上周一为 9-21
    private val now = millis(2026, 10, 1, 12)

    @Test
    fun `以周一零点为界分组`() {
        val groups = groupByWeek(
            listOf(
                release(1, millis(2026, 9, 28)),
                release(2, millis(2026, 9, 27, 23, 59)),
                release(3, millis(2026, 9, 21)),
                release(4, millis(2026, 9, 20, 23, 59))
            ),
            now,
            zone
        )
        assertEquals(listOf(ReleasePeriod.ThisWeek, ReleasePeriod.LastWeek, ReleasePeriod.Earlier), groups.map { it.period })
        assertEquals(listOf(1L), groups[0].releases.map { it.id })
        assertEquals(listOf(2L, 3L), groups[1].releases.map { it.id })
        assertEquals(listOf(4L), groups[2].releases.map { it.id })
    }

    @Test
    fun `今天是周一时本周从当天零点算起`() {
        val monday = millis(2026, 9, 28, 0, 30)
        val groups = groupByWeek(listOf(release(1, millis(2026, 9, 28)), release(2, millis(2026, 9, 27, 22))), monday, zone)
        assertEquals(listOf(ReleasePeriod.ThisWeek, ReleasePeriod.LastWeek), groups.map { it.period })
    }

    @Test
    fun `跨年按周一边界归组`() {
        // 2027-01-01 是周五，本周一为 2026-12-28
        val newYear = millis(2027, 1, 1, 10)
        val groups = groupByWeek(
            listOf(release(1, millis(2026, 12, 28, 8)), release(2, millis(2026, 12, 27, 8))),
            newYear,
            zone
        )
        assertEquals(listOf(ReleasePeriod.ThisWeek, ReleasePeriod.LastWeek), groups.map { it.period })
    }

    @Test
    fun `无发布时间归入更早且空组不输出`() {
        val groups = groupByWeek(listOf(release(1, 0)), now, zone)
        assertEquals(listOf(ReleasePeriod.Earlier), groups.map { it.period })
        assertEquals(emptyList<NewWorksReleaseGroup>(), groupByWeek(emptyList(), now, zone))
    }

    @Test
    fun `说明文字今年省略年份且无时间不带日期`() {
        assertEquals("单曲 · 9月26日", release(1, millis(2026, 9, 26)).caption(now, zone))
        assertEquals("专辑 · 12 首 · 2025年9月26日", release(2, millis(2025, 9, 26), true, 12).caption(now, zone))
        assertEquals("专辑 · 3 首", release(3, 0, true, 3).caption(now, zone))
    }
}
