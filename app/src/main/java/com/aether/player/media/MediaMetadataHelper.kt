package com.aether.player.media

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.aether.player.data.Song

object MediaMetadataHelper {
    /** Light-weight item: artwork is a URI, loaded at 1024px by the service's ArtBitmapLoader. */
    fun buildItem(s: Song): MediaItem = MediaItem.Builder()
        .setMediaId(s.id.toString())
        .setUri(s.uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(s.title)
                .setDisplayTitle(s.title)
                .setArtist(s.artist)
                .setAlbumTitle(s.album)
                .setArtworkUri(ArtworkHelper.uriFor(s.id))
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setIsPlayable(true)
                .build()
        ).build()
}
