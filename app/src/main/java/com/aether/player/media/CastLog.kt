package com.aether.player.media

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Small in-memory log of cast events, viewable in the app (Log button) so failures can be diagnosed on a real phone. */
object CastLog {
    private val lines = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    @Synchronized
    fun add(msg: String) {
        Log.d("AetherCast", msg)
        lines.addLast(fmt.format(Date()) + "  " + msg)
        while (lines.size > 80) lines.removeFirst()
    }

    @Synchronized
    fun dump(): String = if (lines.isEmpty()) "No cast events yet." else lines.joinToString("\n")

    @Synchronized
    fun clear() { lines.clear() }
}
