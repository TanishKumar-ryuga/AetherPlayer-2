package com.aether.player.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aether.player.data.PlayStatsStore
import com.aether.player.ui.PlayerViewModel
import com.aether.player.ui.components.HomeRow

@Composable
fun HomeScreen(vm: PlayerViewModel) {
    val ctx = LocalContext.current
    val songs = vm.songs
    val counts = remember(vm.statsTick, songs) { PlayStatsStore.counts(ctx) }
    val last = remember(vm.statsTick, songs) { PlayStatsStore.last(ctx) }
    val recentAdded = songs.sortedByDescending { it.dateAdded }.take(15)
    val recentPlayed = last.entries.sortedByDescending { it.value }.mapNotNull { vm.byId[it.key] }.take(15)
    val most = counts.entries.sortedByDescending { it.value }.mapNotNull { vm.byId[it.key] }.take(15)
    LazyColumn {
        item {
            Button(
                onClick = { if (songs.isNotEmpty()) vm.playList(songs, songs.indices.random(), true) },
                modifier = Modifier.padding(16.dp)
            ) { Text("Shuffle all (${songs.size} songs)") }
        }
        item { HomeRow("Recently added", recentAdded) { vm.playList(recentAdded, it, false) } }
        item { HomeRow("Recently played", recentPlayed) { vm.playList(recentPlayed, it, false) } }
        item { HomeRow("Most played", most) { vm.playList(most, it, false) } }
    }
}
