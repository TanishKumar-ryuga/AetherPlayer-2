package com.aether.player.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.aether.player.data.MusicRepository
import com.aether.player.data.PlayStatsStore
import com.aether.player.data.PlaylistStore
import com.aether.player.data.Song
import com.aether.player.lyrics.Lyrics
import com.aether.player.lyrics.LyricsRepository
import com.aether.player.media.CastLog
import com.aether.player.media.MediaMetadataHelper
import com.aether.player.media.PlaybackService
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Detail(val title: String, val songs: List<Song>, val playlist: String? = null)
data class Group(val title: String, val subtitle: String, val songs: List<Song>)

class PlayerViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context = app.applicationContext

    // library
    var songs by mutableStateOf<List<Song>>(emptyList())
    var byId by mutableStateOf<Map<Long, Song>>(emptyMap())
    var playlists by mutableStateOf<Map<String, List<Long>>>(PlaylistStore.load(ctx))
    var statsTick by mutableIntStateOf(0)

    // playback
    var controller by mutableStateOf<MediaController?>(null)
    var isPlaying by mutableStateOf(false)
    var currentId by mutableStateOf<String?>(null)
    var tick by mutableIntStateOf(0)
    var shuffle by mutableStateOf(false)
    var repeat by mutableIntStateOf(Player.REPEAT_MODE_OFF)
    var speed by mutableFloatStateOf(1f)
    var sleepMin by mutableIntStateOf(0)
    var lyrics by mutableStateOf<Lyrics?>(null)
    var castStatus by mutableStateOf("")
    var showCastLog by mutableStateOf(false)

    // navigation / dialogs
    var tab by mutableIntStateOf(0)
    var libTab by mutableIntStateOf(0)
    var detail by mutableStateOf<Detail?>(null)
    var showNow by mutableStateOf(false)
    var showEq by mutableStateOf(false)
    var addTo by mutableStateOf<Song?>(null)
    var newPlDialog by mutableStateOf(false)
    var pendingExport: String? = null

    private var future: ListenableFuture<MediaController>? = null
    private var lyricsJob: Job? = null
    private var sleepJob: Job? = null
    private var castListener: SessionManagerListener<CastSession>? = null

    val nowSong: Song?
        get() {
            val id = currentId?.toLongOrNull() ?: return null
            return byId[id]
        }

    fun loadSongs() {
        songs = MusicRepository.loadSongs(ctx)
        byId = songs.associateBy { it.id }
        refreshLyrics()
    }

    fun refreshLyrics(force: Boolean = false) {
        val s = nowSong
        if (s == null) { lyrics = null; return }
        if (force) LyricsRepository.clear(s.id)
        lyricsJob?.cancel()
        lyricsJob = viewModelScope.launch {
            lyrics = withContext(Dispatchers.IO) { LyricsRepository.load(ctx, s) }
        }
    }

    // ---- playback actions ----
    fun playList(l: List<Song>, index: Int, shuffleOn: Boolean) {
        val c = controller ?: return
        if (l.isEmpty()) return
        c.setMediaItems(l.map { MediaMetadataHelper.buildItem(it) }, index, 0L)
        c.shuffleModeEnabled = shuffleOn
        c.prepare()
        c.play()
    }

    fun playNext(s: Song) {
        val c = controller ?: return
        c.addMediaItem(c.currentMediaItemIndex + 1, MediaMetadataHelper.buildItem(s))
    }

    fun enqueue(s: Song) {
        controller?.addMediaItem(MediaMetadataHelper.buildItem(s))
    }

    fun changeSpeed(f: Float) {
        speed = f
        controller?.setPlaybackSpeed(f)
    }

    fun changeSleep(m: Int) {
        sleepMin = m
        sleepJob?.cancel()
        if (m > 0) {
            sleepJob = viewModelScope.launch {
                delay(m * 60_000L)
                controller?.pause()
                sleepMin = 0
            }
        }
    }

    // ---- playlists ----
    private fun savePl(m: Map<String, List<Long>>) {
        playlists = m
        PlaylistStore.save(ctx, m)
    }

    fun createPlaylist(name: String, first: Song?) {
        savePl(playlists + (name to listOfNotNull(first?.id)))
    }

    fun addToPlaylist(name: String, s: Song) {
        savePl(playlists + (name to (playlists[name].orEmpty() + s.id)))
    }

    fun deletePlaylist(name: String) {
        savePl(playlists - name)
    }

    fun importM3u(uri: Uri) {
        val displayName = ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
        }
        val name = displayName?.substringBeforeLast('.') ?: "Imported"
        val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
        val ids = text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.mapNotNull { line ->
            val file = line.substringAfterLast('/').substringAfterLast('\\')
            songs.firstOrNull { it.path.endsWith(file, ignoreCase = true) }?.id
        }
        savePl(playlists + (name to ids))
    }

    fun exportM3u(uri: Uri) {
        val name = pendingExport ?: return
        val ids = playlists[name].orEmpty()
        val text = "#EXTM3U\n" + ids.mapNotNull { byId[it]?.path }.joinToString("\n")
        ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
    }

    init {
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val f = MediaController.Builder(ctx, token).buildAsync()
        future = f
        f.addListener({
            val c = f.get()
            isPlaying = c.isPlaying
            currentId = c.currentMediaItem?.mediaId
            shuffle = c.shuffleModeEnabled
            repeat = c.repeatMode
            c.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlayingNow: Boolean) { isPlaying = isPlayingNow }
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    currentId = mediaItem?.mediaId
                    tick++
                    refreshLyrics()
                    if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                        val id = mediaItem?.mediaId?.toLongOrNull()
                        if (id != null) { PlayStatsStore.record(ctx, id); statsTick++ }
                    }
                }
                override fun onTimelineChanged(timeline: Timeline, reason: Int) { tick++ }
                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) { shuffle = shuffleModeEnabled }
                override fun onRepeatModeChanged(repeatMode: Int) { repeat = repeatMode }
            })
            controller = c
        }, ContextCompat.getMainExecutor(ctx))
        setupCastStatus()
    }

    /** Shows what the cast session is doing (connecting / casting to X / failed) and logs it. */
    private fun setupCastStatus() {
        try {
            val sm = CastContext.getSharedInstance(ctx).sessionManager
            val current = sm.currentCastSession
            if (current != null) castStatus = "Casting to " + (current.castDevice?.friendlyName ?: "cast device")
            val l = object : SessionManagerListener<CastSession> {
                override fun onSessionStarting(session: CastSession) {
                    castStatus = "Connecting to cast device…"
                    CastLog.add("Session starting")
                }
                override fun onSessionStarted(session: CastSession, sessionId: String) {
                    castStatus = "Casting to " + (session.castDevice?.friendlyName ?: "cast device")
                    CastLog.add("Session started: " + (session.castDevice?.friendlyName ?: "?"))
                }
                override fun onSessionStartFailed(session: CastSession, error: Int) {
                    castStatus = "Couldn't connect (code $error). Both devices must be on the same Wi-Fi."
                    CastLog.add("Session start failed, code $error")
                }
                override fun onSessionEnding(session: CastSession) { CastLog.add("Session ending") }
                override fun onSessionEnded(session: CastSession, error: Int) {
                    castStatus = if (error != 0) "Cast disconnected (code $error). Playing on this phone." else ""
                    CastLog.add("Session ended, code $error")
                }
                override fun onSessionResuming(session: CastSession, sessionId: String) {
                    castStatus = "Reconnecting to cast device…"
                    CastLog.add("Session resuming")
                }
                override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
                    castStatus = "Casting to " + (session.castDevice?.friendlyName ?: "cast device")
                    CastLog.add("Session resumed (wasSuspended=$wasSuspended)")
                }
                override fun onSessionResumeFailed(session: CastSession, error: Int) {
                    castStatus = "Couldn't reconnect to the cast device (code $error)."
                    CastLog.add("Session resume failed, code $error")
                }
                override fun onSessionSuspended(session: CastSession, reason: Int) {
                    castStatus = "Cast connection lost, trying to reconnect…"
                    CastLog.add("Session suspended, reason $reason")
                }
            }
            sm.addSessionManagerListener(l, CastSession::class.java)
            castListener = l
        } catch (e: Exception) {
            CastLog.add("Cast status unavailable: ${e.message}")
        }
    }

    override fun onCleared() {
        future?.let { MediaController.releaseFuture(it) }
        try {
            val l = castListener
            if (l != null) CastContext.getSharedInstance(ctx).sessionManager.removeSessionManagerListener(l, CastSession::class.java)
        } catch (e: Exception) {}
    }
}
