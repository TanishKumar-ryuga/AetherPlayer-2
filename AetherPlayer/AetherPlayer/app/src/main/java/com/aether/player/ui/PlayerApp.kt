package com.aether.player.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aether.player.ui.components.CastButton
import com.aether.player.ui.components.CastLogButton
import com.aether.player.ui.components.CastLogDialog
import com.aether.player.ui.components.CastStatusBar
import com.aether.player.ui.components.MiniPlayer
import com.aether.player.ui.screens.*

@Composable
fun PlayerApp(vm: PlayerViewModel = viewModel()) {
    val audioPerm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[audioPerm] == true) vm.loadSongs()
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importM3u(uri)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        if (uri != null) vm.exportM3u(uri)
    }
    LaunchedEffect(Unit) {
        permLauncher.launch(
            if (Build.VERSION.SDK_INT >= 33) arrayOf(audioPerm, Manifest.permission.POST_NOTIFICATIONS)
            else arrayOf(audioPerm)
        )
    }

    // After returning from the "All files access" settings screen, re-check lyrics.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshLyrics(true)
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    BackHandler(enabled = vm.showNow || vm.detail != null) {
        if (vm.showNow) vm.showNow = false else vm.detail = null
    }

    if (vm.showNow) {
        NowPlayingScreen(vm)
    } else {
        Scaffold(
            topBar = {
                Column {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Aether", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                        CastLogButton(vm)
                        CastButton()
                    }
                    CastStatusBar(vm)
                }
            },
            bottomBar = {
                Column {
                    if (vm.currentId != null) MiniPlayer(vm)
                    NavigationBar {
                        listOf("Home", "Library", "Folders", "Playlists").forEachIndexed { i, n ->
                            NavigationBarItem(
                                selected = vm.tab == i,
                                onClick = { vm.tab = i; vm.detail = null },
                                icon = { Text(listOf("🏠", "🎵", "📁", "📃")[i]) },
                                label = { Text(n) }
                            )
                        }
                    }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                val d = vm.detail
                if (d != null) {
                    DetailScreen(vm, d) { name ->
                        vm.pendingExport = name
                        exportLauncher.launch("$name.m3u")
                    }
                } else {
                    when (vm.tab) {
                        0 -> HomeScreen(vm)
                        1 -> LibraryScreen(vm)
                        2 -> FoldersScreen(vm)
                        else -> PlaylistsScreen(vm) { importLauncher.launch(arrayOf("*/*")) }
                    }
                }
            }
        }
    }
    PlaylistDialogs(vm)
    CastLogDialog(vm)
    if (vm.showEq) EqDialog { vm.showEq = false }
}
