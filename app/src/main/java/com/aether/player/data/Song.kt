package com.aether.player.data

import android.net.Uri

data class Song(
    val id: Long, val title: String, val artist: String, val album: String,
    val uri: Uri, val path: String, val folder: String, val dateAdded: Long
)
