package com.aether.player.data

import android.content.Context

object PlayStatsStore {
    private fun p(c: Context) = c.getSharedPreferences("stats", Context.MODE_PRIVATE)
    fun record(c: Context, id: Long) {
        val sp = p(c)
        sp.edit().putInt("p_$id", sp.getInt("p_$id", 0) + 1).putLong("l_$id", System.currentTimeMillis()).apply()
    }
    fun counts(c: Context): Map<Long, Int> =
        p(c).all.filterKeys { it.startsWith("p_") }.map { it.key.substring(2).toLong() to (it.value as Int) }.toMap()
    fun last(c: Context): Map<Long, Long> =
        p(c).all.filterKeys { it.startsWith("l_") }.map { it.key.substring(2).toLong() to (it.value as Long) }.toMap()
}
