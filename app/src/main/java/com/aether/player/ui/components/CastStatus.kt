package com.aether.player.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.aether.player.media.CastLog
import com.aether.player.ui.PlayerViewModel

/** One-line cast state: "Connecting…", "Casting to <device>", or what went wrong. */
@Composable
fun CastStatusBar(vm: PlayerViewModel) {
    val s = vm.castStatus
    if (s.isNotEmpty()) {
        Text(
            s, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun CastLogButton(vm: PlayerViewModel) {
    TextButton(onClick = { vm.showCastLog = true }) { Text("Log") }
}

@Composable
fun CastLogDialog(vm: PlayerViewModel) {
    if (!vm.showCastLog) return
    val clipboard = LocalClipboardManager.current
    val text = CastLog.dump()
    AlertDialog(
        onDismissRequest = { vm.showCastLog = false },
        title = { Text("Cast log") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(text, style = MaterialTheme.typography.bodySmall) } },
        confirmButton = { TextButton(onClick = { clipboard.setText(AnnotatedString(text)) }) { Text("Copy") } },
        dismissButton = {
            Column {
                TextButton(onClick = { CastLog.clear(); vm.showCastLog = false }) { Text("Clear") }
                TextButton(onClick = { vm.showCastLog = false }) { Text("Close") }
            }
        }
    )
}
