package com.aether.player.media

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.net.wifi.WifiManager
import android.provider.MediaStore
import android.widget.Toast
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.BitmapLoader
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.aether.player.MainActivity
import com.google.android.gms.cast.framework.CastContext
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/** Supplies 1024px artwork to the notification, lock screen and system media UIs (incl. ColorOS). */
class ArtBitmapLoader(private val ctx: Context) : BitmapLoader {
    private val exec = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(2))
    override fun supportsMimeType(mimeType: String): Boolean = true
    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        exec.submit(Callable<Bitmap> { BitmapFactory.decodeByteArray(data, 0, data.size) })
    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        exec.submit(Callable<Bitmap> {
            val id = ArtworkHelper.idFrom(uri) ?: throw IllegalArgumentException("Unsupported artwork uri")
            ArtworkHelper.load(ctx, id, 1024) ?: throw IllegalStateException("No artwork")
        })
}

class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private lateinit var exo: ExoPlayer
    private var cast: CastPlayer? = null
    private var casting = false
    private var altMimeTried = false
    private var wifiLock: WifiManager.WifiLock? = null
    private val server by lazy { FileServer(this) }

    private fun toast(msg: String) = Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show()

    override fun onCreate() {
        super.onCreate()
        exo = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        EqController.attach(this, exo.audioSessionId)
        exo.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                EqController.attach(this@PlaybackService, audioSessionId)
            }
        })
        server.start()
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaSession.Builder(this, exo).setBitmapLoader(ArtBitmapLoader(this)).setSessionActivity(openApp).setCallback(object : MediaSession.Callback {
            override fun onAddMediaItems(
                mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>
            ): ListenableFuture<MutableList<MediaItem>> {
                val ip = if (casting) FileServer.localIp(this@PlaybackService) else null
                return Futures.immediateFuture(mediaItems.map { resolve(it, ip) }.toMutableList())
            }
        }).build()
        try {
            val cp = CastPlayer(CastContext.getSharedInstance(this))
            cp.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    CastLog.add("Cast player error ${error.errorCodeName}: ${error.message}")
                    toast("Cast error: ${error.errorCodeName} (tap Log in the app for details)")
                    if (casting && !altMimeTried) {
                        altMimeTried = true
                        FileServer.altFlacMime = true
                        CastLog.add("Retrying once with audio/x-flac for FLAC files")
                        reloadCurrent()
                    }
                }
            })
            cp.setSessionAvailabilityListener(object : SessionAvailabilityListener {
                override fun onCastSessionAvailable() { CastLog.add("Cast session available"); switchTo(cp) }
                override fun onCastSessionUnavailable() { CastLog.add("Cast session ended - back to phone playback"); switchTo(exo) }
            })
            cast = cp
            if (cp.isCastSessionAvailable) switchTo(cp)
        } catch (e: Exception) {
            cast = null
            CastLog.add("Casting unavailable: ${e.message}")
            toast("Casting unavailable: ${e.message}")
        }
    }

    /** Local: content:// URI. Casting: http://<phone-wifi-ip>:8765/song/<id> with a Cast-friendly MIME type. */
    private fun resolve(item: MediaItem, ip: String?): MediaItem {
        val id = item.mediaId.toLongOrNull() ?: return item
        val local = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
        if (!casting) {
            val md = item.mediaMetadata.buildUpon().setArtworkUri(ArtworkHelper.uriFor(id)).build()
            return item.buildUpon().setUri(local).setMimeType(contentResolver.getType(local)).setMediaMetadata(md).build()
        }
        if (ip == null) return item.buildUpon().setUri(local).setMimeType(FileServer.mimeFor(this, local)).build()
        val md = item.mediaMetadata.buildUpon().setArtworkUri(Uri.parse("http://$ip:${FileServer.PORT}/art/$id")).build()
        return item.buildUpon().setUri(Uri.parse("http://$ip:${FileServer.PORT}/song/$id"))
            .setMimeType(FileServer.mimeFor(this, local)).setMediaMetadata(md).build()
    }

    /** Keeps Wi-Fi awake while casting with the screen off. */
    private fun setWifiLock(on: Boolean) {
        try {
            if (on) {
                if (wifiLock == null) {
                    wifiLock = applicationContext.getSystemService(WifiManager::class.java)
                        ?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "aether-cast")
                    wifiLock?.setReferenceCounted(false)
                }
                wifiLock?.acquire()
            } else if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
        } catch (e: Exception) {}
    }

    private fun switchTo(target: Player) {
        val s = session ?: return
        val old = s.player
        if (old === target) return
        try {
            casting = target !== exo
            if (casting) { altMimeTried = false; FileServer.altFlacMime = false }
            server.start()
            setWifiLock(casting)
            val ip = if (casting) FileServer.localIp(this) else null
            if (casting && ip == null) {
                CastLog.add("No Wi-Fi address found - cast device must be reachable over Wi-Fi")
                toast("Casting needs Wi-Fi: couldn't find this phone's Wi-Fi address")
            }
            val items = List(old.mediaItemCount) { resolve(old.getMediaItemAt(it), ip) }
            CastLog.add("Switching to ${if (casting) "cast" else "phone"} playback, phone ip=$ip, items=${items.size}")
            val idx = old.currentMediaItemIndex
            val pos = old.currentPosition
            val play = old.playWhenReady
            try { old.stop(); old.clearMediaItems() } catch (e: Exception) {}
            s.player = target
            if (items.isNotEmpty()) {
                target.setMediaItems(items, idx, pos)
                target.playWhenReady = play
                target.prepare()
            }
        } catch (e: Exception) {
            CastLog.add("Cast switch failed: ${e.message}")
            toast("Cast switch failed: ${e.message}")
        }
    }

    /** One-shot recovery: reload the queue (same position) after a cast error, with the alternate FLAC MIME type. */
    private fun reloadCurrent() {
        val s = session ?: return
        val p = s.player
        try {
            val ip = FileServer.localIp(this)
            val items = List(p.mediaItemCount) { resolve(p.getMediaItemAt(it), ip) }
            if (items.isEmpty()) return
            val idx = p.currentMediaItemIndex
            val pos = p.currentPosition
            p.setMediaItems(items, idx, pos)
            p.prepare()
            p.playWhenReady = true
        } catch (e: Exception) {
            CastLog.add("Retry failed: ${e.message}")
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onDestroy() {
        setWifiLock(false)
        cast?.release()
        server.stop()
        session?.run { player.release(); release() }
        exo.release()
        session = null
        super.onDestroy()
    }
}
