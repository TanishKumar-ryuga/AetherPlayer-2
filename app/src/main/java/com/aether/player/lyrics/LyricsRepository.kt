package com.aether.player.lyrics

import android.content.Context
import android.os.Build
import android.os.Environment
import com.aether.player.data.Song
import java.io.File

object LyricsRepository {
    private val cache = HashMap<Long, Lyrics?>()

    /** Needed to read .lrc files that sit next to the songs (Android 11+). */
    fun hasFileAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager() else true

    @Synchronized
    fun clear(id: Long) { cache.remove(id) }

    @Synchronized
    fun load(ctx: Context, song: Song): Lyrics? {
        if (cache.containsKey(song.id)) return cache[song.id]
        val side = readSidecar(song)
        var emb: Lyrics? = null
        if (side == null || !side.synced) {
            val raw = EmbeddedLyrics.read(ctx, song.uri, song.path)
            if (raw != null) emb = LyricsParser.parse(raw, "Embedded")
        }
        val result: Lyrics? = when {
            side != null && side.synced -> side
            emb != null && emb.synced -> emb
            else -> side ?: emb
        }
        if (cache.size > 200) cache.clear()
        cache[song.id] = result
        return result
    }

    private fun readSidecar(song: Song): Lyrics? {
        val base = song.path.substringBeforeLast('.', song.path)
        for (ext in listOf("lrc", "LRC")) {
            try {
                val f = File("$base.$ext")
                if (f.exists() && f.canRead()) {
                    val parsed = LyricsParser.parse(f.readText(Charsets.UTF_8), ".lrc file")
                    if (parsed != null) return parsed
                }
            } catch (e: Exception) {}
        }
        return null
    }
}
