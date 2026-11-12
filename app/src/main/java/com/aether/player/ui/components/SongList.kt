package com.aether.player.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aether.player.data.Song
import com.aether.player.ui.Group
import com.aether.player.ui.PlayerViewModel

@Composable
fun SongList(
    songs: List<Song>, current: String?, onPlay: (Int) -> Unit, onNext: (Song) -> Unit,
    onQueue: (Song) -> Unit, onAdd: (Song) -> Unit, modifier: Modifier = Modifier
) {
    LazyColumn(modifier) {
        itemsIndexed(songs) { i, s ->
            var menu by remember { mutableStateOf(false) }
            Row(
                Modifier.fillMaxWidth().clickable { onPlay(i) }.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Art(s, Modifier.size(44.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        s.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (current == s.id.toString()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    Text(s.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box {
                    TextButton(onClick = { menu = true }) { Text("⋮") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Play next") }, onClick = { onNext(s); menu = false })
                        DropdownMenuItem(text = { Text("Add to queue") }, onClick = { onQueue(s); menu = false })
                        DropdownMenuItem(text = { Text("Add to playlist") }, onClick = { onAdd(s); menu = false })
                    }
                }
            }
        }
    }
}

@Composable
fun VmSongList(vm: PlayerViewModel, songs: List<Song>, modifier: Modifier = Modifier) {
    SongList(
        songs = songs,
        current = vm.currentId,
        onPlay = { vm.playList(songs, it, false) },
        onNext = { vm.playNext(it) },
        onQueue = { vm.enqueue(it) },
        onAdd = { vm.addTo = it },
        modifier = modifier
    )
}

@Composable
fun GroupList(groups: List<Group>, onClick: (Group) -> Unit) {
    LazyColumn {
        items(groups) { g ->
            Column(Modifier.fillMaxWidth().clickable { onClick(g) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(g.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (g.subtitle.isEmpty()) "${g.songs.size} songs" else "${g.songs.size} songs · ${g.subtitle}",
                    style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun HomeRow(title: String, list: List<Song>, onPlay: (Int) -> Unit) {
    if (list.isEmpty()) return
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 6.dp))
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
        itemsIndexed(list) { i, s ->
            Column(Modifier.width(112.dp).clickable { onPlay(i) }.padding(4.dp)) {
                Art(s, Modifier.size(104.dp))
                Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
