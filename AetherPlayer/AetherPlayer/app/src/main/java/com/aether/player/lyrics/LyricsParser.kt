package com.aether.player.lyrics

object LyricsParser {
    private val tag = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")
    private val offsetTag = Regex("\\[offset:\\s*([+-]?\\d+)]", RegexOption.IGNORE_CASE)
    private val metaTag = Regex("^\\[[a-zA-Z#]+:.*]$")
    private val wordTag = Regex("<\\d+:\\d+(?:[.:]\\d+)?>")

    private fun toMs(m: MatchResult): Long {
        val min = m.groupValues[1].toLong()
        val sec = m.groupValues[2].toLong()
        val frac = m.groupValues[3].padEnd(3, '0').take(3).toLong()
        return min * 60000 + sec * 1000 + frac
    }

    fun parse(raw: String, source: String): Lyrics? {
        val text = raw.replace("\r\n", "\n").replace('\r', '\n').removePrefix("\uFEFF")
        val offset = offsetTag.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val timed = mutableListOf<LyricLine>()
        val plain = mutableListOf<String>()
        for (line in text.lines()) {
            var rest = line.trim()
            val times = mutableListOf<Long>()
            while (true) {
                val m = tag.find(rest)
                if (m != null && m.range.first == 0) {
                    times.add(toMs(m))
                    rest = rest.substring(m.range.last + 1).trimStart()
                } else break
            }
            rest = rest.replace(wordTag, "").trim()
            if (times.isNotEmpty()) {
                for (t in times) timed.add(LyricLine(maxOf(0L, t + offset), rest))
            } else if (!metaTag.matches(line.trim())) {
                plain.add(line.trim())
            }
        }
        if (timed.isNotEmpty()) return Lyrics(true, timed.sortedBy { it.timeMs }, source)
        val cleaned = plain.dropWhile { it.isEmpty() }.dropLastWhile { it.isEmpty() }
        if (cleaned.none { it.isNotBlank() }) return null
        return Lyrics(false, cleaned.map { LyricLine(0L, it) }, source)
    }
}
