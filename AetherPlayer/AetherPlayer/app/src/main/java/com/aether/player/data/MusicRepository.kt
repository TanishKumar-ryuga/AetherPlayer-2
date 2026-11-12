package com.aether.player.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import java.io.File

object MusicRepository {
    fun loadSongs(context: Context): List<Song> {
        val songs = mutableListOf<Song>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.DATE_ADDED
        )
        context.contentResolver.query(
            collection, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val path = c.getString(4) ?: ""
                songs += Song(
                    id, c.getString(1) ?: "Unknown", c.getString(2) ?: "Unknown artist",
                    c.getString(3) ?: "Unknown album", ContentUris.withAppendedId(collection, id),
                    path, File(path).parent ?: "", c.getLong(5)
                )
            }
        }
        return songs
    }
}
