package com.aether.player.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory

@Composable
fun CastButton() {
    AndroidView(
        factory = { c ->
            val button = MediaRouteButton(c)
            CastButtonFactory.setUpMediaRouteButton(c, button)
            button.setAlwaysVisible(true)
            button
        },
        modifier = Modifier.size(48.dp)
    )
}
