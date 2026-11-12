package com.aether.player.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aether.player.ui.Detail
import com.aether.player.ui.Group
import com.aether.player.ui.PlayerViewModel
import com.aether.player.ui.components.GroupList
import com.aether.player.ui.components.VmSongList

@Composable
fun LibraryScreen(vm: PlayerViewModel) {
    val songs = vm.songs
    Column {
        TabRow(selectedTabIndex = vm.libTab) {
            listOf("Songs", "Albums", "Artists").forEachIndexed { i, n ->
                Tab(selected = vm.libTab == i, onClick = { vm.libTab = i }, text = { Text(n) })
            }
        }
        when (vm.libTab) {
            0 -> VmSongList(vm, songs, Modifier.weight(1f))
            1 -> GroupList(
                songs.groupBy { it.album }.map { (k, v) -> Group(k, v.first().artist, v) }.sortedBy { it.title.lowercase() }
            ) { vm.detail = Detail(it.title, it.songs) }
            else -> GroupList(
                songs.groupBy { it.artist }.map { (k, v) -> Group(k, "", v) }.sortedBy { it.title.lowercase() }
            ) { vm.detail = Detail(it.title, it.songs) }
        }
    }
}

@Composable
fun FoldersScreen(vm: PlayerViewModel) {
    GroupList(
        vm.songs.groupBy { it.folder }.map { (k, v) -> Group(k.substringAfterLast('/'), k, v) }.sortedBy { it.title.lowercase() }
    ) { vm.detail = Detail(it.title, it.songs) }
}

@Composable
fun DetailScreen(vm: PlayerViewModel, d: Detail, onExport: (String) -> Unit) {
    Column {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.detail = null }) { Text("←") }
            Text(
                d.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium
            )
        }
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.playList(d.songs, 0, false) }) { Text("Play") }
            OutlinedButton(onClick = {
                if (d.songs.isNotEmpty()) vm.playList(d.songs, d.songs.indices.random(), true)
            }) { Text("Shuffle") }
            val pl = d.playlist
            if (pl != null) {
                OutlinedButton(onClick = { onExport(pl) }) { Text("Export") }
                OutlinedButton(onClick = { vm.deletePlaylist(pl); vm.detail = null }) { Text("Delete") }
            }
        }
        VmSongList(vm, d.songs, Modifier.weight(1f))
    }
}
