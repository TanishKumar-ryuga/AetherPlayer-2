package com.aether.player.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aether.player.ui.PlayerViewModel

@Composable
fun MiniPlayer(vm: PlayerViewModel) {
    val s = vm.nowSong
    val c = vm.controller
    Surface(tonalElevation = 3.dp, modifier = Modifier.clickable { vm.showNow = true }) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Art(s, Modifier.size(40.dp))
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(s?.title ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(s?.artist ?: "", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = { c?.seekToPreviousMediaItem() }) { Text("⏮") }
            FilledTonalButton(onClick = { if (c != null) { if (c.isPlaying) c.pause() else c.play() } }) {
                Text(if (vm.isPlaying) "Pause" else "Play")
            }
            TextButton(onClick = { c?.seekToNextMediaItem() }) { Text("⏭") }
        }
    }
}
