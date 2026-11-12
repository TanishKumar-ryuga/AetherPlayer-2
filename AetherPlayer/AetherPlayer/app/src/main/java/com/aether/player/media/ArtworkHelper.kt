package com.aether.player.media

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size

object ArtworkHelper {
    const val SCHEME = "aether-art"

    fun uriFor(id: Long): Uri = Uri.parse("$SCHEME://song/$id")

    /** Accepts aether-art://song/<id> and http://phone:8765/art/<id> (used while casting). */
    fun idFrom(uri: Uri): Long? =
        if (uri.scheme == SCHEME || uri.path?.startsWith("/art/") == true) uri.lastPathSegment?.toLongOrNull() else null

    private val cache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun load(ctx: Context, id: Long, px: Int): Bitmap? {
        val key = "$id@$px"
        val hit = cache.get(key)
        if (hit != null) return hit
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
        var bmp: Bitmap? = null
        if (Build.VERSION.SDK_INT >= 29) {
            bmp = try { ctx.contentResolver.loadThumbnail(uri, Size(px, px), null) } catch (e: Exception) { null }
        }
        if (bmp == null) bmp = embedded(ctx, uri, px)
        if (bmp != null) cache.put(key, bmp)
        return bmp
    }

    private fun embedded(ctx: Context, uri: Uri, px: Int): Bitmap? {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, uri)
            val b = r.embeddedPicture ?: return null
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(b, 0, b.size, o)
            var s = 1
            while (o.outWidth / (s * 2) >= px && o.outHeight / (s * 2) >= px) s *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = s }
            return BitmapFactory.decodeByteArray(b, 0, b.size, opts)
        } catch (e: Exception) {
            return null
        } finally {
            try { r.release() } catch (e: Exception) {}
        }
    }
}
