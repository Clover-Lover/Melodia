package com.lin0721.linmusic.core.player.domain

// YRC(逐字)/LRC(逐行) 歌词文本解析为 [LyricLine] 列表的纯函数工具
object LyricParser {

    private val yrcLineRegex = Regex("""^\[(\d+),(\d+)](.*)$""")
    private val yrcWordRegex = Regex("""\((\d+),(\d+),\d+\)([^(\n]+)""")

    fun parseYrc(yrcText: String): List<LyricLine> {
        return yrcText.lines().mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@mapNotNull null

            yrcLineRegex.find(trimmed)?.let { match ->
                val lineStartTime = match.groupValues[1].toLongOrNull() ?: return@let null
                val lineDuration = match.groupValues[2].toLongOrNull() ?: return@let null
                val wordsContent = match.groupValues[3]

                val wordsList = mutableListOf<WordInfo>()
                val fullTextBuilder = StringBuilder()

                yrcWordRegex.findAll(wordsContent).forEach { wordMatch ->
                    val absoluteTime = wordMatch.groupValues[1].toLongOrNull() ?: 0L
                    val startOffset = absoluteTime - lineStartTime // 计算相对于行开始时间的偏移量
                    val duration = wordMatch.groupValues[2].toLongOrNull() ?: 0L
                    val wordText = wordMatch.groupValues[3]

                    wordsList.add(WordInfo(wordText, startOffset, duration))
                    fullTextBuilder.append(wordText)
                }

                LyricLine(
                    timeMs = lineStartTime,
                    durationMs = lineDuration,
                    text = fullTextBuilder.toString(),
                    words = wordsList
                )
            }
        }.sortedBy { it.timeMs }
    }

    private val lrcTimeTag = Regex("""\[(\d{2,}):(\d{2})[.:](\d{2,3})]""")
    private val wordTimeTag = Regex("""<(\d{2,}):(\d{2})[.:](\d{2,3})>""")

    // [mm:ss.xx] 与 <mm:ss.xx> 的分组结构一致，共用换算
    private fun timeTagMs(match: MatchResult): Long? {
        val min = match.groupValues[1].toLongOrNull() ?: return null
        val sec = match.groupValues[2].toLongOrNull() ?: return null
        val msRaw = match.groupValues[3]
        val ms = msRaw.toLongOrNull() ?: return null
        return min * 60_000 + sec * 1000 + if (msRaw.length == 2) ms * 10 else ms
    }

    // 拆出行首连续的 [mm:ss.xx] 标签与其后的正文
    private fun splitLineTimes(rawLine: String): Pair<List<Long>, String> {
        var rest = rawLine.trim()
        val times = mutableListOf<Long>()
        while (true) {
            val match = lrcTimeTag.matchAt(rest, 0) ?: break
            times += timeTagMs(match) ?: break
            rest = rest.substring(match.range.last + 1)
        }
        return times to rest.trim()
    }

    // 本地 .lrc 常把重复段落写成一行多个时间标签，如 [00:12.00][01:30.00]副歌，每个标签各展开成一行
    fun parseLrc(lrcText: String): List<LyricLine> {
        return lrcText.lines().flatMap { rawLine ->
            val (times, text) = splitLineTimes(rawLine)
            if (times.isEmpty() || text.isEmpty()) emptyList() else times.map { LyricLine(timeMs = it, text = text) }
        }.sortedBy { it.timeMs }
    }

    // 本地歌词：支持增强型 LRC 逐字标签 <mm:ss.xx>，且同时间戳的第二行视为上一行的译文
    fun parseLocal(text: String): List<LyricLine> {
        val parsed = text.lines().flatMap(::parseLocalRow).sortedBy { it.timeMs }
        val merged = ArrayList<LyricLine>(parsed.size)
        for (line in parsed) {
            val prev = merged.lastOrNull()
            if (prev != null && prev.timeMs == line.timeMs && prev.translation == null) {
                merged[merged.lastIndex] = prev.copy(translation = line.text)
            } else {
                merged += line
            }
        }
        return merged
    }

    private fun parseLocalRow(rawLine: String): List<LyricLine> {
        val (times, body) = splitLineTimes(rawLine)
        if (times.isEmpty() || body.isEmpty()) return emptyList()

        val tags = wordTimeTag.findAll(body).toList()
        val words = if (tags.isEmpty()) emptyList() else parseWords(body, tags, times.first())
        if (words.isEmpty()) {
            val plain = body.replace(wordTimeTag, "").trim()
            return if (plain.isEmpty()) emptyList() else times.map { LyricLine(timeMs = it, text = plain) }
        }

        val text = words.joinToString("") { it.text }.trim()
        if (text.isEmpty()) return emptyList()
        val duration = words.last().let { it.startOffsetMs + it.durationMs }
        return times.map { LyricLine(timeMs = it, durationMs = duration, text = text, words = words) }
    }

    // <起始>词<结束/下一词起始>：词的时长取到下一个标签的间隔，仅含空白的片段并入前一个词
    private fun parseWords(body: String, tags: List<MatchResult>, lineStartMs: Long): List<WordInfo> {
        val words = mutableListOf<WordInfo>()
        val leading = body.substring(0, tags[0].range.first)
        if (leading.isNotBlank()) words += WordInfo(leading, 0, 0)

        for (i in tags.indices) {
            val startMs = timeTagMs(tags[i]) ?: continue
            val segEnd = if (i + 1 < tags.size) tags[i + 1].range.first else body.length
            val segment = body.substring(tags[i].range.last + 1, segEnd)
            if (segment.isBlank()) {
                if (segment.isNotEmpty() && words.isNotEmpty()) {
                    words[words.lastIndex] = words.last().let { it.copy(text = it.text + segment) }
                }
                continue
            }
            val nextMs = tags.getOrNull(i + 1)?.let(::timeTagMs)
            val duration = if (nextMs != null) (nextMs - startMs).coerceAtLeast(0) else 0L
            words += WordInfo(segment, (startMs - lineStartMs).coerceAtLeast(0), duration)
        }
        return words
    }
}
