package com.aether.player.lyrics

data class LyricLine(val timeMs: Long, val text: String)
data class Lyrics(val synced: Boolean, val lines: List<LyricLine>, val source: String)
