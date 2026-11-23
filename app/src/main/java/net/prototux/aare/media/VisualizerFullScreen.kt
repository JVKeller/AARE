package net.prototux.aare.media

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The visualizer on the whole car screen, in stereo: left channel on the left half, right on the
 * right, low frequencies at the outside edges. Any tap leaves; the host sends Back and the knob's
 * press to [DefaultMediaHub.exitFullScreen] and keeps its turns from reaching the screen below.
 * Closes by itself when it's no longer allowed (switched off, or the car moves without passenger
 * mode), and on pause once the bars have fallen.
 */
@Composable
internal fun VisualizerFullScreen(hub: DefaultMediaHub, driving: Boolean, modifier: Modifier) {
    val settings = hub.settings
    @Suppress("UNUSED_EXPRESSION") hub.visualSettings // recompose when they change
    val visual = NowPlayingVisuals.byId(settings.nowPlayingVisual)
    val allowed = visual != null && settings.visualizerFullScreen && !driving
    LaunchedEffect(allowed) { if (!allowed) hub.exitFullScreen() }
    if (visual == null || !allowed) return
    val playback by hub.playback.collectAsState()
    Box(
        modifier.fillMaxSize().background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { hub.exitFullScreen() },
    ) {
    val art = playback?.art
    if (art != null && settings.visualizerArtBackground) {
        Image(art.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.22f)
    }
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 24.dp)) {
        val frames = rememberSpectrumFrames(
            bandsFor(maxWidth / 2), stereo = true, running = playback?.playing == true,
            delayMs = { settings.visualizerDelayMs }, verboseLog = settings.verboseProtocolLog, fallDbPerSec = fallDbPerSec(settings),
        )
        visual.Content(frames, stereo = true, style = visualStyle(settings), modifier = Modifier.fillMaxSize())
        // Paused: the bars fall, then back to now playing rather than a black screen.
        val paused = playback?.playing != true
        LaunchedEffect(paused) {
            if (!paused) return@LaunchedEffect
            snapshotFlow { frames.hasSound }.first { !it }
            hub.exitFullScreen()
        }
    }
    }
}
