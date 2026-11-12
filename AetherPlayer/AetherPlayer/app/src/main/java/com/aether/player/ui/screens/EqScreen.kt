package com.aether.player.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.aether.player.media.EqController

@Composable
fun EqDialog(onClose: () -> Unit) {
    var on by remember { mutableStateOf(EqController.enabled()) }
    var version by remember { mutableIntStateOf(0) }
    var presetMenu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("Done") } },
        title = { Text("Equalizer") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (!EqController.available()) {
                    Text("Start playing a song on this phone first.")
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Enabled", Modifier.weight(1f))
                        Switch(checked = on, onCheckedChange = { on = it; EqController.setEnabled(it) })
                    }
                    Text("Applies to on-phone playback only (not while casting).", style = MaterialTheme.typography.bodySmall)
                    Box {
                        OutlinedButton(onClick = { presetMenu = true }) { Text("Presets") }
                        DropdownMenu(expanded = presetMenu, onDismissRequest = { presetMenu = false }) {
                            EqController.presets().forEachIndexed { i, n ->
                                DropdownMenuItem(
                                    text = { Text(n) },
                                    onClick = { EqController.usePreset(i); version++; presetMenu = false }
                                )
                            }
                        }
                    }
                    for (i in 0 until EqController.bands()) {
                        var v by remember(version) { mutableFloatStateOf(EqController.level(i).toFloat()) }
                        val hz = EqController.freqHz(i)
                        Text(
                            (if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz") + "  (${(v / 100).toInt()} dB)",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Slider(
                            value = v,
                            onValueChange = { v = it; EqController.setLevel(i, it.toInt()) },
                            valueRange = EqController.minLevel().toFloat()..EqController.maxLevel().toFloat()
                        )
                    }
                    if (EqController.bassSupported()) {
                        var b by remember { mutableFloatStateOf(EqController.bassStrength().toFloat()) }
                        Text("Bass boost", style = MaterialTheme.typography.bodySmall)
                        Slider(value = b, onValueChange = { b = it; EqController.setBass(it.toInt()) }, valueRange = 0f..1000f)
                    }
                }
            }
        }
    )
}
