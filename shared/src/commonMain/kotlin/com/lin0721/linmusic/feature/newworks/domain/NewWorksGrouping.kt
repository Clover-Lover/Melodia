package com.lin0721.linmusic.feature.newworks.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

enum class ReleasePeriod(val title: String) {
    ThisWeek("本周"),
    LastWeek("上周"),
    Earlier("更早")
}

data class NewWorksReleaseGroup(
    val period: ReleasePeriod,
    val releases: List<NewWorksRelease>
)

// 以周一 00:00 为界分组，组内保持原顺序；空组不输出。无发布时间的条目归入「更早」
fun groupByWeek(releases: List<NewWorksRelease>, nowMillis: Long, zone: ZoneId): List<NewWorksReleaseGroup> {
    val thisMonday = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val thisWeekStart = thisMonday.atStartOfDay(zone).toInstant().toEpochMilli()
    val lastWeekStart = thisMonday.minusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli()

    val byPeriod = releases.groupBy { release ->
        when {
            release.publishTime <= 0 -> ReleasePeriod.Earlier
            release.publishTime >= thisWeekStart -> ReleasePeriod.ThisWeek
            release.publishTime >= lastWeekStart -> ReleasePeriod.LastWeek
            else -> ReleasePeriod.Earlier
        }
    }
    return ReleasePeriod.entries.mapNotNull { period ->
        byPeriod[period]?.takeIf { it.isNotEmpty() }?.let { NewWorksReleaseGroup(period, it) }
    }
}

// 卡片第三行：类型 · 曲目数 · 日期；今年的日期省略年份，无发布时间时不带日期
fun NewWorksRelease.caption(nowMillis: Long, zone: ZoneId): String {
    val type = if (isAlbum) "专辑 · $trackCount 首" else "单曲"
    if (publishTime <= 0) return type
    val date = Instant.ofEpochMilli(publishTime).atZone(zone).toLocalDate()
    val thisYear = Instant.ofEpochMilli(nowMillis).atZone(zone).year
    val dateText = if (date.year == thisYear) {
        "${date.monthValue}月${date.dayOfMonth}日"
    } else {
        "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
    }
    return "$type · $dateText"
}
