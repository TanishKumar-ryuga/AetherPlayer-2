package com.aether.player.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.aether.player.lyrics.LyricsRepository
import com.aether.player.ui.PlayerViewModel
import com.aether.player.ui.components.Art
import com.aether.player.ui.components.CastButton
import com.aether.player.ui.components.CastLogButton
import com.aether.player.ui.components.CastStatusBar
import com.aether.player.ui.components.LyricsView
import com.aether.player.util.formatTime
import kotlinx.coroutines.delay

@Composable
fun NowPlayingScreen(vm: PlayerViewModel) {
    val ctx = LocalContext.current
    val c = vm.controller
    val song = vm.nowSong
    var page by remember { mutableIntStateOf(0) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var seeking by remember { mutableStateOf(false) }

    LaunchedEffect(c) {
        while (true) {
            if (c != null && !seeking) {
                pos = c.currentPosition
                dur = c.duration.coerceAtLeast(0L)
            }
            delay(300)
        }
    }
    val queue: List<MediaItem> = remember(vm.tick, c) {
        if (c == null) emptyList() else List(c.mediaItemCount) { c.getMediaItemAt(it) }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.showNow = false }) { Text("⌄ Back") }
            Spacer(Modifier.weight(1f))
            CastLogButton(vm)
            CastButton()
        }
        CastStatusBar(vm)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Cover", "Lyrics", "Queue").forEachIndexed { i, n ->
                if (page == i) FilledTonalButton(onClick = { page = i }) { Text(n) }
                else OutlinedButton(onClick = { page = i }) { Text(n) }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            when (page) {
                0 -> Art(song, Modifier.fillMaxWidth(0.85f).aspectRatio(1f), px = 1024)
                1 -> LyricsView(
                    lyrics = vm.lyrics,
                    positionMs = pos,
                    hasFileAccess = LyricsRepository.hasFileAccess(),
                    onSeek = { t -> c?.seekTo(t); pos = t },
                    onGrant = {
                        if (Build.VERSION.SDK_INT >= 30) {
                            ctx.startActivity(
                                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                            )
                        }
                    },
                    onReload = { vm.refreshLyrics(true) },
                    modifier = Modifier.fillMaxSize()
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(queue) { i, item ->
                        Row(
                            Modifier.fillMaxWidth().clickable { c?.seekTo(i, 0L) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                item.mediaMetadata.title?.toString() ?: "",
                                Modifier.weight(1f).padding(vertical = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = if (item.mediaId == vm.currentId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            TextButton(onClick = { c?.removeMediaItem(i) }) { Text("✕") }
                        }
                    }
                }
            }
        }
        Text(song?.title ?: "", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(song?.artist ?: "", style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        Slider(
            value = pos.toFloat(),
            valueRange = 0f..maxOf(1f, dur.toFloat()),
            onValueChange = { seeking = true; pos = it.toLong() },
            onValueChangeFinished = { c?.seekTo(pos); seeking = false }
        )
        Row(Modifier.fillMaxWidth()) {
            Text(formatTime(pos))
            Spacer(Modifier.weight(1f))
            Text(formatTime(dur))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { if (c != null) c.shuffleModeEnabled = !vm.shuffle }) {
                Text(if (vm.shuffle) "🔀 On" else "🔀 Off")
            }
            TextButton(onClick = { c?.seekToPreviousMediaItem() }) { Text("⏮") }
            FilledTonalButton(onClick = { if (c != null) { if (c.isPlaying) c.pause() else c.play() } }) {
                Text(if (vm.isPlaying) "Pause" else "Play")
            }
            TextButton(onClick = { c?.seekToNextMediaItem() }) { Text("⏭") }
            TextButton(onClick = {
                if (c != null) {
                    c.repeatMode = when (vm.repeat) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                }
            }) {
                Text(
                    when (vm.repeat) {
                        Player.REPEAT_MODE_ALL -> "🔁 All"
                        Player.REPEAT_MODE_ONE -> "🔂 One"
                        else -> "🔁 Off"
                    }
                )
            }
        }
        Row {
            val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
            TextButton(onClick = { vm.changeSpeed(speeds[(speeds.indexOf(vm.speed) + 1) % speeds.size]) }) {
                Text("Speed ${vm.speed}x")
            }
            TextButton(onClick = {
                vm.changeSleep(
                    when (vm.sleepMin) {
                        0 -> 15
                        15 -> 30
                        30 -> 60
                        else -> 0
                    }
                )
            }) { Text(if (vm.sleepMin == 0) "Sleep: off" else "Sleep: ${vm.sleepMin}m") }
            TextButton(onClick = { vm.showEq = true }) { Text("EQ") }
        }
    }
}
