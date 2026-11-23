package net.prototux.aare.media

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import net.prototux.aare.ui.car.CarFocusVisibility
import net.prototux.aare.ui.car.CarKnobCapture
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import net.prototux.aare.R
import net.prototux.aare.ui.car.carFocusHighlight
import net.prototux.aare.ui.theme.LocalCarDimens

/**
 * Now playing: big art, title/artist/album, progress (seekable when the app allows it), transport
 * buttons, the app's extra buttons and a queue button. Side by side in landscape, stacked in portrait.
 * With the visualizer on, its bars rise behind the title and progress, and a button on the art
 * opens it on the whole screen (parked, or in passenger mode: [driving] is false then).
 */
@Composable
internal fun NowPlayingView(hub: DefaultMediaHub, source: PlaybackSource, state: SourceState, driving: Boolean, floatingBar: Boolean = false, setView: (MediaView) -> Unit) {
    val info = state.info
    if (info == null) {
        // right after a tap the app needs a moment to resolve the item: wait before saying "nothing"
        var waited by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(4_000)
            waited = true
        }
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            state.error?.let { ErrorCard(it, Modifier.fillMaxWidth().padding(16.dp)) }
            Placeholder(if (waited) stringResource(R.string.media_nothing_playing) else stringResource(R.string.media_starting), loading = !waited && state.error == null,
                modifier = Modifier.weight(1f), icon = Icons.Rounded.MusicNote)
            if (waited) {
                Pill(stringResource(R.string.media_action_browse), selected = true, onClick = { setView(MediaView.BROWSE) }, icon = Icons.Rounded.LibraryMusic)
                Spacer(Modifier.height(32.dp))
            }
        }
        return
    }
    Box(Modifier.fillMaxSize()) {
        // a soft backdrop of the art, fading into the background
        if (info.art != null && (NowPlayingVisuals.byId(hub.settings.nowPlayingVisual) == null || hub.settings.visualizerArtBackground)) {
            Image(info.art.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.22f)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background))))
        }
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp)) {
            val portrait = maxHeight > maxWidth
            val settings = hub.settings
            @Suppress("UNUSED_EXPRESSION") hub.visualSettings // recompose when they change
            val visual = NowPlayingVisuals.byId(settings.nowPlayingVisual)
            // The bars behind the details, and the full-screen button once there's sound to show.
            var frames by remember { mutableStateOf<SpectrumFrames?>(null) }
            val strip: @Composable (Modifier) -> Unit = { mod ->
                if (visual != null) BoxWithConstraints(mod) {
                    val f = rememberSpectrumFrames(
                        bandsFor(maxWidth), stereo = false, running = info.playing && !hub.visualFullScreen,
                        delayMs = { settings.visualizerDelayMs }, verboseLog = settings.verboseProtocolLog, fallDbPerSec = fallDbPerSec(settings),
                    )
                    LaunchedEffect(f) { frames = f }
                    if (f.hasSound) visual.Content(f, stereo = false, style = visualStyle(settings), modifier = Modifier.fillMaxSize())
                }
            }
            val showFullScreen = visual != null && settings.visualizerFullScreen && !driving && frames?.hasSound == true
            val art: @Composable (Modifier) -> Unit = { mod ->
                Box(mod) {
                    ArtBox(info.art, Icons.Rounded.MusicNote, Modifier.fillMaxSize(), MaterialTheme.shapes.large)
                    if (showFullScreen) {
                        CircleButton(Icons.Rounded.Fullscreen, stringResource(R.string.visualizer_full_screen), onClick = { hub.visualFullScreen = true },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).background(Color.Black.copy(alpha = 0.35f), CircleShape))
                    }
                }
            }
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (portrait) {
                    art(Modifier.weight(1f, fill = false).sizeIn(maxWidth = 420.dp).aspectRatio(1f))
                    Spacer(Modifier.height(20.dp))
                    // matchParentSize: the bars take the details' height, without measuring them twice.
                    Box(Modifier.fillMaxWidth()) {
                        strip(Modifier.matchParentSize())
                        Column(Modifier.fillMaxWidth()) { Details(state, info, source) }
                    }
                } else {
                    Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        // With a visual, the art a quarter smaller and the text level with its top:
                        // even room above and below, with no top bar.
                        val height = if (visual != null) VISUAL_ART_HEIGHT else 1f
                        art(Modifier.fillMaxHeight(height).aspectRatio(1f))
                        Spacer(Modifier.width(28.dp))
                        if (visual != null) {
                            // The text at the top, the bars in the room below it.
                            Column(Modifier.weight(1f).fillMaxHeight(height)) {
                                // Room at the end for the floating library button.
                                Box(Modifier.padding(end = if (floatingBar) LocalCarDimens.current.touchTarget + 8.dp else 0.dp)) {
                                    Column { Details(state, info, source) }
                                }
                                Spacer(Modifier.height(12.dp))
                                strip(Modifier.weight(1f).fillMaxWidth())
                            }
                        } else {
                            Column(Modifier.weight(1f)) { Details(state, info, source) }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Controls(hub, source, state, setView)
            }
        }
    }
}

