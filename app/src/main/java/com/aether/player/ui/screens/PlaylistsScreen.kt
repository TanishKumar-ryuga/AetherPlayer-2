package com.aether.player.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aether.player.ui.Detail
import com.aether.player.ui.Group
import com.aether.player.ui.PlayerViewModel
import com.aether.player.ui.components.GroupList

@Composable
fun PlaylistsScreen(vm: PlayerViewModel, onImport: () -> Unit) {
    Column {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.newPlDialog = true }) { Text("New playlist") }
            OutlinedButton(onClick = onImport) { Text("Import .m3u") }
        }
        GroupList(vm.playlists.map { (k, v) -> Group(k, "", v.mapNotNull { vm.byId[it] }) }) {
            vm.detail = Detail(it.title, it.songs, it.title)
        }
    }
}

@Composable
fun PlaylistDialogs(vm: PlayerViewModel) {
    val s = vm.addTo
    if (s != null && !vm.newPlDialog) {
        AlertDialog(
            onDismissRequest = { vm.addTo = null },
            confirmButton = {},
            title = { Text("Add to playlist") },
            text = {
                Column {
                    vm.playlists.keys.forEach { n ->
                        TextButton(onClick = { vm.addToPlaylist(n, s); vm.addTo = null }) { Text(n) }
                    }
                    TextButton(onClick = { vm.newPlDialog = true }) { Text("+ New playlist") }
                }
            }
        )
    }
    if (vm.newPlDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { vm.newPlDialog = false; vm.addTo = null },
            title = { Text("New playlist") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") }) },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) vm.createPlaylist(name.trim(), vm.addTo)
                    vm.newPlDialog = false
                    vm.addTo = null
                }) { Text("Create") }
            }
        )
    }
}
