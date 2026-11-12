package com.aether.player.lyrics

import android.content.Context
import android.net.Uri
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/** Reads lyrics stored inside tags: FLAC (Vorbis comment), MP3 (ID3v2 USLT), M4A (lyr atom). */
object EmbeddedLyrics {
    fun read(ctx: Context, uri: Uri, path: String): String? {
        return try {
            ctx.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val ch = FileInputStream(pfd.fileDescriptor).channel
                when (path.substringAfterLast('.', "").lowercase()) {
                    "flac" -> flac(ch)
                    "mp3" -> id3(ch)
                    "m4a", "mp4", "m4b" -> mp4(ch)
                    else -> null
                }
            }
        } catch (e: Exception) { null }
    }

    private fun readAt(ch: FileChannel, pos: Long, n: Int): ByteArray? {
        if (n < 0 || n > 32_000_000) return null
        val bb = ByteBuffer.allocate(n)
        var p = pos
        while (bb.hasRemaining()) {
            val r = ch.read(bb, p)
            if (r < 0) return null
            p += r
        }
        return bb.array()
    }

    // ---- FLAC ----
    private fun flac(ch: FileChannel): String? {
        val magic = readAt(ch, 0, 4) ?: return null
        if (String(magic, Charsets.ISO_8859_1) != "fLaC") return null
        var pos = 4L
        while (true) {
            val h = readAt(ch, pos, 4) ?: return null
            val last = (h[0].toInt() and 0x80) != 0
            val type = h[0].toInt() and 0x7F
            val len = ((h[1].toInt() and 0xFF) shl 16) or ((h[2].toInt() and 0xFF) shl 8) or (h[3].toInt() and 0xFF)
            if (type == 4) {
                val body = readAt(ch, pos + 4, len) ?: return null
                return vorbis(body)
            }
            if (last) return null
            pos += 4 + len
        }
    }

    private fun vorbis(b: ByteArray): String? {
        val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        val vlen = bb.getInt()
        bb.position(4 + vlen)
        val n = bb.getInt()
        val found = HashMap<String, String>()
        for (k in 0 until n) {
            val l = bb.getInt()
            val s = ByteArray(l)
            bb.get(s)
            val str = String(s, Charsets.UTF_8)
            found[str.substringBefore('=').uppercase()] = str.substringAfter('=', "")
        }
        for (key in listOf("SYNCEDLYRICS", "LRC", "LYRICS", "UNSYNCEDLYRICS")) {
            val v = found[key]
            if (v != null) return v
        }
        return null
    }

    // ---- MP3 / ID3v2 ----
    private fun syncsafe(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0x7F) shl 21) or ((b[o + 1].toInt() and 0x7F) shl 14) or
            ((b[o + 2].toInt() and 0x7F) shl 7) or (b[o + 3].toInt() and 0x7F)

    private fun id3(ch: FileChannel): String? {
        val h = readAt(ch, 0, 10) ?: return null
        if (String(h, 0, 3, Charsets.ISO_8859_1) != "ID3") return null
        val ver = h[3].toInt()
        val tag = readAt(ch, 10, syncsafe(h, 6)) ?: return null
        var p = 0
        if ((h[5].toInt() and 0x40) != 0 && tag.size >= 4) {
            p = if (ver == 4) syncsafe(tag, 0) else ByteBuffer.wrap(tag, 0, 4).getInt() + 4
        }
        var best: String? = null
        while (p + 10 <= tag.size) {
            val id = String(tag, p, 4, Charsets.ISO_8859_1)
            if (id[0] == '\u0000') break
            val fsz = if (ver == 4) syncsafe(tag, p + 4) else ByteBuffer.wrap(tag, p + 4, 4).getInt()
            if (fsz <= 0 || p + 10 + fsz > tag.size) break
            if (id == "USLT") {
                val t = uslt(tag, p + 10, fsz)
                if (t != null && (best == null || t.contains('['))) best = t
            }
            p += 10 + fsz
        }
        return best
    }

    private fun uslt(b: ByteArray, off: Int, len: Int): String? {
        val enc = b[off].toInt()
        val end = off + len
        var i = off + 4
        val cs = when (enc) {
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            else -> Charsets.UTF_8
        }
        if (enc == 0 || enc == 3) {
            while (i < end && b[i] != 0.toByte()) i++
            i++
        } else {
            while (i + 1 < end && !(b[i] == 0.toByte() && b[i + 1] == 0.toByte())) i += 2
            i += 2
        }
        if (i >= end) return null
        return String(b, i, end - i, cs).trim('\u0000')
    }

    // ---- M4A ----
    private fun mp4(ch: FileChannel): String? {
        val size = ch.size()
        var pos = 0L
        while (pos + 8 <= size) {
            val h = readAt(ch, pos, 8) ?: return null
            val asz = ByteBuffer.wrap(h, 0, 4).getInt().toLong() and 0xFFFFFFFFL
            if (asz < 8) return null
            if (String(h, 4, 4, Charsets.ISO_8859_1) == "moov") return mp4Box(ch, pos + 8, pos + asz)
            pos += asz
        }
        return null
    }

    private fun mp4Box(ch: FileChannel, start: Long, end: Long): String? {
        var pos = start
        while (pos + 8 <= end) {
            val h = readAt(ch, pos, 8) ?: return null
            val asz = ByteBuffer.wrap(h, 0, 4).getInt().toLong() and 0xFFFFFFFFL
            if (asz < 8) return null
            val body = pos + 8
            val type = String(h, 4, 4, Charsets.ISO_8859_1)
            if (type == "udta" || type == "ilst") {
                val r = mp4Box(ch, body, pos + asz)
                if (r != null) return r
            } else if (type == "meta") {
                val r = mp4Box(ch, body + 4, pos + asz)
                if (r != null) return r
            } else if (type == "\u00A9lyr") {
                val d = readAt(ch, body, (asz - 8).toInt()) ?: return null
                if (d.size > 16) return String(d, 16, d.size - 16, Charsets.UTF_8)
            }
            pos += asz
        }
        return null
    }
}