@Composable
private fun Details(state: SourceState, info: PlaybackInfo, source: PlaybackSource) {
    state.error?.let {
        ErrorCard(it, Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
    }
    Text(info.title, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    if (info.artist.isNotEmpty()) {
        Text(info.artist, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (info.album.isNotEmpty()) {
        Text(info.album, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.height(16.dp))
    Progress(info, state.canSeek, source::seekTo)
}

/**
 * The transport row across the full width: previous / play-pause / next in the middle, the app's
 * own buttons split on both sides, the queue at the end. Extra buttons are dropped when narrow.
 */
@Composable
private fun Controls(hub: DefaultMediaHub, source: PlaybackSource, state: SourceState, setView: (MediaView) -> Unit) {
    val touch = LocalCarDimens.current.touchTarget
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val hasQueue = state.queue.isNotEmpty()
        val slots = ((maxWidth - PLAY_SIZE - touch * 2) / touch).toInt() - (if (hasQueue) 1 else 0)
        val actions = state.actions.take(slots.coerceAtLeast(0))
        val left = actions.take((actions.size + 1) / 2)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
            left.forEach { ActionButton(hub, it) }
            CircleButton(Icons.Rounded.SkipPrevious, stringResource(R.string.media_action_previous), onClick = source::previous, enabled = state.canPrevious)
            CircleButton(
                if (state.showPause) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, stringResource(if (state.showPause) R.string.media_action_pause else R.string.media_action_play),
                onClick = source::playPause, filled = true, size = PLAY_SIZE,
            )
            CircleButton(Icons.Rounded.SkipNext, stringResource(R.string.media_action_next), onClick = source::next, enabled = state.canNext)
            actions.drop(left.size).forEach { ActionButton(hub, it) }
            if (hasQueue) CircleButton(Icons.AutoMirrored.Rounded.QueueMusic, stringResource(R.string.media_queue_title), onClick = { setView(MediaView.QUEUE) })
        }
    }
}

private val PLAY_SIZE = 96.dp

/** Position extrapolated from the last sample while playing; a slider when seeking is allowed. */
@Composable
private fun Progress(info: PlaybackInfo, canSeek: Boolean, seekTo: (Long) -> Unit) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(info.playing, info.sampledAtMs) {
        now = SystemClock.elapsedRealtime()
        while (info.playing) {
            delay(500)
            now = SystemClock.elapsedRealtime()
        }
    }
    val raw = if (info.playing) info.positionMs + (now - info.sampledAtMs) else info.positionMs
    val pos = if (info.durationMs > 0) raw.coerceIn(0, info.durationMs) else raw.coerceAtLeast(0)
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    // The knob: pressing the bar takes the knob for scrubbing (turns and left/right tilts step
    // through the song), pressing again gives it back. The app seeks once the turning pauses.
    var knobEngaged by remember { mutableStateOf(false) }
    var knobTarget by remember { mutableStateOf<Long?>(null) }
    var knobStreak by remember { mutableIntStateOf(0) }
    var knobDir by remember { mutableIntStateOf(0) }
    var knobLast by remember { mutableLongStateOf(0L) }
    val latestPos by rememberUpdatedState(pos)
    val latestSeek by rememberUpdatedState(seekTo)
    fun knobStep(dir: Int) {
        if (info.durationMs <= 0) return
        val t = SystemClock.uptimeMillis()
        if (t - knobLast > KNOB_STREAK_MS || dir != knobDir) knobStreak = 0
        knobLast = t
        knobDir = dir
        knobStreak++
        val step = when {
            knobStreak <= 3 -> 5_000L
            knobStreak <= 6 -> 15_000L
            else -> 30_000L
        }
        knobTarget = ((knobTarget ?: latestPos) + dir * step).coerceIn(0L, info.durationMs)
    }
    LaunchedEffect(knobTarget) {
        val t = knobTarget ?: return@LaunchedEffect
        delay(KNOB_SETTLE_MS)
        latestSeek(t)
        // Keep showing the target until the app's new position has had time to come in.
        delay(KNOB_SHOW_MS)
        knobTarget = null
    }
    if (info.durationMs > 0) {
        val fraction = pos.toFloat() / info.durationMs
        if (canSeek) {
            SeekBar(
                fraction = when {
                    dragging -> dragValue
                    knobTarget != null -> knobTarget!!.toFloat() / info.durationMs
                    else -> fraction
                },
                engaged = knobEngaged,
                onEngage = { knobEngaged = it; if (!it) knobStreak = 0 },
                onKnobStep = ::knobStep,
                onDrag = { dragging = true; dragValue = it },
                onDragEnd = {
                    seekTo((dragValue * info.durationMs).toLong())
                    dragging = false
                },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)))
            Spacer(Modifier.height(8.dp))
        }
    }
    Row(Modifier.fillMaxWidth()) {
        val shown = if (dragging) (dragValue * info.durationMs).toLong() else knobTarget ?: pos
        Text(formatTime(shown), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        if (info.durationMs > 0) {
            Text(formatTime(info.durationMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The seekable progress bar. The knob lands on it like a button (with the focus ring); a press
 * takes the knob for scrubbing (turns and left/right tilts step, thicker bar with a thumb), and a
 * press or Back gives it back. Up/down tilts never seek: they move on as from any other item.
 * Cars without a knob (a D-pad) step with left/right on the focused bar, as the old slider did.
 * Touch taps or drags it.
 */
@Composable
private fun SeekBar(
    fraction: Float,
    engaged: Boolean,
    onEngage: (Boolean) -> Unit,
    onKnobStep: (Int) -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val latestStep by rememberUpdatedState(onKnobStep)
    val latestEngage by rememberUpdatedState(onEngage)
    BackHandler(enabled = engaged) { latestEngage(false) }
    DisposableEffect(engaged) {
        if (!engaged) return@DisposableEffect onDispose {}
        val handler: (KeyEvent) -> Boolean? = { e ->
            val down = e.type == KeyEventType.KeyDown
            when (e.key) {
                Key.Tab -> { if (down) latestStep(if (e.isShiftPressed) -1 else 1); true }
                Key.DirectionLeft, Key.DirectionRight -> { if (down) latestStep(if (e.key == Key.DirectionRight) 1 else -1); true }
                Key.DirectionUp, Key.DirectionDown -> true
                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { if (e.type == KeyEventType.KeyUp) latestEngage(false); true }
                else -> null
            }
        }
        CarKnobCapture.handler = handler
        onDispose { if (CarKnobCapture.handler === handler) CarKnobCapture.handler = null }
    }
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
    val fill = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(20.dp)
    val f = fraction.coerceIn(0f, 1f)
    Canvas(
        modifier.height(40.dp).clip(shape).carFocusHighlight(shape)
            .onFocusChanged {
                focused = it.isFocused
                if (!it.isFocused && engaged) latestEngage(false)
            }
            .onKeyEvent { e ->
                when (e.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (e.type == KeyEventType.KeyUp) latestEngage(true)
                        true
                    }
                    // No knob: left/right on the focused bar step, like the slider did.
                    Key.DirectionLeft, Key.DirectionRight -> if (!CarFocusVisibility.rotary) {
                        if (e.type == KeyEventType.KeyDown) latestStep(if (e.key == Key.DirectionRight) 1 else -1)
                        true
                    } else false
                    else -> false
                }
            }
            .focusable()
            .pointerInput(Unit) { detectTapGestures { onDrag((it.x / size.width).coerceIn(0f, 1f)); onDragEnd() } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(onDragEnd = onDragEnd) { change, _ -> onDrag((change.position.x / size.width).coerceIn(0f, 1f)) }
            },
    ) {
        val y = size.height / 2
        val inset = 14.dp.toPx()
        val w = size.width - 2 * inset
        val stroke = (if (engaged) 10.dp else if (focused) 7.dp else 5.dp).toPx()
        drawLine(track, Offset(inset, y), Offset(inset + w, y), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(fill, Offset(inset, y), Offset(inset + w * f, y), strokeWidth = stroke, cap = StrokeCap.Round)
        drawCircle(fill, radius = (if (engaged) 12.dp else 8.dp).toPx(), center = Offset(inset + w * f, y))
    }
}

/** Knob clicks closer together than this keep a scrub's step growing. */
private const val KNOB_STREAK_MS = 700L
/** The app seeks once the knob has been still this long. */
private const val KNOB_SETTLE_MS = 450L
/** After that seek, how long the bar keeps showing the target while the app's position comes in. */
private const val KNOB_SHOW_MS = 1_500L

/** An app-defined button: its icon (tinted like ours) or, failing that, a short label. */
@Composable
private fun ActionButton(hub: DefaultMediaHub, action: CustomAction) {
    val d = LocalCarDimens.current
    val icon = rememberIcon(hub.art, action.icon, 96)
    Box(
        Modifier.size(d.touchTarget).clip(CircleShape).carFocusHighlight(CircleShape).clickable { action.run() },
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(icon.asImageBitmap(), action.label, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(d.iconSize))
        } else {
            Text(action.label.take(6), style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

/** The play queue; tapping an entry jumps to it. */
@Composable
internal fun QueueView(source: PlaybackSource, state: SourceState, done: () -> Unit) {
    if (state.queue.isEmpty()) {
        Placeholder(stringResource(R.string.media_queue_empty), loading = false, icon = Icons.Rounded.LibraryMusic)
        return
    }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = state.queue.indexOfFirst { it.current }.coerceAtLeast(0))
    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        // keyed on the position too: buggy apps repeat queue ids, and duplicate keys crash the list
        itemsIndexed(state.queue, key = { i, entry -> "$i:${entry.id}" }) { _, entry ->
            val bg = if (entry.current) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
            Row(
                Modifier.fillMaxWidth().heightIn(min = LocalCarDimens.current.touchTarget).clip(MaterialTheme.shapes.medium).background(bg)
                    .carFocusHighlight(MaterialTheme.shapes.medium)
                    .clickable { source.skipTo(entry); done() }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    if (entry.current) Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(entry.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (entry.subtitle.isNotEmpty()) {
                        Text(entry.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** The art's share of the height with a visual. */
private const val VISUAL_ART_HEIGHT = 0.75f
