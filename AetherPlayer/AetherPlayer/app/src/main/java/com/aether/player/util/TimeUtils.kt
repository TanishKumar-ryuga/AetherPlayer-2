package com.aether.player.util

fun formatTime(ms: Long): String {
    val t = if (ms < 0) 0 else ms
    return "%d:%02d".format(t / 60000, t / 1000 % 60)
}
