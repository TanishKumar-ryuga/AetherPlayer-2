package com.aether.player.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aether.player.lyrics.Lyrics

@Composable
fun LyricsView(
    lyrics: Lyrics?, positionMs: Long, hasFileAccess: Boolean,
    onSeek: (Long) -> Unit, onGrant: () -> Unit, onReload: () -> Unit, modifier: Modifier = Modifier
) {
    if (lyrics == null) {
        Column(
            modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("No lyrics found")
            if (!hasFileAccess) {
                Text(
                    "To read .lrc files next to your songs, Aether needs \"All files access\".",
                    style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
                Button(onClick = onGrant) { Text("Allow access") }
            }
            TextButton(onClick = onReload) { Text("Reload") }
        }
    } else {
        val state = rememberLazyListState()
        val idx = if (lyrics.synced) lyrics.lines.indexOfLast { it.timeMs <= positionMs } else -1
        LaunchedEffect(idx) {
            if (idx >= 0) state.animateScrollToItem(maxOf(0, idx - 2))
        }
        LazyColumn(modifier.fillMaxWidth(), state = state, contentPadding = PaddingValues(vertical = 24.dp)) {
            itemsIndexed(lyrics.lines) { i, l ->
                val current = i == idx
                val color = when {
                    !lyrics.synced -> MaterialTheme.colorScheme.onSurface
                    current -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                }
                Text(
                    text = l.text.ifEmpty { "♪" },
                    modifier = Modifier.fillMaxWidth()
                        .clickable(enabled = lyrics.synced) { onSeek(l.timeMs) }
                        .padding(horizontal = 16.dp, vertical = if (lyrics.synced) 8.dp else 2.dp),
                    textAlign = TextAlign.Center,
                    style = if (current) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                    fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                    color = color
                )
            }
        }
    }
}
