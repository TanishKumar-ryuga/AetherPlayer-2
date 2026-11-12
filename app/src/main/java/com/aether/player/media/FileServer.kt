package com.aether.player.media

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.provider.MediaStore
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.channels.Channels
import kotlin.concurrent.thread

/** Small HTTP server (Range, HEAD, CORS) so Chromecast / Nest can pull the original files, FLAC included. */
class FileServer(private val ctx: Context) {
    companion object {
        const val PORT = 8765

        /** Some older cast devices prefer audio/x-flac; the service flips this on for one retry if FLAC fails. */
        @Volatile var altFlacMime = false

        private const val CORS = "Access-Control-Allow-Origin: *\r\n" +
            "Access-Control-Allow-Headers: Range, Content-Type\r\n" +
            "Access-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n" +
            "Access-Control-Expose-Headers: Content-Length, Content-Range, Accept-Ranges\r\n"

        /** Wi-Fi (or hotspot) IPv4 address of this phone, never the mobile-data one. */
        fun localIp(ctx: Context): String? {
            try {
                val cm = ctx.getSystemService(ConnectivityManager::class.java)
                for (n in cm.allNetworks) {
                    val caps = cm.getNetworkCapabilities(n) ?: continue
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                        val ip = cm.getLinkProperties(n)?.linkAddresses?.map { it.address }
                            ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
                        if (ip != null) return ip
                    }
                }
            } catch (e: Exception) {}
            try {
                val found = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                    .filter { !it.isLoopback }
                    .flatMap { ni -> ni.inetAddresses.toList().filter { it is Inet4Address && it.isSiteLocalAddress }.map { ni.name to it.hostAddress } }
                return found.firstOrNull { it.first.startsWith("wlan") || it.first.startsWith("ap") || it.first.startsWith("swlan") }?.second
            } catch (e: Exception) {}
            return null
        }

        /** A MIME type the Cast receiver understands (x-flac, mp4a-latm, etc. are normalised). */
        fun mimeFor(ctx: Context, uri: Uri): String {
            val raw = ctx.contentResolver.getType(uri)?.lowercase() ?: return "audio/mpeg"
            return when {
                raw.contains("flac") -> if (altFlacMime) "audio/x-flac" else "audio/flac"
                raw.contains("wav") -> "audio/wav"
                raw.contains("m4a") || raw.contains("mp4") -> "audio/mp4"
                raw == "audio/aac" || raw == "audio/aacp" -> "audio/aac"
                raw.contains("ogg") || raw.contains("opus") -> "audio/ogg"
                raw.contains("mpeg") || raw.contains("mp3") -> "audio/mpeg"
                raw.startsWith("audio/") -> raw
                else -> "audio/mpeg"
            }
        }
    }

    @Volatile private var running = false
    @Volatile private var server: ServerSocket? = null

    /** Keeps retrying to bind the port, so a quick service restart can't leave casting without a server. */
    fun start() {
        if (running) return
        running = true
        thread(isDaemon = true) {
            var failLogged = false
            while (running) {
                try {
                    val s = ServerSocket()
                    s.reuseAddress = true
                    s.bind(InetSocketAddress(PORT))
                    server = s
                    failLogged = false
                    CastLog.add("File server listening on port $PORT")
                    while (running && !s.isClosed) {
                        val c = s.accept()
                        thread(isDaemon = true) { handle(c) }
                    }
                } catch (e: Exception) {
                    if (running && !failLogged) {
                        failLogged = true
                        CastLog.add("File server problem: ${e.message} (retrying)")
                    }
                }
                if (running) { try { Thread.sleep(2000) } catch (e: Exception) {} }
            }
        }
    }

    fun stop() {
        running = false
        try { server?.close() } catch (e: Exception) {}
        server = null
    }

    private fun handle(sock: Socket) {
        try {
            sock.soTimeout = 30000
            sock.use {
                val reader = sock.getInputStream().bufferedReader()
                val parts = (reader.readLine() ?: return).split(" ")
                val method = parts[0]
                val path = parts.getOrNull(1) ?: return
                var range: String? = null
                while (true) {
                    val l = reader.readLine() ?: break
                    if (l.isEmpty()) break
                    if (l.startsWith("Range:", true)) range = l.substring(6).trim()
                }
                CastLog.add("HTTP $method $path" + (if (range != null) "  [$range]" else ""))
                val out = BufferedOutputStream(sock.getOutputStream())
                if (method == "OPTIONS") { status(out, "204 No Content"); return }
                val id = path.substringBefore('?').substringAfterLast('/').toLongOrNull()
                if (id == null) { status(out, "404 Not Found"); return }
                if (path.startsWith("/art/")) serveArt(out, id, method == "HEAD")
                else serveSong(out, id, range, method == "HEAD")
            }
        } catch (e: Exception) {
            // Cast devices routinely close connections mid-stream (seeking, track change); that is normal.
            if (e !is SocketException) CastLog.add("Server error: ${e.javaClass.simpleName} ${e.message}")
        }
    }

    private fun status(out: BufferedOutputStream, line: String) {
        out.write("HTTP/1.1 $line\r\n${CORS}Content-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
        out.flush()
    }

    private fun serveArt(out: BufferedOutputStream, id: Long, head: Boolean) {
        val bmp = ArtworkHelper.load(ctx, id, 600)
        if (bmp == null) { status(out, "404 Not Found"); return }
        val bos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 90, bos)
        val bytes = bos.toByteArray()
        out.write(
            ("HTTP/1.1 200 OK\r\n${CORS}Content-Type: image/jpeg\r\nCache-Control: public, max-age=86400\r\n" +
                "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray()
        )
        if (!head) out.write(bytes)
        out.flush()
    }

    private fun serveSong(out: BufferedOutputStream, id: Long, range: String?, head: Boolean) {
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
        val pfd = ctx.contentResolver.openFileDescriptor(uri, "r")
        if (pfd == null) {
            CastLog.add("Song $id not readable")
            status(out, "404 Not Found")
            return
        }
        pfd.use { fd ->
            val len = fd.statSize
            var start = 0L
            var end = len - 1
            if (range != null) {
                val spec = range.removePrefix("bytes=").trim()
                val a = spec.substringBefore('-')
                val b = spec.substringAfter('-', "")
                if (a.isEmpty()) {
                    start = maxOf(0L, len - (b.toLongOrNull() ?: 0L))
                } else {
                    start = a.toLongOrNull() ?: 0L
                    if (b.isNotEmpty()) end = minOf(b.toLongOrNull() ?: end, len - 1)
                }
            }
            if (start >= len || start > end) { status(out, "416 Range Not Satisfiable"); return }
            val partial = range != null
            out.write(
                ((if (partial) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n") + CORS +
                    "Content-Type: ${mimeFor(ctx, uri)}\r\nAccept-Ranges: bytes\r\nContent-Length: ${end - start + 1}\r\n" +
                    (if (partial) "Content-Range: bytes $start-$end/$len\r\n" else "") +
                    "Connection: close\r\n\r\n").toByteArray()
            )
            if (!head) {
                val ch = FileInputStream(fd.fileDescriptor).channel
                ch.position(start)
                val ins = Channels.newInputStream(ch)
                val buf = ByteArray(65536)
                var remaining = end - start + 1
                while (remaining > 0) {
                    val n = ins.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                    if (n < 0) break
                    out.write(buf, 0, n)
                    remaining -= n
                }
            }
            out.flush()
        }
    }
}
